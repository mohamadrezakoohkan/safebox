import Foundation

/// Copy shared by every page of the guide, regardless of the selected face.
/// Deliberately NOT part of `LockCopy`: the guide runs only while no passcode
/// exists (first run) or inside the unlocked vault (revisit from Settings), so
/// vault vocabulary is allowed here — it never appears on the armed lock face.
///
/// Per-face guide copy lives in each face's own copy enum (`CalculatorCopy`,
/// `NumpadCopy`, `PatternCopy`).
enum GuideCopy {
    static let skip = localizedCopy("onboarding_skip", "Skip")
    static let next = localizedCopy("onboarding_next", "Next")
    static let start = localizedCopy("onboarding_start", "Set my code")
    static let page2Title = localizedCopy("onboarding_page2_title", "Secretly, it's your vault")
    static let page2Body = localizedCopy("onboarding_page2_body", "Everything stays on this device. No account, no cloud, no sync.")
    static let page2Photos = localizedCopy("onboarding_page2_photos", "Photos")
    static let page2PhotosSub = localizedCopy("onboarding_page2_photos_sub", "Private albums, imported from your library")
    static let page2Notes = localizedCopy("onboarding_page2_notes", "Notes")
    static let page2NotesSub = localizedCopy("onboarding_page2_notes_sub", "Rich text with tags and live preview")
    static let page2Contacts = localizedCopy("onboarding_page2_contacts", "Contacts")
    static let page2ContactsSub = localizedCopy("onboarding_page2_contacts_sub", "People only you know about")
    static let page3Clear = localizedCopy("onboarding_page3_clear", "Reset")
    static let page4Warning = localizedCopy("onboarding_page4_warning", "There is no recovery. If you forget your code, the vault stays locked forever.")
}
