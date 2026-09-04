import Foundation

/// Iteration-2 vault copy table (decisions §10). Every key is the shared
/// cross-platform string ID — identical to the Android `strings.xml` name —
/// and the default value is the English source. Translations live in
/// Support/Localizable.xcstrings; integer arguments are `%lld` there.
///
/// Everything in this enum is vault-side vocabulary. It may only be rendered
/// inside the unlocked vault (`MainTabView` and below) — never from the locked
/// calculator or anything reachable from it.
///
/// Naming: the constant is the camelCased ID (`empty_albums_title` →
/// `emptyAlbumsTitle`). Keys with a format argument are static functions.
enum VaultCopy {
    // MARK: - Tabs

    static let vaultTabGallery = localizedCopy("vault_tab_gallery", "Gallery")
    static let vaultTabNotes = localizedCopy("vault_tab_notes", "Notes")
    static let vaultTabContacts = localizedCopy("vault_tab_contacts", "Contacts")
    static let vaultTabSettings = localizedCopy("vault_tab_settings", "Settings")

    // MARK: - Common actions

    static let cancelAction = localizedCopy("cancel_action", "Cancel")
    static let deleteAction = localizedCopy("delete_action", "Delete")
    static let doneAction = localizedCopy("done_action", "Done")
    static let okAction = localizedCopy("ok_action", "OK")
    static let selectAction = localizedCopy("select_action", "Select")
    static let undoAction = localizedCopy("undo_action", "Undo")

    /// "N selected" — selection-mode toolbar title (P6, photo grid).
    static func selectionCount(_ count: Int) -> String {
        localizedCopy("selection_count", "\(count) selected")
    }

    // MARK: - Empty states (P2)

    static let emptyAlbumsTitle = localizedCopy("empty_albums_title", "No albums yet")
    static let emptyAlbumsBody = localizedCopy("empty_albums_body", "Albums keep your imported photos and videos organized.")
    static let emptyAlbumsAction = localizedCopy("empty_albums_action", "Create album")
    static let emptyPhotosTitle = localizedCopy("empty_photos_title", "No photos yet")
    static let emptyPhotosBody = localizedCopy("empty_photos_body", "Imports are copies — the originals stay in your library.")
    static let emptyPhotosAction = localizedCopy("empty_photos_action", "Import photos")
    static let emptyNotesTitle = localizedCopy("empty_notes_title", "No notes yet")
    static let emptyNotesBody = localizedCopy("empty_notes_body", "Notes support markdown with a live preview.")
    static let emptyNotesAction = localizedCopy("empty_notes_action", "New note")
    static let emptyContactsTitle = localizedCopy("empty_contacts_title", "No contacts yet")
    static let emptyContactsBody = localizedCopy("empty_contacts_body", "Contacts live only in this vault.")
    static let emptyContactsAction = localizedCopy("empty_contacts_action", "Add contact")
    static let emptyResultsTitle = localizedCopy("empty_results_title", "No results")
    static let emptyResultsBody = localizedCopy("empty_results_body", "Check the spelling or try a different search.")

    // MARK: - Delete confirmations (P3, P6)

    /// "Delete album and its N photos?"
    static func confirmDeleteAlbum(photoCount: Int) -> String {
        localizedCopy("confirm_delete_album", "Delete album and its \(photoCount) photos?")
    }

    static let confirmDeletePhoto = localizedCopy("confirm_delete_photo", "Delete this photo?")

    /// "Delete N photos?"
    static func confirmDeletePhotos(_ count: Int) -> String {
        localizedCopy("confirm_delete_photos", "Delete \(count) photos?")
    }

    static let confirmDeleteNote = localizedCopy("confirm_delete_note", "Delete this note?")

    /// "Delete N notes?"
    static func confirmDeleteNotes(_ count: Int) -> String {
        localizedCopy("confirm_delete_notes", "Delete \(count) notes?")
    }

    static let confirmDeleteContact = localizedCopy("confirm_delete_contact", "Delete this contact?")

    /// "Delete N contacts?"
    static func confirmDeleteContacts(_ count: Int) -> String {
        localizedCopy("confirm_delete_contacts", "Delete \(count) contacts?")
    }

    /// Shared body for every delete confirm once soft-delete lands (P3).
    static let confirmDeleteBodyTrash = localizedCopy("confirm_delete_body_trash", "You can restore it from Recently deleted for 30 days.")

    // MARK: - Deleted toasts (P3 undo affordance)

    static let deletedAlbum = localizedCopy("deleted_album", "Album deleted")
    static let deletedPhoto = localizedCopy("deleted_photo", "Photo deleted")

    /// "N photos deleted"
    static func deletedPhotos(_ count: Int) -> String {
        localizedCopy("deleted_photos", "\(count) photos deleted")
    }

    static let deletedNote = localizedCopy("deleted_note", "Note deleted")

    /// "N notes deleted"
    static func deletedNotes(_ count: Int) -> String {
        localizedCopy("deleted_notes", "\(count) notes deleted")
    }

    static let deletedContact = localizedCopy("deleted_contact", "Contact deleted")

    /// "N contacts deleted"
    static func deletedContacts(_ count: Int) -> String {
        localizedCopy("deleted_contacts", "\(count) contacts deleted")
    }

    // MARK: - Recently deleted (P3)

    static let trashTitle = localizedCopy("trash_title", "Recently deleted")
    static let trashSubtitle = localizedCopy("trash_subtitle", "Items are kept for 30 days, then deleted permanently.")
    static let trashRestore = localizedCopy("trash_restore", "Restore")
    static let trashDeleteNow = localizedCopy("trash_delete_now", "Delete now")
    static let trashEmpty = localizedCopy("trash_empty", "Empty")
    static let trashEmptyConfirmTitle = localizedCopy("trash_empty_confirm_title", "Delete everything in Recently deleted?")
    static let trashEmptyConfirmBody = localizedCopy("trash_empty_confirm_body", "This permanently deletes every item here. This cannot be undone.")
    static let trashEmptyStateTitle = localizedCopy("trash_empty_state_title", "Nothing here")
    static let trashEmptyStateBody = localizedCopy("trash_empty_state_body", "Deleted items appear here for 30 days.")
    static let trashSectionAlbums = localizedCopy("trash_section_albums", "Albums")
    static let trashSectionPhotos = localizedCopy("trash_section_photos", "Photos")
    static let trashSectionNotes = localizedCopy("trash_section_notes", "Notes")
    static let trashSectionContacts = localizedCopy("trash_section_contacts", "Contacts")

    /// "N days left"
    static func trashDaysLeft(_ days: Int) -> String {
        localizedCopy("trash_days_left", "\(days) days left")
    }

    /// "N photos" — album row in the trash.
    static func trashPhotoCount(_ count: Int) -> String {
        localizedCopy("trash_photo_count", "\(count) photos")
    }

    // MARK: - Sort (P4)

    static let sortTitle = localizedCopy("sort_title", "Sort by")
    static let sortAlbumManual = localizedCopy("sort_album_manual", "Manual")
    static let sortName = localizedCopy("sort_name", "Name")
    static let sortDateCreated = localizedCopy("sort_date_created", "Date created")
    static let sortPhotoCount = localizedCopy("sort_photo_count", "Photo count")
    static let sortDateModified = localizedCopy("sort_date_modified", "Date modified")
    static let sortNoteTitle = localizedCopy("sort_note_title", "Title")

    // MARK: - Settings (P5)

    static let settingsTitle = localizedCopy("settings_title", "Settings")
    static let settingsSectionSecurity = localizedCopy("settings_section_security", "Security")
    static let settingsSectionData = localizedCopy("settings_section_data", "Data")
    static let settingsSectionAbout = localizedCopy("settings_section_about", "About")
    static let settingsLockNow = localizedCopy("settings_lock_now", "Lock now")
    static let settingsVersion = localizedCopy("settings_version", "Version")
    static let settingsHowItWorks = localizedCopy("settings_how_it_works", "How it works")
    static let settingsHowItWorksSubtitle = localizedCopy("settings_how_it_works_subtitle", "Revisit the guide")
    static let settingsPrivacyTitle = localizedCopy("settings_privacy_title", "Privacy")
    static let settingsPrivacySubtitle = localizedCopy("settings_privacy_subtitle", "All data stays on this device.")
    static let settingsPrivacyBody = localizedCopy("settings_privacy_body", "All data stays on this device. This app has no servers and sends nothing anywhere — no accounts, no analytics, no cloud sync.")
    /// Present for ID parity with Android; the licenses row is Android-only (decisions §5).
    static let settingsLicenses = localizedCopy("settings_licenses", "Open-source licenses")

    // MARK: - Source link + update check (decisions §13)

    static let settingsSourceCode = localizedCopy("settings_source_code", "Source code")
    static let settingsSourceCodeSubtitle = localizedCopy("settings_source_code_subtitle", "View this app on GitHub")
    static let settingsCheckUpdates = localizedCopy("settings_check_updates", "Check for updates")
    static let settingsUpdateChecking = localizedCopy("settings_update_checking", "Checking…")
    static let settingsUpdateUpToDate = localizedCopy("settings_update_up_to_date", "Up to date")
    static let settingsUpdateFailed = localizedCopy("settings_update_failed", "Couldn't check for updates")

    /// "Version X.Y.Z available" — the subtitle once a newer version is found.
    static func settingsUpdateAvailable(_ version: String) -> String {
        localizedCopy("settings_update_available", "Version \(version) available")
    }

    // MARK: - Onboarding revisit (P5)

    static let onboardingDone = localizedCopy("onboarding_done", "Done")

    // MARK: - Disguise carousel, picker and switch flow (iteration 3, §7)

    /// §9a, iOS wording. Deliberately differs from Android's, which also
    /// renames the app — an accepted exception to §7's identical-strings rule,
    /// because the platforms genuinely do different things.
    static let disguiseIdentityDisclosure = localizedCopy("disguise_identity_disclosure", "Your home screen icon changes to match the disguise. The app's name stays \"Calculator+\" — iOS does not let an app rename itself — and iOS shows a brief system alert when the icon changes.")
    static let disguiseCurrentBadge = localizedCopy("disguise_current_badge", "Current")
    static let onboardingDisguiseTitle = localizedCopy("onboarding_disguise_title", "Pick a disguise")
    static let onboardingDisguiseBody = localizedCopy("onboarding_disguise_body", "Anyone who opens Calculator+ sees only this screen. You can change it later in Settings.")
    static let onboardingDisguiseRevisitHint = localizedCopy("onboarding_disguise_revisit_hint", "This is your current disguise. You can change it in Settings → Change disguise.")
    static let settingsChangeDisguiseTitle = localizedCopy("settings_change_disguise_title", "Change disguise")
    static let disguisePickAction = localizedCopy("disguise_pick_action", "Use this disguise")
    static let disguiseSwitchSuccessTitle = localizedCopy("disguise_switch_success_title", "Disguise changed")
    static let disguiseSwitchSuccessBody = localizedCopy("disguise_switch_success_body", "Your new code works from now on. There is no way to recover it — if you forget it, your vault contents cannot be retrieved.")

    /// The per-card cover-identity line (§9a), parameterized by the face's
    /// cover identity name. iOS wording: the icon only — `setAlternateIconName`
    /// cannot rename an app.
    static func disguiseCoverIdentity(_ coverName: String) -> String {
        localizedCopy("disguise_cover_identity", "Uses the \(coverName) icon on your home screen")
    }

    /// The picker explainer, parameterized by the CURRENT face's display name
    /// and commit gesture.
    static func disguiseSwitchExplainer(currentName: String, currentGesture: String) -> String {
        localizedCopy(
            "disguise_switch_explainer",
            "Your current code belongs to the \(currentName) disguise and is confirmed with \(currentGesture). The new disguise needs a new code, set on its own keys. Your photos, notes, and contacts are unchanged."
        )
    }

    // MARK: - Global search (N1)

    static let searchTitle = localizedCopy("search_title", "Search")
    static let searchPlaceholder = localizedCopy("search_placeholder", "Notes, contacts, albums")
    static let searchNoQueryTitle = localizedCopy("search_no_query_title", "Search your vault")
    static let searchNoQueryBody = localizedCopy("search_no_query_body", "Find notes, contacts, and albums by name or content.")
    static let searchSectionAlbums = localizedCopy("search_section_albums", "Albums")
    static let searchSectionNotes = localizedCopy("search_section_notes", "Notes")
    static let searchSectionContacts = localizedCopy("search_section_contacts", "Contacts")

    // MARK: - Photo info (N2, N3)

    static let photoInfoTitle = localizedCopy("photo_info_title", "Details")
    static let photoInfoDimensions = localizedCopy("photo_info_dimensions", "Dimensions")
    static let photoInfoSize = localizedCopy("photo_info_size", "File size")
    static let photoInfoType = localizedCopy("photo_info_type", "Type")
    static let photoInfoImported = localizedCopy("photo_info_imported", "Imported")
    static let photoInfoDuration = localizedCopy("photo_info_duration", "Duration")

    // MARK: - Import (N3)

    static let videoImportFailed = localizedCopy("video_import_failed", "Some videos could not be imported.")

    /// "Importing done/total…" — the import progress pill.
    static func importProgress(done: Int, total: Int) -> String {
        localizedCopy("import_progress", "Importing \(done)/\(total)…")
    }
}
