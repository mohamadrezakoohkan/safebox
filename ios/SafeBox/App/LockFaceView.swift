import SwiftUI

/// The lock screen / first-run setup surface, driven by `AppLockCoordinator`.
/// Whichever face is active renders here; the host owns everything else.
struct LockFaceView: View {
    let coordinator: AppLockCoordinator

    var body: some View {
        DisguiseSurfaceHost(
            disguise: coordinator.surfaceDisguise,
            mode: coordinator.surfaceMode,
            caption: coordinator.caption,
            failedAttemptCount: coordinator.failedAttemptCount,
            onCommit: { tokens, overflowed in
                // Verification runs off the UI path; the face already rendered.
                Task { await coordinator.commit(tokens: tokens, overflowed: overflowed) }
            }
        )
        .statusBarHidden(false)
    }
}

/// Full-screen resting face installed the moment the scene resigns active and
/// removed on active — independent of the lock decision, so the app-switcher
/// snapshot never shows vault content. Rendered from the active face's own
/// cover so it is pixel-consistent with the live locked screen.
struct DisguiseCoverView: View {
    let disguise: any DisguiseProviding

    var body: some View {
        disguise.makeCoverFace()
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
