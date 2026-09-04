import SwiftUI

/// The calculator face's own copy. The caption mapping is **pinned verbatim**
/// by `docs/plans/calculator-disguise-design.md` §6 — these are iteration 1's
/// exact strings, reached through the semantic seam instead of directly.
enum CalculatorCopy {
    static let displayName = localizedCopy("calculator_display_name", "Calculator")
    static let tagline = localizedCopy("calculator_tagline", "A fully working calculator. A wrong code just calculates — no error, no hint.")
    static let commitGesture = localizedCopy("calculator_commit_gesture", "the = key")
    static let guidePage3Title = localizedCopy("calculator_guide_page3_title", "Your code is a key sequence")
    static let guidePage3Body = localizedCopy("calculator_guide_page3_body", "Pick 4 to 32 calculator keys — digits and symbols all count, and order matters. Try one here. This is just practice; nothing is saved.")
    static let guideTry = localizedCopy("calculator_guide_try", "Tap at least 4 keys")
    static let guideOk = localizedCopy("calculator_guide_ok", "That would work — symbols make it stronger")
    static let guidePage4Title = localizedCopy("calculator_guide_page4_title", "Press = to enter")
    static let guidePage4Body = localizedCopy("calculator_guide_page4_body", "To unlock, type your code on the calculator and press =. A wrong code just calculates — no error, no hint that anything is hidden.")

    /// Decisions §2.1: the pinned mapping, one line per kind.
    static func text(for kind: CaptionKind) -> String {
        switch kind {
        case .promptNewSetup: LockCopy.setupEntryBanner
        case .promptNewChange: LockCopy.changeEnterNewCaption
        case .strengthHint: LockCopy.setupEntryHint
        case .tooShort: LockCopy.setupTooShort
        case .tooLong: LockCopy.setupTooLong
        case .promptConfirmSetup: LockCopy.setupConfirmBanner
        case .promptConfirmChange: LockCopy.changeConfirmCaption
        case .mismatch: LockCopy.setupMismatch
        case .trivialWarning: LockCopy.setupTrivialWarning
        case .promptCurrent: LockCopy.verifyCurrentCaption
        case .wrongCode: LockCopy.verifyError
        }
    }
}

/// The calculator: the default face and the only covert one. A non-match is
/// silent, forever.
@MainActor
struct CalculatorDisguise: DisguiseProviding {
    let id = "calculator"
    let isCovert = true

    /// The 17 passcode keys, in `CalcKey` declaration order. Derived from the
    /// enum so the descriptor cannot drift from the type.
    let alphabet = AlphabetDescriptor(
        tokenSetId: "calculator",
        alphabetVersion: 1,
        tokens: CalcKey.allCases.filter(\.isPasscodeKey).map(\.rawValue)
    )

    var displayName: String { CalculatorCopy.displayName }
    var tagline: String { CalculatorCopy.tagline }
    var commitGesture: String { CalculatorCopy.commitGesture }
    var identityGrade: DisguiseIdentityGrade { .native }
    var page3Title: String { CalculatorCopy.guidePage3Title }
    var page3Body: String { CalculatorCopy.guidePage3Body }
    var page3Try: String { CalculatorCopy.guideTry }
    var page3Ok: String { CalculatorCopy.guideOk }
    var page4Title: String { CalculatorCopy.guidePage4Title }
    var page4Body: String { CalculatorCopy.guidePage4Body }

    func makePlayground(onCountChanged: @escaping (Int) -> Void) -> AnyView {
        AnyView(CalculatorPlayground(onCountChanged: onCountChanged))
    }

    func makeCommitHero() -> AnyView {
        AnyView(PulsingKeyHero(glyph: "="))
    }

    func makeSurface(mode: DisguiseMode,
                     caption: LockBanner?,
                     failedAttemptCount: Int,
                     events: @escaping (DisguiseEvent) -> Void) -> AnyView {
        AnyView(CalculatorSurface(mode: mode,
                                  caption: caption,
                                  failedAttemptCount: failedAttemptCount,
                                  events: events))
    }

    func makeCoverFace() -> AnyView {
        AnyView(CalculatorCoverFace())
    }
}

// MARK: - Guide page 3 playground

/// A real mini keypad the user can tap to feel how a key-sequence code works.
/// Purely illustrative: nothing leaves this view but a count.
private struct CalculatorPlayground: View {
    let onCountChanged: (Int) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var taps: [String] = []

    private let rows: [[(label: String, isOp: Bool)]] = [
        [("7", false), ("8", false), ("9", false), ("÷", true)],
        [("4", false), ("5", false), ("6", false), ("×", true)],
        [("1", false), ("2", false), ("3", false), ("+", true)],
    ]

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        VStack(spacing: 0) {
            // Recorded-sequence chips (last 8 shown), popping in with a spring.
            HStack(spacing: 6) {
                if taps.count > 8 {
                    Text("…").font(.system(size: 18)).foregroundStyle(theme.caption)
                }
                ForEach(Array(taps.suffix(8).enumerated()), id: \.offset) { _, label in
                    Text(label)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(theme.keyLabel)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(theme.keyFn, in: RoundedRectangle(cornerRadius: 8))
                        .transition(.scale(scale: 0.5).combined(with: .opacity))
                }
            }
            .frame(minHeight: 40)
            .animation(.spring(duration: 0.35, bounce: 0.4), value: taps)

            VStack(spacing: 10) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 10) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, key in
                            DemoKey(label: key.label, isOperator: key.isOp, theme: theme) {
                                taps.append(key.label)
                                onCountChanged(taps.count)
                            }
                        }
                    }
                }
            }
            .padding(.top, 14)

            Button(GuideCopy.page3Clear) {
                withAnimation { taps.removeAll() }
                onCountChanged(0)
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.caption)
            .padding(.top, 10)
        }
    }
}

/// One demo key. Shared by the calculator and PIN pad playgrounds.
struct DemoKey: View {
    let label: String
    var isOperator = false
    let theme: DisguiseTheme
    var diameter: CGFloat = 58
    var isCircle = false
    let onTap: () -> Void

    @State private var pressed = false

    var body: some View {
        Text(label)
            .font(.system(size: 24, weight: .medium))
            .foregroundStyle(isOperator ? theme.keyLabelOnOp : theme.keyLabel)
            .frame(width: diameter, height: diameter)
            .background(fill, in: shape)
            .onTapGesture {
                KeyHaptics.fire()
                pressed = true
                withAnimation(.easeOut(duration: 0.18)) { pressed = false }
                onTap()
            }
            .accessibilityLabel("demo key \(label)")
    }

    private var shape: AnyShape {
        isCircle ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: 14))
    }

    private var fill: Color {
        if isOperator {
            return pressed ? theme.keyOpPressed : theme.keyOp
        } else {
            return pressed ? theme.keyDigitPressed : theme.keyDigit
        }
    }
}

// MARK: - Guide page 4 hero

/// A pulsing commit key — 0.85 s / 1.09× loop, shared by the calculator (`=`)
/// and the PIN pad (`✓`).
struct PulsingKeyHero: View {
    let glyph: String
    var isCircle = false

    @Environment(\.colorScheme) private var colorScheme
    @State private var pulsing = false

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        Group {
            if isCircle {
                Circle().fill(theme.keyOp)
            } else {
                RoundedRectangle(cornerRadius: 26).fill(theme.keyOp)
            }
        }
        .frame(width: 104, height: 104)
        .overlay {
            Text(glyph)
                .font(.system(size: 48, weight: .medium))
                .foregroundStyle(theme.keyLabelOnOp)
        }
        .scaleEffect(pulsing ? 1.09 : 1)
        .animation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true), value: pulsing)
        .onAppear { pulsing = true }
        .accessibilityHidden(true)
    }
}
