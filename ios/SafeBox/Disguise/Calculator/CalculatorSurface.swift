import SwiftUI

/// Rendered caption text for a face: the semantic kinds already resolved to
/// this face's own words.
struct FaceCaption: Equatable, Sendable {
    var primary: String
    var primaryIsError: Bool
    var secondary: String?
}

/// The calculator face's pure presentation: caption strip + display + 4×5
/// keypad. Behavior, metrics, engine and haptics are byte-identical to
/// iteration 1 — only the plumbing above it changed.
struct CalculatorFace: View {
    let display: String
    let caption: FaceCaption?
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
            // Caption strip: only composed when a caption exists (never in
            // pure Disguise mode — not hidden, not transparent: not composed).
            if let caption {
                CaptionStrip(primary: caption.primary,
                             primaryIsError: caption.primaryIsError,
                             secondary: caption.secondary,
                             theme: theme,
                             horizontalPadding: metrics.sideMargin)
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
        .animation(.easeInOut(duration: 0.15), value: caption)
        .animation(.easeInOut(duration: 0.3), value: shakeToken)
    }

    // MARK: - Keypad

    private func keypad(theme: DisguiseTheme, metrics: KeypadMetrics) -> some View {
        let k = metrics.keyWidth
        let h = metrics.keyHeight
        let g = metrics.gutter
        return VStack(spacing: metrics.verticalGutter) {
            ForEach(Array(CalculatorKeypad.rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: g) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, entry in
                        button(for: entry, theme: theme, k: k, h: h, g: g)
                    }
                }
            }
        }
        .frame(width: metrics.contentWidth)
    }

    private func button(for entry: CalculatorKeypad.Key,
                        theme: DisguiseTheme,
                        k: CGFloat, h: CGFloat, g: CGFloat) -> some View {
        let isClear = entry.key == .clear
        let label = isClear ? clearLabel : entry.label
        let a11y = isClear ? (clearLabel == "AC" ? "all clear" : "clear") : entry.a11yLabel
        let width = entry.span == 2 ? 2 * k + g : k
        return CalcButton(
            label: label,
            accessibilityLabel: a11y,
            kind: entry.kind,
            width: width,
            height: h,
            theme: theme,
            showRing: ringOperator != nil && ringOperator == entry.key.asOperator,
            glyphCenterX: entry.span == 2 ? k / 2 : nil
        ) {
            onKey(entry.key)
        }
    }
}

/// The calculator face wired to the host seam: owns the engine view model and
/// maps `CaptionKind` to the pinned strings.
struct CalculatorSurface: View {
    let mode: DisguiseMode
    let caption: LockBanner?
    let failedAttemptCount: Int
    let events: (DisguiseEvent) -> Void

    @State private var viewModel: CalculatorViewModel
    /// Only ever driven by a pulse observed AFTER the first render.
    @State private var shakeToken = 0

    init(mode: DisguiseMode,
         caption: LockBanner?,
         failedAttemptCount: Int,
         events: @escaping (DisguiseEvent) -> Void) {
        self.mode = mode
        self.caption = caption
        self.failedAttemptCount = failedAttemptCount
        self.events = events
        _viewModel = State(initialValue: CalculatorViewModel(events: events))
    }

    var body: some View {
        CalculatorFace(
            display: viewModel.display,
            caption: renderedCaption,
            shakeToken: shakeToken,
            clearLabel: viewModel.clearLabel,
            ringOperator: viewModel.ringOperator,
            onKey: { viewModel.press($0) }
        )
        .onChange(of: failedAttemptCount) { old, new in
            // Covert: by §1.1 the calculator never receives a pulse in
            // `disguise` mode. It shakes the display readout in verifyCurrent,
            // exactly as iteration 1 did.
            guard new > old else { return }
            shakeToken += 1
        }
    }

    /// Never composed in `disguise` mode — the calculator has no static title.
    private var renderedCaption: FaceCaption? {
        guard mode != .disguise, let caption else { return nil }
        return FaceCaption(
            primary: CalculatorCopy.text(for: caption.primary),
            primaryIsError: caption.primary.isError,
            secondary: caption.secondary.map(CalculatorCopy.text(for:))
        )
    }
}

/// The static resting face: snapshot cover and carousel thumbnail.
struct CalculatorCoverFace: View {
    var body: some View {
        CalculatorFace(
            display: "0",
            caption: nil,
            clearLabel: "AC",
            ringOperator: nil,
            onKey: { _ in }
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private extension CalcKey {
    /// The operator a key stands for, for the pending-operator ring.
    var asOperator: CalcOperator? {
        switch self {
        case .add: .add
        case .sub: .sub
        case .mul: .mul
        case .div: .div
        default: nil
        }
    }
}
