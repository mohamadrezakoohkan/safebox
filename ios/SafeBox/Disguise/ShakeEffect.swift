import SwiftUI

/// ±8pt horizontal shake, 3 cycles, 300 ms, ease-in-out (design §5.6, reused
/// verbatim by both overt faces per decisions §2.2.4). Set `travel` to 0 under
/// reduce-motion: the hold stays, the translation goes.
struct ShakeEffect: GeometryEffect {
    var travel: CGFloat = 8
    var cycles: CGFloat = 3
    var animatableData: CGFloat

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(
            translationX: travel * sin(animatableData * .pi * 2 * cycles), y: 0
        ))
    }
}

/// Shared overt-face constants (decisions §8).
enum OvertFeedback {
    /// The entry clears when the shake ends — same delay under reduce-motion,
    /// so the user still sees what failed.
    static let failHoldMs = 300
    static let shakeDurationMs = 300
}
