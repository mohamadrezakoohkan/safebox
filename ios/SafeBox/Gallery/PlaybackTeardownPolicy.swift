import Foundation

/// Anything that owns a running video and can be stopped. The pager's
/// `AVPlayer` wrapper conforms in production; tests conform a recording fake,
/// so the containment rules below are exercised without AVFoundation.
@MainActor
protocol VaultVideoPlayback: AnyObject {
    /// Stop playing, keep the loaded item (and its position).
    func pausePlayback()
    /// Stop playing AND drop the item, so no decoder, no surface and no audio
    /// route survives.
    func releasePlayback()
}

/// Every reason vault video playback is reconsidered.
enum PlaybackEvent: Hashable, Sendable {
    /// The pager swiped to a different page.
    case pageChanged
    /// The player view left the hierarchy (back, dismiss, lock).
    case disappeared
    /// The representable was dismantled by SwiftUI.
    case dismantled
    /// The scene stopped being active — app switcher, Control Center, a call
    /// banner. The vault locks on this too, but the player must not wait for
    /// the lock to reach it.
    case sceneLeftForeground
    /// The scene became active again.
    case sceneBecameActive
}

/// What the player must do about an event.
enum PlaybackTeardown: Hashable, Sendable {
    /// Leave the player alone. Only ever the answer for "the app came back".
    case none
    case pause
    /// Pause AND drop the item.
    case release
}

/// The containment rule for vault video (decisions §9), as a pure function so
/// it can be pinned by a unit test rather than trusted to a view's lifecycle.
///
/// Two invariants the tests enforce:
/// 1. **No event ever starts or resumes playback.** Play is a user action, and
///    only a user action.
/// 2. **Everything that takes the video off screen releases it** — nothing is
///    left decoding behind the snapshot cover, behind another page, or after a
///    lock. Picture-in-Picture is disabled at the controller level, so a
///    released player cannot escape the app either.
enum PlaybackTeardownPolicy {
    static func action(for event: PlaybackEvent) -> PlaybackTeardown {
        switch event {
        case .pageChanged, .disappeared, .dismantled, .sceneLeftForeground:
            return .release
        case .sceneBecameActive:
            return .none
        }
    }

    /// Applies the decided action. Returns it, so a caller (or a test) can see
    /// what happened without reaching into the player.
    @MainActor
    @discardableResult
    static func apply(_ event: PlaybackEvent, to playback: any VaultVideoPlayback) -> PlaybackTeardown {
        let action = self.action(for: event)
        switch action {
        case .none:
            break
        case .pause:
            playback.pausePlayback()
        case .release:
            playback.releasePlayback()
        }
        return action
    }
}
