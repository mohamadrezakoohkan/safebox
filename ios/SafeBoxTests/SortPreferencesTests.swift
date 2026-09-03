import Foundation
import Testing
@testable import SafeBox

/// The persisted sort preferences (decisions §4): raw values, defaults,
/// corrupt-value fallback, and survival across a relaunch / a lock.
struct SortPreferencesTests {
    /// A throwaway defaults suite; the SAME name is used to remove it, so
    /// nothing leaks into the simulator's preferences.
    private func withDefaults(_ body: (UserDefaults) throws -> Void) rethrows {
        let suiteName = "test.sort.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        try body(defaults)
    }

    // MARK: - Raw values (shared with Android — never change these)

    @Test func albumRawValuesAreTheSharedSnakeCaseStrings() {
        #expect(AlbumSort.manual.rawValue == "manual")
        #expect(AlbumSort.name.rawValue == "name")
        #expect(AlbumSort.dateCreated.rawValue == "date_created")
        #expect(AlbumSort.photoCount.rawValue == "photo_count")
        #expect(AlbumSort.allCases.map(\.rawValue) == ["manual", "name", "date_created", "photo_count"])
    }

    @Test func noteRawValuesAreTheSharedSnakeCaseStrings() {
        #expect(NoteSort.dateModified.rawValue == "date_modified")
        #expect(NoteSort.dateCreated.rawValue == "date_created")
        #expect(NoteSort.title.rawValue == "title")
        #expect(NoteSort.allCases.map(\.rawValue) == ["date_modified", "date_created", "title"])
    }

    @Test func defaultsAreManualAndDateModified() {
        withDefaults { defaults in
            #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
            #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
        }
    }

    @Test func keysAreTheAgreedOnes() {
        #expect(SortPreferences.albumSortKey == "albumSort.v1")
        #expect(SortPreferences.noteSortKey == "noteSort.v1")
    }

    // MARK: - Round-trip

    @Test func everyModeRoundTripsThroughTheStore() {
        withDefaults { defaults in
            for mode in AlbumSort.allCases {
                SortPreferences.setAlbumSort(mode, defaults: defaults)
                #expect(SortPreferences.albumSort(defaults: defaults) == mode)
                #expect(defaults.string(forKey: SortPreferences.albumSortKey) == mode.rawValue)
            }
            for mode in NoteSort.allCases {
                SortPreferences.setNoteSort(mode, defaults: defaults)
                #expect(SortPreferences.noteSort(defaults: defaults) == mode)
                #expect(defaults.string(forKey: SortPreferences.noteSortKey) == mode.rawValue)
            }
        }
    }

    @Test func thePreferenceSurvivesAFreshStoreInstance() {
        // Relaunch (and lock): a brand-new UserDefaults object over the same
        // suite reads back what the previous one wrote. Nothing about the
        // choice lives in a view model.
        let suiteName = "test.sort.\(UUID().uuidString)"
        let writing = UserDefaults(suiteName: suiteName)!
        defer { writing.removePersistentDomain(forName: suiteName) }

        SortPreferences.setAlbumSort(.photoCount, defaults: writing)
        SortPreferences.setNoteSort(.title, defaults: writing)

        let reading = UserDefaults(suiteName: suiteName)!
        #expect(SortPreferences.albumSort(defaults: reading) == .photoCount)
        #expect(SortPreferences.noteSort(defaults: reading) == .title)
    }

    // MARK: - Corrupt values

    @Test func anUnknownStoredValueFallsBackToTheDefault() {
        withDefaults { defaults in
            defaults.set("largest_first", forKey: SortPreferences.albumSortKey)
            defaults.set("alphabetical", forKey: SortPreferences.noteSortKey)
            #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
            #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
        }
    }

    @Test func aNonStringStoredValueFallsBackToTheDefault() {
        withDefaults { defaults in
            // e.g. a future version storing an index instead of a raw value.
            defaults.set(2, forKey: SortPreferences.albumSortKey)
            defaults.set(true, forKey: SortPreferences.noteSortKey)
            #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
            #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
        }
    }

    @Test func resetReturnsBothToTheDefaults() {
        withDefaults { defaults in
            SortPreferences.setAlbumSort(.name, defaults: defaults)
            SortPreferences.setNoteSort(.dateCreated, defaults: defaults)
            SortPreferences.reset(defaults: defaults)
            #expect(defaults.string(forKey: SortPreferences.albumSortKey) == nil)
            #expect(defaults.string(forKey: SortPreferences.noteSortKey) == nil)
            #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
            #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
        }
    }

    // MARK: - Labels

    @Test func everyModeCarriesItsSharedLabel() {
        #expect(AlbumSort.manual.label == VaultCopy.sortAlbumManual)
        #expect(AlbumSort.name.label == VaultCopy.sortName)
        #expect(AlbumSort.dateCreated.label == VaultCopy.sortDateCreated)
        #expect(AlbumSort.photoCount.label == VaultCopy.sortPhotoCount)
        #expect(NoteSort.dateModified.label == VaultCopy.sortDateModified)
        #expect(NoteSort.dateCreated.label == VaultCopy.sortDateCreated)
        #expect(NoteSort.title.label == VaultCopy.sortNoteTitle)
        #expect(!AlbumSort.allCases.contains { $0.label.isEmpty })
        #expect(!NoteSort.allCases.contains { $0.label.isEmpty })
    }
}
