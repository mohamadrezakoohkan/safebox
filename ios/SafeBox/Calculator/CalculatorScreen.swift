import SwiftUI

/// The full calculator face: caption strip + display + 4×5 keypad.
/// One component serves three consumers via its inputs: the lock screen,
/// first-run setup, and the Settings change-passcode flow.
struct CalculatorSurface: View {
    let display: String
    let banner: LockBanner?
    var bannerIsError = false
    var shakeToken = 0
    let clearLabel: String
    let ringOperator: CalcOperator?
    let onKey: (CalcKey) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { geo in
            let theme = DisguiseTheme.theme(for: colorScheme)
            let metrics = KeypadMetrics(size: geo.size)
            VStack(spacing: 0) {
                displayRegion(theme: theme, metrics: metrics)
                keypad(theme: theme, metrics: metrics)
                    .padding(.bottom, metrics.gutter)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background { backgroundColor.ignoresSafeArea() }
    }

    private var backgroundColor: Color {
        DisguiseTheme.theme(for: colorScheme).background
    }

    // MARK: - Display region

    private func displayRegion(theme: DisguiseTheme, metrics: KeypadMetrics) -> some View {
        VStack(spacing: 0) {
            // Caption strip: only composed when a banner exists (never in
            // pure Disguise mode — not hidden, not transparent: not composed).
            if let banner {
                VStack(spacing: 2) {
                    Text(banner.primary)
                        .foregroundStyle(bannerIsError ? theme.captionError : theme.caption)
                    if let secondary = banner.secondary {
                        Text(secondary)
                            .foregroundStyle(theme.caption)
                    }
                }
                .font(.system(size: 13))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 28)
                .padding(.horizontal, metrics.sideMargin)
                .padding(.top, 4)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            Spacer(minLength: 0)
            Text(display)
                .font(.system(size: metrics.contentWidth / 5.2, weight: .regular))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .foregroundStyle(theme.displayText)
                .frame(maxWidth: metrics.contentWidth, alignment: .trailing)
                .padding(.horizontal, metrics.sideMargin)
                .padding(.vertical, metrics.gutter)
                .modifier(ShakeEffect(animatableData: CGFloat(shakeToken)))
                .accessibilityLabel("result")
                .accessibilityValue(display)
        }
        .animation(.easeInOut(duration: 0.15), value: banner)
        .animation(.easeInOut(duration: 0.3), value: shakeToken)
    }

    // MARK: - Keypad

    private func keypad(theme: DisguiseTheme, metrics: KeypadMetrics) -> some View {
        let k = metrics.keyWidth
        let h = metrics.keyHeight
        let g = metrics.gutter
        return VStack(spacing: metrics.verticalGutter) {
            HStack(spacing: g) {
                key(clearLabel, clearLabel == "AC" ? "all clear" : "clear", .fn, .clear, theme, k, h)
                key("±", "plus minus", .fn, .sign, theme, k, h)
                key("%", "percent", .fn, .pct, theme, k, h)
                key("÷", "divide", .op, .div, theme, k, h, ring: ringOperator == .div)
            }
            HStack(spacing: g) {
                key("7", "seven", .digit, .d7, theme, k, h)
                key("8", "eight", .digit, .d8, theme, k, h)
                key("9", "nine", .digit, .d9, theme, k, h)
                key("×", "multiply", .op, .mul, theme, k, h, ring: ringOperator == .mul)
            }
            HStack(spacing: g) {
                key("4", "four", .digit, .d4, theme, k, h)
                key("5", "five", .digit, .d5, theme, k, h)
                key("6", "six", .digit, .d6, theme, k, h)
                key("−", "minus", .op, .sub, theme, k, h, ring: ringOperator == .sub)
            }
            HStack(spacing: g) {
                key("1", "one", .digit, .d1, theme, k, h)
                key("2", "two", .digit, .d2, theme, k, h)
                key("3", "three", .digit, .d3, theme, k, h)
                key("+", "plus", .op, .add, theme, k, h, ring: ringOperator == .add)
            }
            HStack(spacing: g) {
                CalcButton(label: "0", accessibilityLabel: "zero", kind: .digit,
                           width: 2 * k + g, height: h, theme: theme,
                           glyphCenterX: k / 2) { onKey(.d0) }
                key(".", "decimal point", .digit, .dot, theme, k, h)
                key("=", "equals", .op, .equals, theme, k, h)
            }
        }
        .frame(width: metrics.contentWidth)
    }

    private func key(_ label: String, _ a11y: String, _ kind: CalcKeyKind, _ calcKey: CalcKey,
                     _ theme: DisguiseTheme, _ w: CGFloat, _ h: CGFloat, ring: Bool = false) -> some View {
        CalcButton(label: label, accessibilityLabel: a11y, kind: kind,
                   width: w, height: h, theme: theme, showRing: ring) {
            onKey(calcKey)
        }
    }
}

/// ±8pt horizontal shake, 3 cycles (design spec §5.6) — VerifyCurrent only.
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

/// The lock screen / first-run setup calculator, driven by AppLockCoordinator.
struct LockCalculatorView: View {
    let coordinator: AppLockCoordinator
    @State private var viewModel: CalculatorViewModel

    init(coordinator: AppLockCoordinator) {
        self.coordinator = coordinator
        _viewModel = State(initialValue: CalculatorViewModel(onCommit: { keys, overflowed in
            // Verification runs off the UI path; the display already rendered.
            Task { await coordinator.commit(sequence: keys, overflowed: overflowed) }
        }))
    }

    var body: some View {
        CalculatorSurface(
            display: viewModel.display,
            banner: coordinator.banner,
            clearLabel: viewModel.clearLabel,
            ringOperator: viewModel.ringOperator,
            onKey: { viewModel.press($0) }
        )
        .statusBarHidden(false)
    }
}
