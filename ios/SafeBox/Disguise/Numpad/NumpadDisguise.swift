import SwiftUI

/// The PIN pad face's own copy (decisions §7). No string here contains
/// "passcode", "vault", "unlock" or "SafeBox".
enum NumpadCopy {
    static let displayName = localizedCopy("numpad_display_name", "PIN pad")
    static let tagline = localizedCopy("numpad_tagline", "A plain PIN screen. A wrong PIN shakes and clears, like a phone lock.")
    static let commitGesture = localizedCopy("numpad_commit_gesture", "the ✓ key")
    static let guidePage3Title = localizedCopy("numpad_guide_page3_title", "Your PIN is 4 to 32 digits")
    static let guidePage3Body = localizedCopy("numpad_guide_page3_body", "Any digits, any length from 4 to 32 — order matters. Try one here. This is just practice; nothing is saved.")
    static let guideTry = localizedCopy("numpad_guide_try", "Tap at least 4 digits")
    static let guideOk = localizedCopy("numpad_guide_ok", "That would work — more digits make it stronger")
    static let guidePage4Title = localizedCopy("numpad_guide_page4_title", "Tap ✓ to enter")
    static let guidePage4Body = localizedCopy("numpad_guide_page4_body", "To unlock, enter your PIN and tap ✓. A wrong PIN shakes and clears — anyone can see it's a lock screen, but not what it protects.")

    static let faceTitle = localizedCopy("numpad_face_title", "Enter PIN")
    static let promptNew = localizedCopy("numpad_prompt_new", "Choose a PIN: enter 4 to 32 digits, then tap ✓")
    static let promptNewChange = localizedCopy("numpad_prompt_new_change", "Enter your new PIN, then tap ✓")
    static let hint = localizedCopy("numpad_hint", "Best: 6 or more digits — not a date, not a repeat.")
    static let tooShort = localizedCopy("numpad_too_short", "Too short — use at least 4 digits")
    static let tooLong = localizedCopy("numpad_too_long", "Too long — start again (max 32 digits)")
    static let promptConfirm = localizedCopy("numpad_prompt_confirm", "Re-enter the same PIN, then tap ✓")
    static let promptConfirmChange = localizedCopy("numpad_prompt_confirm_change", "Re-enter the new PIN, then tap ✓")
    static let mismatch = localizedCopy("numpad_mismatch", "PINs didn't match — start again")
    static let trivialWarning = localizedCopy("numpad_trivial_warning", "Easy to guess — re-enter it to keep it anyway, or enter a different PIN to start over.")
    static let promptCurrent = localizedCopy("numpad_prompt_current", "Enter your current PIN, then tap ✓")
    static let wrongCode = localizedCopy("numpad_wrong_code", "Incorrect PIN — try again")

    static func text(for kind: CaptionKind) -> String {
        switch kind {
        case .promptNewSetup: promptNew
        case .promptNewChange: promptNewChange
        case .strengthHint: hint
        case .tooShort: tooShort
        case .tooLong: tooLong
        case .promptConfirmSetup: promptConfirm
        case .promptConfirmChange: promptConfirmChange
        case .mismatch: mismatch
        case .trivialWarning: trivialWarning
        case .promptCurrent: promptCurrent
        case .wrongCode: wrongCode
        }
    }
}

/// A plain PIN screen. Overt: a wrong PIN shakes and clears.
///
/// The digit token IDs deliberately coincide with the calculator's: token IDs
/// are opaque per alphabet, salts differ per enrollment, and the overlap gives
/// the fail-closed calculator face a chance to still accept a digits-only PIN
/// if a face ever fails to resolve (§2.3).
@MainActor
struct NumpadDisguise: DisguiseProviding {
    let id = "numpad"
    let isCovert = false

    let alphabet = AlphabetDescriptor(
        tokenSetId: "numpad",
        alphabetVersion: 1,
        tokens: (0...9).map { "D\($0)" }
    )

    var displayName: String { NumpadCopy.displayName }
    var tagline: String { NumpadCopy.tagline }
    var commitGesture: String { NumpadCopy.commitGesture }
    var identityGrade: DisguiseIdentityGrade { .incoherent }
    var page3Title: String { NumpadCopy.guidePage3Title }
    var page3Body: String { NumpadCopy.guidePage3Body }
    var page3Try: String { NumpadCopy.guideTry }
    var page3Ok: String { NumpadCopy.guideOk }
    var page4Title: String { NumpadCopy.guidePage4Title }
    var page4Body: String { NumpadCopy.guidePage4Body }

    func makePlayground(onCountChanged: @escaping (Int) -> Void) -> AnyView {
        AnyView(NumpadPlayground(onCountChanged: onCountChanged))
    }

    func makeCommitHero() -> AnyView {
        AnyView(PulsingKeyHero(glyph: "✓", isCircle: true))
    }

    func makeSurface(mode: DisguiseMode,
                     caption: LockBanner?,
                     failedAttemptCount: Int,
                     events: @escaping (DisguiseEvent) -> Void) -> AnyView {
        AnyView(NumpadSurface(mode: mode,
                              caption: caption,
                              failedAttemptCount: failedAttemptCount,
                              events: events))
    }

    func makeCoverFace() -> AnyView {
        AnyView(NumpadCoverFace())
    }
}

// MARK: - Guide page 3 playground

/// A compact digits-only PIN pad filling a mini dot row. Nothing leaves this view but
/// a count.
private struct NumpadPlayground: View {
    let onCountChanged: (Int) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var entry = NumpadEntry()

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ForEach(0..<entry.visibleDots, id: \.self) { _ in
                    Circle()
                        .fill(theme.displayText)
                        .frame(width: 10, height: 10)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(minHeight: 40)
            .animation(.spring(duration: 0.3, bounce: 0.4), value: entry)

            VStack(spacing: 10) {
                // All four rows, but the `if case .digit` filter below drops
                // ⌫ and ✓ — they have no meaning in a practice pad. That
                // leaves 1-9 plus a lone 0, matching Android exactly.
                ForEach(NumpadKeypad.rows, id: \.self) { row in
                    HStack(spacing: 10) {
                        ForEach(row, id: \.self) { key in
                            if case .digit(let value) = key {
                                DemoKey(label: String(value), theme: theme,
                                        diameter: 54, isCircle: true) {
                                    entry.append()
                                    onCountChanged(entry.count)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.top, 8)

            Button(GuideCopy.page3Clear) {
                withAnimation { entry.clear() }
                onCountChanged(0)
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.caption)
            .padding(.top, 10)
        }
    }
}
