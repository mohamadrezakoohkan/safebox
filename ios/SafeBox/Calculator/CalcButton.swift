import SwiftUI
import UIKit

enum CalcKeyKind {
    case digit   // digits + "."
    case fn      // AC/C, ±, %
    case op      // ÷ × − + and =
}

/// One haptic, every key, every mode, every outcome (design spec §5.2).
@MainActor
enum KeyHaptics {
    private static let generator: UIImpactFeedbackGenerator = {
        let g = UIImpactFeedbackGenerator(style: .light)
        g.prepare()
        return g
    }()

    static func fire() {
        generator.impactOccurred(intensity: 0.7)
        generator.prepare()
    }
}

/// A real accessible button: VoiceOver's synthesized activate fires the same
/// action exactly once. Pressed visual + haptic land on touch-down; the action
/// itself fires on touch-up (Button's native path) — a deliberate, recorded
/// divergence from the spec's provisional touch-down rule (§5.1/R3): the
/// touch-down guard proved racy (double-fired =), and a single deterministic
/// firing is non-negotiable for passcode capture.
struct CalcButton: View {
    let label: String
    let accessibilityLabel: String
    let kind: CalcKeyKind
    let width: CGFloat
    let height: CGFloat
    let theme: DisguiseTheme
    var showRing = false
    /// Glyph center X override (double-width zero centers on the first column).
    var glyphCenterX: CGFloat?
    let action: () -> Void

    var body: some View {
        Button {
            action()
        } label: {
            labelView
        }
        .buttonStyle(CalcButtonStyle(
            kind: kind,
            theme: theme,
            cornerRadius: height * 0.24,
            showRing: showRing,
            onPressChanged: { pressed in
                // One identical haptic for every key, fired on touch-down.
                if pressed { KeyHaptics.fire() }
            }
        ))
        .frame(width: width, height: height)
        .accessibilityLabel(accessibilityLabel)
    }

    private var labelView: some View {
        let fontSize: CGFloat
        switch kind {
        case .digit: fontSize = 32
        case .fn: fontSize = 26
        case .op: fontSize = 34
        }
        let color = kind == .op ? theme.keyLabelOnOp : theme.keyLabel
        return Text(label)
            .font(.system(size: fontSize, weight: .medium))
            .foregroundStyle(color)
            .position(x: glyphCenterX ?? width / 2, y: height / 2)
            .frame(width: width, height: height)
            .contentShape(RoundedRectangle(cornerRadius: height * 0.24, style: .continuous))
    }
}

private struct CalcButtonStyle: ButtonStyle {
    let kind: CalcKeyKind
    let theme: DisguiseTheme
    let cornerRadius: CGFloat
    let showRing: Bool
    let onPressChanged: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        let restFill: Color
        let pressedFill: Color
        switch kind {
        case .digit:
            restFill = theme.keyDigit
            pressedFill = theme.keyDigitPressed
        case .fn:
            restFill = theme.keyFn
            pressedFill = theme.keyFnPressed
        case .op:
            restFill = theme.keyOp
            pressedFill = theme.keyOpPressed
        }
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return configuration.label
            .background(shape.fill(configuration.isPressed ? pressedFill : restFill))
            .overlay {
                if showRing {
                    shape.inset(by: 2).stroke(theme.keyOpActiveRing, lineWidth: 2)
                }
            }
            // Instant fill on press-down; 180ms ease-out release. No scale.
            .animation(configuration.isPressed ? nil : .easeOut(duration: 0.18), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in
                onPressChanged(pressed)
            }
    }
}
