import Foundation
import Testing
@testable import SafeBox

/// Spot-checks the iteration-2 shared string table (decisions §10): a few
/// `VaultCopy` entries resolve to their English source, integer arguments are
/// substituted, and the keys really are in the compiled string catalog rather
/// than only falling back to the in-code default.
struct VaultCopyTests {
    // MARK: - Plain keys

    @Test func plainKeysResolveToEnglish() {
        #expect(VaultCopy.emptyAlbumsTitle == "No albums yet")
        #expect(VaultCopy.trashTitle == "Recently deleted")
        #expect(VaultCopy.onboardingDone == "Done")
        #expect(VaultCopy.emptyPhotosBody == "Imports are copies — the originals stay in your library.")
    }

    // MARK: - Keys with arguments

    @Test func integerArgumentsAreSubstituted() {
        #expect(VaultCopy.selectionCount(3) == "3 selected")
        #expect(VaultCopy.confirmDeleteAlbum(photoCount: 12) == "Delete album and its 12 photos?")
        #expect(VaultCopy.deletedNotes(0) == "0 notes deleted")
        #expect(VaultCopy.trashDaysLeft(30) == "30 days left")
        #expect(VaultCopy.importProgress(done: 2, total: 5) == "Importing 2/5…")
        #expect(VaultCopy.settingsUpdateAvailable("1.2.3") == "Version 1.2.3 available")
    }

    // MARK: - Catalog presence

    @Test func keysArePresentInCompiledCatalog() {
        // A sentinel default makes a missing catalog entry visible; the
        // in-code English fallback would otherwise mask it.
        let missing = "<missing>"
        let bundle = Bundle.main
        #expect(bundle.localizedString(forKey: "empty_albums_title", value: missing, table: nil) == "No albums yet")
        #expect(bundle.localizedString(forKey: "selection_count", value: missing, table: nil) == "%lld selected")
        #expect(bundle.localizedString(forKey: "import_progress", value: missing, table: nil) == "Importing %1$lld/%2$lld…")
        // Decisions §13: the string-argument key uses %@, and the privacy body
        // no longer claims the app "sends nothing anywhere".
        #expect(bundle.localizedString(forKey: "settings_update_available", value: missing, table: nil) == "Version %@ available")
        #expect(bundle.localizedString(forKey: "settings_source_code", value: missing, table: nil) == "Source code")
        #expect(bundle.localizedString(forKey: "settings_privacy_body", value: missing, table: nil)
            == "All data stays on this device — no accounts, no analytics, no cloud sync. The only time this app connects to the internet is when you tap Check for updates.")
        // An existing iteration-1 key still resolves, proving the merge kept it.
        #expect(bundle.localizedString(forKey: "settings_change_title", value: missing, table: nil) == "Change passcode")
    }
}
