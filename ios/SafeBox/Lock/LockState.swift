import Foundation

enum SetupPhase: Equatable, Sendable {
    case enterNew
    case confirm(pending: [String])
}

enum LockState: Equatable, Sendable {
    case firstRunSetup(SetupPhase)
    case locked
    case unlocked
}

/// Resolves one shared-copy-table entry: the key is the cross-platform string
/// ID (identical to Android's strings.xml names), the default value the
/// English fallback. Translations live in Support/Localizable.xcstrings.
func localizedCopy(_ key: StaticString, _ defaultValue: String.LocalizationValue) -> String {
    String(localized: key, defaultValue: defaultValue)
}

/// Consolidated copy table (design spec §6). No lock-screen string ever
/// contains "passcode", "vault", "unlock", or "SafeBox".
///
/// The caption entries here are the **calculator face's** caption mapping
/// (decisions §2.1) and are pinned verbatim; the other two faces map the same
/// `CaptionKind`s to their own strings.
enum LockCopy {
    static let setupEntryBanner = localizedCopy("setup_entry_banner", "Set your secret code: type it on the keypad, then press =")
    static let setupEntryHint = localizedCopy("setup_entry_hint", "Best: 6+ keys with a symbol (+ − × ÷ % ± .), and not a sum someone might really type.")
    static let setupTooShort = localizedCopy("setup_too_short", "Too short — use at least 4 keys")
    static let setupTooLong = localizedCopy("setup_too_long", "Too long — start again (max 32 keys)")
    static let setupConfirmBanner = localizedCopy("setup_confirm_banner", "Re-enter the same code, then press =")
    static let setupMismatch = localizedCopy("setup_mismatch", "Codes didn't match — start again")
    static let setupTrivialWarning = localizedCopy("setup_trivial_warning", "Easy to guess — re-enter it to keep it anyway, or enter a different code and press = to start over.")
    static let noRecoveryTitle = localizedCopy("setup_no_recovery_title", "Remember your code")
    static let noRecoveryBody = localizedCopy("setup_no_recovery_body", "There is no way to recover this code. If you forget it, your vault contents cannot be retrieved.")
    static let noRecoveryButton = localizedCopy("setup_no_recovery_button", "I understand")
    static let verifyCurrentCaption = localizedCopy("verify_current_caption", "Enter your current code, then press =")
    static let verifyError = localizedCopy("verify_error", "Incorrect code — try again")
    static let changeEnterNewCaption = localizedCopy("change_enter_new_caption", "Enter your new code, then press =")
    static let changeConfirmCaption = localizedCopy("change_confirm_caption", "Re-enter the new code, then press =")
    static let changeSuccess = localizedCopy("change_success", "Code changed")
    static let changeCancel = localizedCopy("change_cancel", "Cancel")
    static let settingsChangeTitle = localizedCopy("settings_change_title", "Change passcode")

    // Erase everything (nuke) — shown inside the vault only, never on the
    // lock screen, so vault vocabulary is fine here.
    static let nukeRowTitle = localizedCopy("nuke_row_title", "Erase everything")
    static let nukeRowSubtitle = localizedCopy("nuke_row_subtitle", "Delete all vault content and the passcode. The app starts over.")
    static let nukeConfirmTitle = localizedCopy("nuke_confirm_title", "Erase everything?")
    static let nukeConfirmBody = localizedCopy("nuke_confirm_body", "This permanently deletes every photo, note, and contact in the vault and removes your passcode. The app returns to its just-installed state.")
    static let nukeConfirmContinue = localizedCopy("nuke_confirm_continue", "Continue")
    static let nukeFinalTitle = localizedCopy("nuke_final_title", "Last chance")
    static let nukeFinalBody = localizedCopy("nuke_final_body", "All vault content will be destroyed immediately. There is no undo and no recovery.")
    static let nukeFinalErase = localizedCopy("nuke_final_erase", "Erase everything")
}
