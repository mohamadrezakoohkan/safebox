import Foundation
import SwiftUI
import Testing
@testable import SafeBox

/// N3 — the two pure decisions behind video: which frame becomes the poster,
/// and when playback must stop. Both are unit-tested rather than trusted to a
/// view's lifecycle, because getting either wrong is a containment failure
/// (a video still decoding behind the app-switcher cover) or an ugly poster.
struct PosterFrameTests {
    @Test func offsetIsOneSecondOrHalfTheDurationWhicheverIsSmaller() {
        // Long clips: the fixed 1 s offset.
        #expect(PosterFrame.offsetSeconds(durationSeconds: 600) == 1.0)
        #expect(PosterFrame.offsetSeconds(durationSeconds: 2.0) == 1.0)
        // Short clips: the midpoint, so a 0.4 s clip never asks for a frame
        // past its own end.
        #expect(PosterFrame.offsetSeconds(durationSeconds: 1.9) == 0.95)
        #expect(PosterFrame.offsetSeconds(durationSeconds: 0.4) == 0.2)
        #expect(PosterFrame.offsetSeconds(durationSeconds: 0.02) == 0.01)
    }

    @Test func offsetIsTheFirstFrameForDegenerateDurations() {
        #expect(PosterFrame.offsetSeconds(durationSeconds: 0) == 0)
        #expect(PosterFrame.offsetSeconds(durationSeconds: -5) == 0)
        #expect(PosterFrame.offsetSeconds(durationSeconds: .nan) == 0)
        #expect(PosterFrame.offsetSeconds(durationSeconds: .infinity) == 0)
    }

    @Test func theSharedConstantIsOneSecond() {
        #expect(PosterFrame.preferredOffsetSeconds == 1.0) // decisions §11
    }

    @Test func millisecondsRoundToTheNearestMillisecondAndNeverGoNegative() {
        #expect(VideoProbe.milliseconds(fromSeconds: 1.0) == 1000)
        #expect(VideoProbe.milliseconds(fromSeconds: 1.2345) == 1235)
        #expect(VideoProbe.milliseconds(fromSeconds: 0) == 0)
        #expect(VideoProbe.milliseconds(fromSeconds: -3) == 0)
        #expect(VideoProbe.milliseconds(fromSeconds: .nan) == 0)
    }

    @Test func mimeTypeComesFromTheRealExtension() {
        #expect(VideoProbe.mimeType(for: URL(fileURLWithPath: "/tmp/a.mov")) == "video/quicktime")
        #expect(VideoProbe.mimeType(for: URL(fileURLWithPath: "/tmp/a.mp4")) == "video/mp4")
        // Unknown/absent extension falls back rather than storing nothing.
        #expect(VideoProbe.mimeType(for: URL(fileURLWithPath: "/tmp/a")) == VideoProbe.fallbackMIMEType)
        // …and the stored value drives the Details sheet's Type row.
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "video/quicktime") == "MOV")
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "video/mp4") == "MP4")
    }
}

/// Records what the teardown policy asks a player to do, so the containment
/// rules are testable without AVFoundation, a window, or a real video.
@MainActor
final class FakeVaultPlayback: VaultVideoPlayback {
    private(set) var pauseCount = 0
    private(set) var releaseCount = 0

    func pausePlayback() { pauseCount += 1 }
    func releasePlayback() { releaseCount += 1 }
}

@MainActor
struct PlaybackTeardownPolicyTests {
    @Test func everyEventThatTakesTheVideoOffScreenReleasesIt() {
        for event: PlaybackEvent in [.pageChanged, .disappeared, .dismantled, .sceneLeftForeground] {
            #expect(PlaybackTeardownPolicy.action(for: event) == .release)
        }
    }

    @Test func comingBackToTheForegroundNeverResumesPlayback() {
        // Invariant: play is a user action. Nothing in the policy starts it.
        #expect(PlaybackTeardownPolicy.action(for: .sceneBecameActive) == .none)
    }

    @Test func backgroundingReleasesTheItemNotJustPauses() {
        // A paused player still owns a decoder and a surface; the app-switcher
        // snapshot cover is not a reason to keep either.
        let playback = FakeVaultPlayback()
        let action = PlaybackTeardownPolicy.apply(.sceneLeftForeground, to: playback)
        #expect(action == .release)
        #expect(playback.releaseCount == 1)
        #expect(playback.pauseCount == 0)
    }

    @Test func applyingEveryEventReleasesOnceEachAndNeverTouchesThePlayerOnResume() {
        let playback = FakeVaultPlayback()
        for event: PlaybackEvent in [.pageChanged, .disappeared, .dismantled, .sceneLeftForeground] {
            PlaybackTeardownPolicy.apply(event, to: playback)
        }
        #expect(playback.releaseCount == 4)

        PlaybackTeardownPolicy.apply(.sceneBecameActive, to: playback)
        #expect(playback.releaseCount == 4)
        #expect(playback.pauseCount == 0)
    }

    /// The page's own decision table: what the pager hands the representable.
    @Test func theOpenPageIsTheOnlyOneAllowedToHoldAnItem() {
        // Visible, foreground, current → the only combination that may play.
        #expect(VaultVideoPage.suspension(isVisible: true, isCovered: false, isCurrent: true) == nil)
        #expect(VaultVideoPage.suspension(isVisible: true, isCovered: false, isCurrent: false) == .pageChanged)
        #expect(VaultVideoPage.suspension(isVisible: true, isCovered: true, isCurrent: true) == .sceneLeftForeground)
        #expect(VaultVideoPage.suspension(isVisible: false, isCovered: false, isCurrent: true) == .disappeared)
        // Leaving the hierarchy wins over everything else.
        #expect(VaultVideoPage.suspension(isVisible: false, isCovered: true, isCurrent: false) == .disappeared)
    }

    /// The scene rule the page reuses is the same one that raises the
    /// app-switcher cover, so video can never play behind the cover.
    @Test func theSceneRuleMatchesTheSnapshotCoverRule() {
        #expect(SnapshotCoverPolicy.shouldCover(for: .active) == false)
        #expect(SnapshotCoverPolicy.shouldCover(for: .inactive) == true)
        #expect(SnapshotCoverPolicy.shouldCover(for: .background) == true)
    }

    /// The player reconciles against the CURRENT url on every update. Before
    /// this, the coordinator captured the url once at `makeCoordinator()` time,
    /// so a representable reused with a different url would have kept playing
    /// the previous video.
    @Test func theItemIsRebuiltOnlyWhenTheRequestedURLChanges() {
        let first = URL(fileURLWithPath: "/tmp/first.mov")
        let second = URL(fileURLWithPath: "/tmp/second.mov")

        // Nothing loaded → load.
        #expect(PlayerItemReconciliation.needsItem(loadedURL: nil, requestedURL: first))
        // Same video already loaded → no-op (never restart what is playing).
        #expect(!PlayerItemReconciliation.needsItem(loadedURL: first, requestedURL: first))
        // Different video → swap.
        #expect(PlayerItemReconciliation.needsItem(loadedURL: first, requestedURL: second))
        // …and after a release the next prepare loads again.
        #expect(PlayerItemReconciliation.needsItem(loadedURL: nil, requestedURL: second))
    }
}

@MainActor
struct VideoImportFailureNoticeTests {
    /// A failed video import is visible but not undoable: same toast host, no
    /// Undo button (decisions §9 / §10 `video_import_failed`).
    @Test func aNoticeShowsAMessageWithNothingToUndo() {
        let center = UndoCenter(displayDuration: .seconds(60))
        center.postNotice(message: VaultCopy.videoImportFailed)

        let entry = center.current
        #expect(entry != nil)
        #expect(entry?.message == VaultCopy.videoImportFailed)
        #expect(entry?.undo == nil)

        // Pressing Undo (which the toast does not even render) is a no-op.
        center.undo()
        #expect(center.current?.id == entry?.id)
    }

    @Test func aNoticeReplacesAPendingUndoLikeAnyOtherEntry() {
        let center = UndoCenter(displayDuration: .seconds(60))
        let counter = Counter()
        center.post(message: VaultCopy.deletedPhoto) { counter.calls += 1 }
        center.postNotice(message: VaultCopy.videoImportFailed)

        #expect(center.current?.message == VaultCopy.videoImportFailed)
        center.undo()
        #expect(counter.calls == 0) // the replaced undo is dropped, never run
    }

    @MainActor
    final class Counter {
        var calls = 0
    }
}
