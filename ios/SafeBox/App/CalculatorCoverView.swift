import SwiftUI

/// Full-screen calculator-face cover installed the moment the scene resigns
/// active and removed on active — independent of the lock decision, so the
/// app-switcher snapshot never shows vault content (ios-plan §2.3, Risk 3).
/// Rendered from the same surface/tokens so it is pixel-consistent with the
/// live locked screen.
struct CalculatorCoverView: View {
    var body: some View {
        CalculatorSurface(
            display: "0",
            banner: nil,
            clearLabel: "AC",
            ringOperator: nil,
            onKey: { _ in }
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
