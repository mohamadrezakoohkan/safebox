import AVFoundation
import AVKit
import SwiftUI
import UIKit

/// One page of the pager showing a video.
///
/// Wraps `AVPlayerViewController` through `UIViewControllerRepresentable`
/// instead of SwiftUI's `VideoPlayer` for one reason: `VideoPlayer` gives no
/// way to switch Picture-in-Picture off, and PiP would float vault video in a
/// window that outlives the app — the exact containment failure decisions §9
/// forbids. Every teardown here goes through `PlaybackTeardownPolicy`.
struct VaultVideoPlayerView: UIViewControllerRepresentable {
    let url: URL
    /// `nil` while this page may hold a loaded item; otherwise the reason it
    /// may not, fed straight to `PlaybackTeardownPolicy`.
    let suspension: PlaybackEvent?

    func makeCoordinator() -> VaultVideoPlaybackBox {
        VaultVideoPlaybackBox()
    }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        // Containment: no PiP, ever. Both flags — the second one is what makes
        // a swipe-to-home start PiP automatically on iOS 14.2+.
        controller.allowsPictureInPicturePlayback = false
        controller.canStartPictureInPictureAutomaticallyFromInline = false
        // Nothing about vault media reaches the Now Playing surfaces (lock
        // screen, Control Center, CarPlay).
        controller.updatesNowPlayingInfoCenter = false
        controller.showsPlaybackControls = true
        controller.videoGravity = .resizeAspect
        controller.view.backgroundColor = .black
        controller.player = context.coordinator.player
        if suspension == nil { context.coordinator.prepare(url: url) }
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if let suspension {
            PlaybackTeardownPolicy.apply(suspension, to: context.coordinator)
        } else {
            // The current `url`, not the one this representable was first made
            // with: `prepare(url:)` is a no-op while it matches and swaps the
            // item when it does not, so a reused page can never play the
            // previous video.
            context.coordinator.prepare(url: url)
        }
    }

    static func dismantleUIViewController(_ controller: AVPlayerViewController,
                                          coordinator: VaultVideoPlaybackBox) {
        PlaybackTeardownPolicy.apply(.dismantled, to: coordinator)
        controller.player = nil
    }
}

/// Owns the `AVPlayer` for one page and implements the teardown contract.
/// Kept out of the view struct so the player's lifetime is exactly the
/// representable's, and so the teardown path is a plain object a test could
/// stand in for.
@MainActor
final class VaultVideoPlaybackBox: VaultVideoPlayback {
    let player = AVPlayer()
    /// The URL of the loaded item, `nil` when there is none.
    private(set) var loadedURL: URL?
    var hasItem: Bool { loadedURL != nil }

    init() {
        // Stop at the end rather than freeing the item — the user may scrub
        // back — and never advance to anything else.
        player.actionAtItemEnd = .pause
        player.preventsDisplaySleepDuringVideoPlayback = true
        // Same containment argument as PiP: AirPlay / an external display
        // would render vault video on hardware the user is not holding.
        player.allowsExternalPlayback = false
    }

    /// Reconciles the loaded item with `url`: a no-op while the right video is
    /// already loaded, otherwise it swaps the item in. Never starts playback:
    /// play is a user action (`PlaybackTeardownPolicy` invariant 1).
    func prepare(url: URL) {
        guard PlayerItemReconciliation.needsItem(loadedURL: loadedURL, requestedURL: url) else { return }
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        loadedURL = url
    }

    func pausePlayback() {
        player.pause()
    }

    func releasePlayback() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        loadedURL = nil
    }
}

/// Whether the player has to build a new `AVPlayerItem`, as a pure decision so
/// the "a changed url must swap the item" rule is pinned by a test instead of
/// living inside a `UIViewControllerRepresentable`.
enum PlayerItemReconciliation {
    static func needsItem(loadedURL: URL?, requestedURL: URL) -> Bool {
        loadedURL != requestedURL
    }
}

/// The pager's video page: hands the representable the two facts that decide
/// whether the item may exist at all — is this the visible page, and is the
/// scene in the foreground (the same `SnapshotCoverPolicy` rule that raises
/// the app-switcher cover, so video never plays under it).
struct VaultVideoPage: View {
    let url: URL
    let isCurrent: Bool

    @Environment(\.scenePhase) private var scenePhase
    @State private var isVisible = true

    /// The page's decision, as a pure function so it is unit-tested rather
    /// than inferred from a view's lifecycle. Ordered by severity: leaving the
    /// hierarchy first (the lock path), then the scene leaving the foreground,
    /// then simply not being the open page.
    static func suspension(isVisible: Bool, isCovered: Bool, isCurrent: Bool) -> PlaybackEvent? {
        if !isVisible { return .disappeared }
        if isCovered { return .sceneLeftForeground }
        if !isCurrent { return .pageChanged }
        return nil
    }

    var body: some View {
        VaultVideoPlayerView(
            url: url,
            suspension: Self.suspension(isVisible: isVisible,
                                        isCovered: SnapshotCoverPolicy.shouldCover(for: scenePhase),
                                        isCurrent: isCurrent)
        )
            .background(Color.black)
            .onAppear { isVisible = true }
            // Explicit, rather than relying on `dismantleUIViewController`
            // alone: the item is dropped the moment the page goes away.
            .onDisappear { isVisible = false }
    }
}
