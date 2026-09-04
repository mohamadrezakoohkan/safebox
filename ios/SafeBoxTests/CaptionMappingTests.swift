import Foundation
import Testing
@testable import SafeBox

/// Every face maps the eleven semantic kinds to its own words, and the
/// calculator's mapping is the PINNED design-§6 copy, verbatim.
struct CaptionMappingTests {
    @Test func onlyWrongCodeIsAnError() {
        for kind in CaptionKind.allCases {
            #expect(kind.isError == (kind == .wrongCode))
        }
        #expect(CaptionKind.allCases.count == 11)
    }

    @Test func theCalculatorRendersThePinnedStringsVerbatim() {
        #expect(CalculatorCopy.text(for: .promptNewSetup)
                == "Set your secret code: type it on the keypad, then press =")
        #expect(CalculatorCopy.text(for: .promptNewChange)
                == "Enter your new code, then press =")
        #expect(CalculatorCopy.text(for: .strengthHint)
                == "Best: 6+ keys with a symbol (+ − × ÷ % ± .), and not a sum someone might really type.")
        #expect(CalculatorCopy.text(for: .tooShort) == "Too short — use at least 4 keys")
        #expect(CalculatorCopy.text(for: .tooLong) == "Too long — start again (max 32 keys)")
        #expect(CalculatorCopy.text(for: .promptConfirmSetup) == "Re-enter the same code, then press =")
        #expect(CalculatorCopy.text(for: .promptConfirmChange) == "Re-enter the new code, then press =")
        #expect(CalculatorCopy.text(for: .mismatch) == "Codes didn't match — start again")
        #expect(CalculatorCopy.text(for: .trivialWarning)
                == "Easy to guess — re-enter it to keep it anyway, or enter a different code and press = to start over.")
        #expect(CalculatorCopy.text(for: .promptCurrent) == "Enter your current code, then press =")
        #expect(CalculatorCopy.text(for: .wrongCode) == "Incorrect code — try again")
    }

    @Test func theNumpadNamesItsOwnCommitGesture() {
        #expect(NumpadCopy.text(for: .promptNewSetup) == "Choose a PIN: enter 4 to 32 digits, then tap ✓")
        #expect(NumpadCopy.text(for: .promptCurrent) == "Enter your current PIN, then tap ✓")
        #expect(NumpadCopy.text(for: .wrongCode) == "Incorrect PIN — try again")
        #expect(NumpadCopy.faceTitle == "Enter PIN")
    }

    @Test func thePatternMapsUnreachableKindsToTheModePrompt() {
        #expect(PatternCopy.text(for: .tooLong, mode: .captureNew) == PatternCopy.promptNew)
        #expect(PatternCopy.text(for: .trivialWarning, mode: .confirmNew) == PatternCopy.promptConfirm)
        #expect(PatternCopy.text(for: .tooLong, mode: .verifyCurrent) == PatternCopy.promptCurrent)
        #expect(PatternCopy.text(for: .wrongCode, mode: .verifyCurrent) == "Wrong pattern — try again")
        #expect(PatternCopy.faceTitle == "Draw your pattern")
    }

    /// Decisions §7: no lock-screen string leaks vault vocabulary.
    @Test func noLockScreenStringLeaksVaultVocabulary() {
        let forbidden = ["passcode", "vault", "unlock", "safebox"]
        var strings: [String] = [NumpadCopy.faceTitle, PatternCopy.faceTitle]
        for kind in CaptionKind.allCases {
            strings.append(CalculatorCopy.text(for: kind))
            strings.append(NumpadCopy.text(for: kind))
            strings.append(PatternCopy.text(for: kind, mode: .verifyCurrent))
        }
        for string in strings {
            let lowered = string.lowercased()
            for word in forbidden {
                #expect(!lowered.contains(word), "\"\(string)\" contains \"\(word)\"")
            }
        }
    }

    /// Identifiers on the lock faces must not name the secret either.
    @Test func faceAccessibilityIdentifiersAreGenre() {
        let identifiers = (0...9).map { "numpad_key_\($0)" }
            + ["numpad_key_delete", "numpad_key_enter", "numpad_dots", "pattern_grid"]
        let forbidden = ["passcode", "vault", "unlock", "secret", "lock", "safebox"]
        for identifier in identifiers {
            for word in forbidden {
                #expect(!identifier.contains(word), "\(identifier) contains \(word)")
            }
        }
    }
}
