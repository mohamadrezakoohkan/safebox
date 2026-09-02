import Foundation
import Observation

/// Settings change-passcode flow (runs entirely while unlocked). Silence is a
/// disguise feature only on the lock screen — here wrong input gets visible
/// feedback.
@MainActor
@Observable
final class PasscodeEntrySession {
    enum Phase: Equatable {
        case verifyCurrent
        case enterNew
        case confirm(pending: [CalcKey])
        case done
    }

    private(set) var phase: Phase = .verifyCurrent
    private(set) var banner: LockBanner
    private(set) var bannerIsError = false
    /// Bumped to trigger the shake animation on the display readout.
    private(set) var shakeToken = 0

    private let passcodeStore: any PasscodeStore

    init(passcodeStore: any PasscodeStore) {
        self.passcodeStore = passcodeStore
        banner = LockBanner(primary: LockCopy.verifyCurrentCaption)
    }

    /// Reverts an error caption back to the phase caption (called on key press).
    func keyPressed() {
        if bannerIsError {
            bannerIsError = false
            banner = LockBanner(primary: LockCopy.verifyCurrentCaption)
        }
    }

    func commit(sequence: [CalcKey], overflowed: Bool) async {
        switch phase {
        case .verifyCurrent:
            // ANY commit that is not the exact current code shows verify_error —
            // including sub-minimum and overflowed commits (design spec §5.6).
            if !overflowed, PasscodeRules.isValidLength(sequence),
               await passcodeStore.matches(sequence: sequence) {
                phase = .enterNew
                bannerIsError = false
                banner = LockBanner(primary: LockCopy.changeEnterNewCaption, secondary: LockCopy.setupEntryHint)
            } else {
                banner = LockBanner(primary: LockCopy.verifyError)
                bannerIsError = true
                shakeToken += 1
            }

        case .enterNew:
            if overflowed {
                banner = LockBanner(primary: LockCopy.setupTooLong, secondary: LockCopy.setupEntryHint)
                return
            }
            guard sequence.count >= PasscodeRules.minKeys else {
                banner = LockBanner(primary: LockCopy.setupTooShort, secondary: LockCopy.setupEntryHint)
                return
            }
            let warning = PasscodeRules.isTrivial(sequence) ? LockCopy.setupTrivialWarning : nil
            phase = .confirm(pending: sequence)
            banner = LockBanner(primary: LockCopy.changeConfirmCaption, secondary: warning)

        case .confirm(let pending):
            if !overflowed && sequence == pending {
                do {
                    // Fresh salt; the Keychain item is replaced atomically.
                    try await passcodeStore.set(sequence: sequence)
                    phase = .done
                } catch {
                    phase = .enterNew
                    banner = LockBanner(primary: LockCopy.changeEnterNewCaption, secondary: LockCopy.setupEntryHint)
                }
            } else {
                phase = .enterNew
                banner = LockBanner(primary: LockCopy.setupMismatch, secondary: LockCopy.setupEntryHint)
            }

        case .done:
            break
        }
    }
}
