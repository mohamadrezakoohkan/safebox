import Foundation
import Testing
import UIKit
@testable import SafeBox

@MainActor
struct TrashRepositoryTests {
    private struct Stack {
        let photos: SwiftDataPhotoRepository
        let notes: SwiftDataNoteRepository
        let contacts: SwiftDataContactRepository
        let trash: SwiftDataTrashRepository
        let fileStore: PhotoFileStore
        let root: URL
    }

    private func makeStack() -> Stack {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxTrashTests-\(UUID().uuidString)", isDirectory: true)
        let container = ModelContainerFactory.inMemory()
        let fileStore = PhotoFileStore(rootURL: root)
        let photos = SwiftDataPhotoRepository(container: container, fileStore: fileStore)
        let notes = SwiftDataNoteRepository(container: container)
        let contacts = SwiftDataContactRepository(container: container)
        let trash = SwiftDataTrashRepository(photoRepository: photos, noteRepository: notes,
                                             contactRepository: contacts)
        return Stack(photos: photos, notes: notes, contacts: contacts, trash: trash,
                     fileStore: fileStore, root: root)
    }

    private func makePNGData() -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 12), format: format)
        let image = renderer.image { ctx in
            UIColor.systemTeal.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 20, height: 12))
        }
        return image.pngData()!
    }

    private func insertPhoto(_ stack: Stack, into album: Album) async throws -> Photo {
        try stack.photos.insertPhoto(try await stack.fileStore.store(data: makePNGData()), albumId: album.id)!
    }

    private func filesExist(_ photo: Photo, _ fileStore: PhotoFileStore) -> Bool {
        FileManager.default.fileExists(atPath: fileStore.photoURL(fileName: photo.fileName).path)
            && FileManager.default.fileExists(atPath: fileStore.thumbnailURL(thumbFileName: photo.thumbFileName).path)
    }

    private let thirtyOneDaysAgo = Date.now.addingTimeInterval(-31 * 86_400)

    // MARK: - Policy

    @Test func retentionIsThirtyDays() {
        #expect(TrashPolicy.retentionDays == 30)
        #expect(TrashPolicy.retention == 30 * 86_400)
    }

    @Test func daysLeftRoundsUpAndClampsAtZero() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        #expect(TrashPolicy.daysLeft(deletedAt: now, now: now) == 30)
        #expect(TrashPolicy.daysLeft(deletedAt: now.addingTimeInterval(-29.5 * 86_400), now: now) == 1)
        #expect(TrashPolicy.daysLeft(deletedAt: now.addingTimeInterval(-30 * 86_400), now: now) == 0)
        #expect(TrashPolicy.daysLeft(deletedAt: now.addingTimeInterval(-45 * 86_400), now: now) == 0)
    }

    @Test func expiryIsInclusiveAtExactlyThirtyDays() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        #expect(!TrashPolicy.isExpired(deletedAt: now, now: now))
        #expect(!TrashPolicy.isExpired(deletedAt: now.addingTimeInterval(-30 * 86_400 + 1), now: now))
        #expect(TrashPolicy.isExpired(deletedAt: now.addingTimeInterval(-30 * 86_400), now: now))
        #expect(TrashPolicy.isExpired(deletedAt: now.addingTimeInterval(-31 * 86_400), now: now))
    }

    // MARK: - Contents

    @Test func contentsGroupByTypeAndHidePhotosUnderTrashedAlbums() async throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let live = try stack.photos.createAlbum(name: "Live")
        let single = try await insertPhoto(stack, into: live)
        _ = try await insertPhoto(stack, into: live)
        let trashedAlbum = try stack.photos.createAlbum(name: "Gone")
        _ = try await insertPhoto(stack, into: trashedAlbum)
        _ = try await insertPhoto(stack, into: trashedAlbum)
        let note = try stack.notes.createNote(body: "bye")
        let liveNote = try stack.notes.createNote(body: "stay")
        let contact = Contact(givenName: "Trashed")
        try stack.contacts.insert(contact)
        try stack.contacts.insert(Contact(givenName: "Kept"))

        #expect(stack.trash.contents().isEmpty)

        try stack.photos.deletePhotos([single])
        try stack.photos.deleteAlbum(trashedAlbum)
        try stack.notes.delete(note: note)
        try stack.contacts.delete(contact)

        let contents = stack.trash.contents()
        #expect(!contents.isEmpty)
        #expect(contents.albums.map(\.id) == [trashedAlbum.id])
        #expect(contents.photos.map(\.id) == [single.id])      // album's photos are NOT listed individually
        #expect(contents.notes.map(\.id) == [note.id])
        #expect(contents.contacts.map(\.id) == [contact.id])
        // Live rows are untouched.
        #expect(stack.photos.albums().map(\.id) == [live.id])
        #expect(stack.notes.notes().map(\.id) == [liveNote.id])
        #expect(stack.contacts.contacts().map(\.displayName) == ["Kept"])
    }

    @Test func contentsListMostRecentlyDeletedFirst() throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let older = try stack.notes.createNote(body: "older")
        let newer = try stack.notes.createNote(body: "newer")
        try stack.notes.delete(notes: [older, newer])
        older.deletedAt = Date.now.addingTimeInterval(-3_600)
        #expect(stack.trash.contents().notes.map(\.id) == [newer.id, older.id])
    }

    // MARK: - Restore / purge

    @Test func restoreRoutesEachKindToItsRepository() async throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let album = try stack.photos.createAlbum(name: "A")
        let photo = try await insertPhoto(stack, into: album)
        let otherAlbum = try stack.photos.createAlbum(name: "B")
        let inOther = try await insertPhoto(stack, into: otherAlbum)
        let note = try stack.notes.createNote(body: "n")
        let contact = Contact(givenName: "C")
        try stack.contacts.insert(contact)

        try stack.photos.deletePhotos([photo])
        try stack.photos.deleteAlbum(otherAlbum)
        try stack.notes.delete(note: note)
        try stack.contacts.delete(contact)

        try stack.trash.restore([
            TrashItemID(kind: .photo, id: photo.id),
            TrashItemID(kind: .album, id: otherAlbum.id),
            TrashItemID(kind: .note, id: note.id),
            TrashItemID(kind: .contact, id: contact.id),
        ])

        #expect(stack.trash.contents().isEmpty)
        #expect(stack.photos.photos(in: album).map(\.id) == [photo.id])
        #expect(stack.photos.photos(in: otherAlbum).map(\.id) == [inOther.id])
        #expect(stack.notes.notes().map(\.id) == [note.id])
        #expect(stack.contacts.contacts().map(\.id) == [contact.id])
    }

    @Test func purgeDeletesRowsAndBothPhotoFiles() async throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let album = try stack.photos.createAlbum(name: "A")
        let photo = try await insertPhoto(stack, into: album)
        let kept = try await insertPhoto(stack, into: album)
        let doomedAlbum = try stack.photos.createAlbum(name: "B")
        let inDoomed = try await insertPhoto(stack, into: doomedAlbum)
        let note = try stack.notes.createNote(body: "n")
        let contact = Contact(givenName: "C")
        try stack.contacts.insert(contact)

        try stack.photos.deletePhotos([photo, kept])
        try stack.photos.deleteAlbum(doomedAlbum)
        try stack.notes.delete(note: note)
        try stack.contacts.delete(contact)

        try await stack.trash.purge([
            TrashItemID(kind: .photo, id: photo.id),
            TrashItemID(kind: .album, id: doomedAlbum.id),
            TrashItemID(kind: .note, id: note.id),
            TrashItemID(kind: .contact, id: contact.id),
        ])

        let contents = stack.trash.contents()
        #expect(contents.photos.map(\.id) == [kept.id])        // untouched trash item survives
        #expect(contents.albums.isEmpty)
        #expect(contents.notes.isEmpty)
        #expect(contents.contacts.isEmpty)
        #expect(!filesExist(photo, stack.fileStore))
        #expect(!filesExist(inDoomed, stack.fileStore))
        #expect(filesExist(kept, stack.fileStore))
        #expect(stack.photos.trashedPhotos().map(\.id) == [kept.id])
    }

    @Test func emptyAllPurgesEverythingIncludingAlbumPhotos() async throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let live = try stack.photos.createAlbum(name: "Live")
        let livePhoto = try await insertPhoto(stack, into: live)
        let trashedPhoto = try await insertPhoto(stack, into: live)
        let trashedAlbum = try stack.photos.createAlbum(name: "Gone")
        let underAlbum = try await insertPhoto(stack, into: trashedAlbum)
        let note = try stack.notes.createNote(body: "n")
        let contact = Contact(givenName: "C")
        try stack.contacts.insert(contact)

        try stack.photos.deletePhotos([trashedPhoto])
        try stack.photos.deleteAlbum(trashedAlbum)
        try stack.notes.delete(note: note)
        try stack.contacts.delete(contact)

        try await stack.trash.emptyAll()

        #expect(stack.trash.contents().isEmpty)
        #expect(stack.photos.trashedAlbums().isEmpty)
        #expect(stack.photos.trashedPhotos().isEmpty)
        #expect(stack.notes.trashedNotes().isEmpty)
        #expect(stack.contacts.trashedContacts().isEmpty)
        #expect(!filesExist(trashedPhoto, stack.fileStore))
        #expect(!filesExist(underAlbum, stack.fileStore))
        // Live content is untouched.
        #expect(filesExist(livePhoto, stack.fileStore))
        #expect(stack.photos.photos(in: live).map(\.id) == [livePhoto.id])
    }

    @Test func purgeExpiredRemovesExpiredAcrossTypesAndKeepsFreshOnes() async throws {
        let stack = makeStack()
        defer { try? FileManager.default.removeItem(at: stack.root) }
        let album = try stack.photos.createAlbum(name: "A")
        let expiredPhoto = try await insertPhoto(stack, into: album)
        let freshPhoto = try await insertPhoto(stack, into: album)
        let expiredNote = try stack.notes.createNote(body: "old")
        let freshNote = try stack.notes.createNote(body: "new")
        let expiredContact = Contact(givenName: "Old")
        let freshContact = Contact(givenName: "New")
        try stack.contacts.insert(expiredContact)
        try stack.contacts.insert(freshContact)

        try stack.photos.deletePhotos([expiredPhoto, freshPhoto])
        try stack.notes.delete(notes: [expiredNote, freshNote])
        try stack.contacts.delete(contacts: [expiredContact, freshContact])
        expiredPhoto.deletedAt = thirtyOneDaysAgo
        expiredNote.deletedAt = thirtyOneDaysAgo
        expiredContact.deletedAt = thirtyOneDaysAgo

        // What MainTabView runs on every unlock (and AppContainer at launch).
        await stack.trash.purgeExpired(now: .now)

        let contents = stack.trash.contents()
        #expect(contents.photos.map(\.id) == [freshPhoto.id])
        #expect(contents.notes.map(\.id) == [freshNote.id])
        #expect(contents.contacts.map(\.id) == [freshContact.id])
        #expect(!filesExist(expiredPhoto, stack.fileStore))
        #expect(filesExist(freshPhoto, stack.fileStore))
    }

    // MARK: - Empty state preset

    @Test func trashEmptyStateHasNoAction() {
        #expect(EmptyStateContent.trashEmpty.title == VaultCopy.trashEmptyStateTitle)
        #expect(EmptyStateContent.trashEmpty.description == VaultCopy.trashEmptyStateBody)
        #expect(EmptyStateContent.trashEmpty.actionTitle == nil)
        #expect(EmptyStateContent.vaultPresets.contains(.trashEmpty))
    }
}
