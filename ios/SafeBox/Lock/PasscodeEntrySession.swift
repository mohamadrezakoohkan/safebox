import Foundation
import Observation

/// The Settings re-enrollment flows (both run entirely while unlocked). Silence
/// is a disguise feature only on the lock screen — here every wrong input gets
/// visible feedback.
///
/// - `.changePasscode`: VERIFY_CURRENT → ENTER_NEW → CONFIRM on the same face.
/// - `.changeDisguise`: VERIFY_CURRENT → PICK → ENTER_NEW → CONFIRM on the new
///   face (decisions §5). Both end in exactly ONE `set(...)`.
@MainActor
@Observable
final class PasscodeEntrySession {
    enum Kind: Equatable, Sendable {
        case changePasscode
        case changeDisguise
    }

    enum Phase: Equatable {
        case verifyCurrent
        case pickDisguise
        case enterNew
        case confirm(pending: [String])
        case done
    }

    let kind: Kind
    private(set) var phase: Phase = .verifyCurrent
    private(set) var caption: LockBanner
    /// §1.1 pulse. In `verifyCurrent` EVERY face is pulsed on a non-accepted
    /// commit — this generalizes iteration 1's `shakeToken`.
    private(set) var failedAttemptCount = 0

    /// The enrolled face; owns the VERIFY_CURRENT surface.
    let currentDisguise: any DisguiseProviding
    /// The face the new code is captured on. Equal to `currentDisguise` for a
    /// plain change, so `activeDisguiseId` is preserved.
    private(set) var targetDisguise: any DisguiseProviding

    private let passcodeStore: any PasscodeStore
    let registry: DisguiseRegistry

    init(passcodeStore: any PasscodeStore,
         registry: DisguiseRegistry,
         currentDisguise: any DisguiseProviding,
         kind: Kind = .changePasscode) {
        self.passcodeStore = passcodeStore
        self.registry = registry
        self.currentDisguise = currentDisguise
        self.targetDisguise = currentDisguise
        self.kind = kind
        caption = LockBanner(primary: .promptCurrent)
    }

    // MARK: - What the host renders

    /// Which face owns the surface right now.
    var surfaceDisguise: any DisguiseProviding {
        switch phase {
        case .verifyCurrent, .pickDisguise, .done: currentDisguise
        case .enterNew, .confirm: targetDisguise
        }
    }

    var surfaceMode: DisguiseMode {
        switch phase {
        case .verifyCurrent, .pickDisguise, .done: .verifyCurrent
        case .enterNew: .captureNew
        case .confirm: .confirmNew
        }
    }

    var navigationTitle: String {
        switch kind {
        case .changePasscode: LockCopy.settingsChangeTitle
        case .changeDisguise: VaultCopy.settingsChangeDisguiseTitle
        }
    }

    // MARK: - Caption revert (§1.3)

    /// While the caption is WRONG_CODE, any `token`, `clear` or `removeLast`
    /// reverts it to PROMPT_CURRENT. A `commit` does not.
    func eventObserved(_ event: DisguiseEvent) {
        guard caption.primary.isError else { return }
        switch event {
        case .token, .clear, .removeLast:
            caption = LockBanner(primary: .promptCurrent)
        case .commit:
            break
        }
    }

    // MARK: - Picking a new face

    /// The current face is never pickable (§5): switching to it would be a
    /// no-op re-enrollment. The picker's CTA is disabled on that card.
    func canPick(_ disguise: any DisguiseProviding) -> Bool {
        disguise.id != currentDisguise.id
    }

    func pick(_ disguise: any DisguiseProviding) {
        guard phase == .pickDisguise, canPick(disguise) else { return }
        targetDisguise = disguise
        phase = .enterNew
        caption = LockBanner(primary: .promptNewChange, secondary: .strengthHint)
    }

    // MARK: - Commits

    func commit(tokens: [String], overflowed: Bool) async {
        switch phase {
        case .verifyCurrent:
            // ANY commit that is not the exact current code fails visibly —
            // including sub-minimum and overflowed commits, which still skip
            // the KDF (design spec §5.6, decisions §1.1).
            if !overflowed, PasscodeRules.isValidLength(tokens),
               await passcodeStore.matches(tokens: tokens) {
                switch kind {
                case .changePasscode:
                    phase = .enterNew
                    caption = LockBanner(primary: .promptNewChange, secondary: .strengthHint)
                case .changeDisguise:
                    phase = .pickDisguise
                    caption = LockBanner(primary: .promptCurrent)
                }
            } else {
                caption = LockBanner(primary: .wrongCode)
                failedAttemptCount += 1
            }

        case .pickDisguise, .done:
            break

        case .enterNew:
            if overflowed {
                caption = LockBanner(primary: .tooLong, secondary: .strengthHint)
                return
            }
            guard tokens.count >= PasscodeRules.minTokens else {
                caption = LockBanner(primary: .tooShort, secondary: .strengthHint)
                return
            }
            let warning: CaptionKind? = PasscodeRules.isTrivial(tokens) ? .trivialWarning : nil
            phase = .confirm(pending: tokens)
            caption = LockBanner(primary: .promptConfirmChange, secondary: warning)

        case .confirm(let pending):
            if !overflowed && tokens == pending {
                do {
                    // ONE atomic replace: fresh salt, the target face's
                    // alphabet and id. Until it lands the old blob is
                    // authoritative — old code valid, old face shown.
                    try await passcodeStore.set(tokens: tokens,
                                                alphabet: targetDisguise.alphabet,
                                                activeDisguiseId: targetDisguise.id)
                    phase = .done
                } catch {
                    phase = .enterNew
                    caption = LockBanner(primary: .promptNewChange, secondary: .strengthHint)
                }
            } else {
                phase = .enterNew
                caption = LockBanner(primary: .mismatch, secondary: .strengthHint)
            }
        }
    }
}
