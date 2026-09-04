import Foundation
import SwiftData
import Testing
import UIKit
@testable import SafeBox

/// Erase everything must hard-clear the trash too (decisions §3): every row of
/// every table including soft-deleted ones, and every file including those
/// still owned by trashed photos.
@MainActor
struct VaultNukerTests {
    private func makePNGData() -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 12), format: format)
        let image = renderer.image { ctx in
            UIColor.systemPink.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 20, height: 12))
        }
        return image.pngData()!
    }

    @Test func nukeHardClearsTrashedRowsAndTheirFiles() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxNukeTests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let suiteName = "test.nuke.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let container = ModelContainerFactory.inMemory()
        let fileStore = PhotoFileStore(rootURL: root)
        let photos = SwiftDataPhotoRepository(container: container, fileStore: fileStore)
        let notes = SwiftDataNoteRepository(container: container)
        let contacts = SwiftDataContactRepository(container: container)

        // Live + trashed content of every kind.
        let live = try photos.createAlbum(name: "Live")
        let livePhoto = try photos.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: live.id)!
        let trashedPhoto = try photos.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: live.id)!
        let trashedAlbum = try photos.createAlbum(name: "Gone")
        let underAlbum = try photos.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: trashedAlbum.id)!
        try photos.deletePhotos([trashedPhoto])
        try photos.deleteAlbum(trashedAlbum)
        _ = try notes.createNote(body: "live")
        let trashedNote = try notes.createNote(body: "gone")
        let tag = try notes.findOrCreateTag(named: "t")
        try notes.setTags([tag], on: trashedNote)
        try notes.delete(note: trashedNote)
        let trashedContact = Contact(givenName: "Gone")
        try contacts.insert(Contact(givenName: "Live"))
        try contacts.insert(trashedContact)
        try contacts.delete(trashedContact)
        let allFiles = [livePhoto, trashedPhoto, underAlbum].flatMap {
            [fileStore.photoURL(fileName: $0.fileName), fileStore.thumbnailURL(thumbFileName: $0.thumbFileName)]
        }
        for url in allFiles {
            #expect(FileManager.default.fileExists(atPath: url.path))
        }

        let passcodeStore = InMemoryPasscodeStore()
        try await passcodeStore.set(tokens: ["D1", "D2", "D3", "D4"],
                                    alphabet: CalculatorDisguise().alphabet,
                                    activeDisguiseId: "calculator")
        let coordinator = AppLockCoordinator(passcodeStore: passcodeStore)
        OnboardingSentinel.setComplete(defaults: defaults)
        SortPreferences.setAlbumSort(.photoCount, defaults: defaults)
        SortPreferences.setNoteSort(.title, defaults: defaults)
        let nuker = VaultNuker(modelContainer: container, fileStore: fileStore,
                               passcodeStore: passcodeStore, lockCoordinator: coordinator,
                               preferenceDefaults: defaults)

        await nuker.nuke()

        // Every table empty — trashed rows included — checked on a fresh context.
        let context = ModelContext(container)
        #expect(try context.fetch(FetchDescriptor<Photo>()).isEmpty)
        #expect(try context.fetch(FetchDescriptor<Album>()).isEmpty)
        #expect(try context.fetch(FetchDescriptor<Note>()).isEmpty)
        #expect(try context.fetch(FetchDescriptor<SafeBox.Tag>()).isEmpty) // `Tag` also exists in Testing
        #expect(try context.fetch(FetchDescriptor<Contact>()).isEmpty)
        #expect(photos.trashedAlbums().isEmpty)
        #expect(photos.trashedPhotos().isEmpty)
        #expect(notes.trashedNotes().isEmpty)
        #expect(contacts.trashedContacts().isEmpty)
        // Every file gone, trashed photos' files included.
        for url in allFiles {
            #expect(!FileManager.default.fileExists(atPath: url.path))
        }
        #expect(!FileManager.default.fileExists(atPath: fileStore.photosURL.path))
        #expect(!FileManager.default.fileExists(atPath: fileStore.thumbnailsURL.path))
        // Passcode, onboarding flag, sort preferences and lock state reset to
        // just-installed (decisions §4: an erase returns every stored choice).
        #expect(!passcodeStore.hasPasscode)
        #expect(!OnboardingSentinel.isComplete(defaults: defaults))
        #expect(defaults.string(forKey: SortPreferences.albumSortKey) == nil)
        #expect(defaults.string(forKey: SortPreferences.noteSortKey) == nil)
        #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
        #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
    }
}
