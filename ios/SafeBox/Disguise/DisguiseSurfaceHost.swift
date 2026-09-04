import SwiftUI

/// Holds the recorder outside SwiftUI's value-diffing so recording a token does
/// not invalidate the surface mid-drag (the pattern face emits tokens inside a
/// live gesture). `@State` still owns the instance, so it is created with the
/// surface and dies with it — the §1.5 re-instantiation guarantee is unchanged.
@MainActor
private final class TokenRecorderBox {
    var recorder = TokenRecorder()
}

/// The one object per surface instance that owns the `TokenRecorder`,
/// translates the face's event stream, and calls the host's
/// `commit(tokens:overflowed:)` (decisions §1.7).
///
/// The §1.1 **overt buffer clear** lives here: when the host bumps the pulse
/// for an overt face, the face visibly resets its entry, so the buffer it no
/// longer depicts is cleared in the same step. The calculator is covert, so its
/// `verifyCurrent` buffer is deliberately left alone — that is iteration 1's
/// behavior and it stays.
struct DisguiseSurfaceHost: View {
    let disguise: any DisguiseProviding
    let mode: DisguiseMode
    let caption: LockBanner?
    let failedAttemptCount: Int
    /// Raw stream, for the host's caption-revert rule. Never inspect tokens.
    var onEvent: (DisguiseEvent) -> Void = { _ in }
    let onCommit: (_ tokens: [String], _ overflowed: Bool) -> Void

    @State private var box = TokenRecorderBox()

    var body: some View {
        disguise.makeSurface(
            mode: mode,
            caption: caption,
            failedAttemptCount: failedAttemptCount
        ) { event in
            onEvent(event)
            if let commit = box.recorder.apply(event) {
                onCommit(commit.tokens, commit.overflowed)
            }
        }
        .onChange(of: failedAttemptCount) { old, new in
            guard new > old, !disguise.isCovert else { return }
            box.recorder.clear()
        }
    }
}
