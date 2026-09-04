import SwiftUI

/// The PIN pad face: caption slot (or static title) → dot row → 3×4 circular
/// keypad. Overt — a wrong PIN shakes and clears, like a phone lock screen.
///
/// Nothing here is ever logged: not a key, not the entry length, not the pulse.
struct NumpadSurface: View {
    let mode: DisguiseMode
    let caption: LockBanner?
    let failedAttemptCount: Int
    let events: (DisguiseEvent) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var entry = NumpadEntry()
    @State private var shakeToken = 0

    var body: some View {
        GeometryReader { geo in
            let theme = DisguiseTheme.theme(for: colorScheme)
            let margin = NumpadMetrics.sideMargin(width: geo.size.width)
            let column = NumpadMetrics.columnWidth(width: geo.size.width)
            let diameter = NumpadMetrics.keyDiameter(columnWidth: column)
            VStack(spacing: 0) {
                captionSlot(theme: theme, margin: margin)
                Spacer(minLength: 0)
                VStack(spacing: NumpadMetrics.dotsToKeypadGap) {
                    dotRow(theme: theme, columnWidth: column)
                    keypad(theme: theme, diameter: diameter)
                }
                .frame(width: column)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background { DisguiseTheme.theme(for: colorScheme).background.ignoresSafeArea() }
        .onChange(of: failedAttemptCount) { old, new in
            guard new > old else { return }
            shakeToken += 1
            // The entry clears when the shake ends — same hold under
            // reduce-motion, so the user still sees what failed.
            Task {
                try? await Task.sleep(for: .milliseconds(OvertFeedback.failHoldMs))
                entry.clear()
            }
        }
    }

    // MARK: - Caption slot

    @ViewBuilder
    private func captionSlot(theme: DisguiseTheme, margin: CGFloat) -> some View {
        if mode == .disguise {
            // Face-owned decoy text, `disguise` mode only. Never a host caption.
            CaptionStrip(primary: NumpadCopy.faceTitle, theme: theme, horizontalPadding: margin)
        } else if let caption {
            CaptionStrip(primary: NumpadCopy.text(for: caption.primary),
                         primaryIsError: caption.primary.isError,
                         secondary: caption.secondary.map(NumpadCopy.text(for:)),
                         theme: theme,
                         horizontalPadding: margin)
                .animation(.easeInOut(duration: 0.15), value: caption)
        }
    }

    // MARK: - Dot row

    private func dotRow(theme: DisguiseTheme, columnWidth: CGFloat) -> some View {
        let dots = entry.visibleDots
        let metrics = NumpadEntry.dotMetrics(columnWidth: columnWidth, dots: dots)
        return HStack(spacing: metrics.gap) {
            ForEach(0..<dots, id: \.self) { _ in
                Circle()
                    .fill(theme.displayText)
                    .frame(width: metrics.size, height: metrics.size)
            }
        }
        .frame(height: NumpadEntry.baseDotSize)
        .frame(maxWidth: .infinity)
        .modifier(ShakeEffect(travel: reduceMotion ? 0 : 8, animatableData: CGFloat(shakeToken)))
        .animation(.easeInOut(duration: Double(OvertFeedback.shakeDurationMs) / 1000), value: shakeToken)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("entered digits")
        .accessibilityValue("\(entry.count)")
        .accessibilityIdentifier("numpad_dots")
    }

    // MARK: - Keypad

    private func keypad(theme: DisguiseTheme, diameter: CGFloat) -> some View {
        VStack(spacing: NumpadMetrics.keyGap) {
            ForEach(NumpadKeypad.rows, id: \.self) { row in
                HStack(spacing: NumpadMetrics.keyGap) {
                    ForEach(row, id: \.self) { key in
                        keyView(key, theme: theme, diameter: diameter)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func keyView(_ key: NumpadKeypad.Key, theme: DisguiseTheme, diameter: CGFloat) -> some View {
        switch key {
        case .digit(let value):
            NumpadKeyButton(
                glyph: .text(String(value)),
                glyphSize: 32,
                a11yLabel: NumpadKeypad.digitLabels[value],
                identifier: "numpad_key_\(value)",
                diameter: diameter,
                fill: theme.keyDigit,
                pressedFill: theme.keyDigitPressed,
                labelColor: theme.keyLabel,
                firesOnTouchDown: true,
                onActivate: { press(digit: value) }
            )
        case .delete:
            NumpadKeyButton(
                glyph: .symbol("delete.left"),
                glyphSize: 26,
                a11yLabel: "delete",
                identifier: "numpad_key_delete",
                diameter: diameter,
                fill: theme.keyFn,
                pressedFill: theme.keyFnPressed,
                labelColor: theme.keyLabel,
                firesOnTouchDown: false,
                onActivate: { pressDelete() },
                onLongPress: { pressClear() }
            )
        case .enter:
            NumpadKeyButton(
                glyph: .symbol("checkmark"),
                glyphSize: 26,
                a11yLabel: "enter",
                identifier: "numpad_key_enter",
                diameter: diameter,
                fill: theme.keyFn,
                pressedFill: theme.keyFnPressed,
                labelColor: theme.keyLabel,
                firesOnTouchDown: true,
                onActivate: { pressEnter() }
            )
        }
    }

    // MARK: - Events

    private func press(digit: Int) {
        entry.append()
        events(.token("D\(digit)"))
    }

    private func pressDelete() {
        entry.removeLast()
        events(.removeLast)
    }

    private func pressClear() {
        entry.clear()
        events(.clear)
    }

    private func pressEnter() {
        events(.commit)
        // In the capture modes the caption tells the outcome and no pulse ever
        // arrives, so the face clears itself immediately. On the lock screen
        // and in verifyCurrent the entry stays until the pulse or teardown.
        if mode == .captureNew || mode == .confirmNew {
            entry.clear()
        }
    }
}

/// The 3×4 grid as data, so the alphabet-drift test can read the emittable set.
enum NumpadKeypad {
    enum Key: Hashable, Sendable {
        case digit(Int)
        case delete
        case enter
    }

    static let rows: [[Key]] = [
        [.digit(1), .digit(2), .digit(3)],
        [.digit(4), .digit(5), .digit(6)],
        [.digit(7), .digit(8), .digit(9)],
        [.delete, .digit(0), .enter],
    ]

    static let digitLabels = ["zero", "one", "two", "three", "four",
                              "five", "six", "seven", "eight", "nine"]

    /// `✓` commits and `⌫` deletes — signals, never tokens.
    static var emittableTokens: [String] {
        rows.flatMap { $0 }.compactMap {
            if case .digit(let value) = $0 { return "D\(value)" }
            return nil
        }
    }
}

/// One circular key. Digits and `✓` fire on touch-down; `⌫` fires on release
/// unless a long-press already fired `clear` (decisions §2.3).
private struct NumpadKeyButton: View {
    enum Glyph {
        case text(String)
        case symbol(String)
    }

    let glyph: Glyph
    let glyphSize: CGFloat
    let a11yLabel: String
    let identifier: String
    let diameter: CGFloat
    let fill: Color
    let pressedFill: Color
    let labelColor: Color
    let firesOnTouchDown: Bool
    let onActivate: () -> Void
    var onLongPress: (() -> Void)?

    @State private var pressed = false
    @State private var longPressFired = false

    var body: some View {
        Circle()
            .fill(pressed ? pressedFill : fill)
            .frame(width: diameter, height: diameter)
            .overlay { glyphView }
            // Instant fill on press-down; 180ms ease-out release. No scale.
            .animation(pressed ? nil : .easeOut(duration: 0.18), value: pressed)
            .contentShape(Circle())
            .simultaneousGesture(longPress)
            .simultaneousGesture(press)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(a11yLabel)
            .accessibilityIdentifier(identifier)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onActivate() }
    }

    @ViewBuilder
    private var glyphView: some View {
        switch glyph {
        case .text(let value):
            Text(value)
                .font(.system(size: glyphSize, weight: .medium))
                .foregroundStyle(labelColor)
        case .symbol(let name):
            Image(systemName: name)
                .font(.system(size: glyphSize, weight: .medium))
                .foregroundStyle(labelColor)
        }
    }

    /// Platform-default long-press timeout; only `⌫` supplies a handler.
    private var longPress: some Gesture {
        LongPressGesture()
            .onEnded { _ in
                guard let onLongPress else { return }
                longPressFired = true
                onLongPress()
            }
    }

    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !pressed else { return }
                pressed = true
                // One identical haptic per key, on touch-down. A swallowed
                // release adds none, so a long-press is still exactly one.
                KeyHaptics.fire()
                if firesOnTouchDown { onActivate() }
            }
            .onEnded { _ in
                pressed = false
                if !firesOnTouchDown && !longPressFired { onActivate() }
                longPressFired = false
            }
    }
}

/// Resting face: title, empty dot row, keypad.
struct NumpadCoverFace: View {
    var body: some View {
        NumpadSurface(mode: .disguise, caption: nil, failedAttemptCount: 0, events: { _ in })
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
