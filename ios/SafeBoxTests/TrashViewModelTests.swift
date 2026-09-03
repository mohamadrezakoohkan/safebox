import Foundation
import Testing
@testable import SafeBox

/// P3 review follow-ups: the trash view model prunes its `contents` BEFORE it
/// awaits the repository (so no deleted-and-saved `@Model` is read during a
/// body re-evaluation), and the album row count is the same-stamp set that
/// Restore actually brings back.
@MainActor
struct TrashViewModelTests {
    /// Fake whose `purge` / `emptyAll` suspend until the test lets them finish,
    /// so the state between "call started" and "call returned" is observable.
    @MainActor
    private final class GatedTrashRepository: TrashRepository {
        var contentsToReturn = TrashContents()
        var purgeCalls: [[TrashItemID]] = []
        var emptyAllCalls = 0
        private var gate: CheckedContinuation<Void, Never>?

        func contents() -> TrashContents { contentsToReturn }
        func restore(_ items: [TrashItemID]) throws {}

        func purge(_ items: [TrashItemID]) async throws {
            purgeCalls.append(items)
            await withCheckedContinuation { gate = $0 }
        }

        func emptyAll() async throws {
            emptyAllCalls += 1
            await withCheckedContinuation { gate = $0 }
        }

        func purgeExpired(now: Date) async {}

        func open() {
            gate?.resume()
            gate = nil
        }
    }

    private func makeTrashedNote(_ body: String) -> Note {
        let note = Note(body: body)
        note.deletedAt = .now
        return note
    }

    // MARK: - Prune before await

    @Test func purgeRemovesTheRowFromContentsBeforeTheRepositoryReturns() async throws {
        let repo = GatedTrashRepository()
        let doomed = makeTrashedNote("doomed")
        let kept = makeTrashedNote("kept")
        repo.contentsToReturn = TrashContents(notes: [doomed, kept])
        let viewModel = TrashViewModel(repository: repo)
        viewModel.reload()
        #expect(viewModel.contents.notes.count == 2)

        let item = TrashItemID(kind: .note, id: doomed.id)
        let purge = Task { await viewModel.purge(item) }
        while repo.purgeCalls.isEmpty { await Task.yield() }

        // The repository has been entered but has not returned: the row is
        // already gone from the view model's copy.
        #expect(viewModel.contents.notes.map(\.id) == [kept.id])
        #expect(repo.purgeCalls == [[item]])

        repo.contentsToReturn = TrashContents(notes: [kept])
        repo.open()
        await purge.value
        #expect(viewModel.contents.notes.map(\.id) == [kept.id])
    }

    @Test func emptyAllClearsContentsBeforeTheRepositoryReturns() async throws {
        let repo = GatedTrashRepository()
        repo.contentsToReturn = TrashContents(notes: [makeTrashedNote("a"), makeTrashedNote("b")])
        let viewModel = TrashViewModel(repository: repo)
        viewModel.reload()
        #expect(!viewModel.contents.isEmpty)

        let empty = Task { await viewModel.emptyAll() }
        while repo.emptyAllCalls == 0 { await Task.yield() }
        #expect(viewModel.contents.isEmpty)

        repo.contentsToReturn = TrashContents()
        repo.open()
        await empty.value
        #expect(viewModel.contents.isEmpty)
    }

    @Test func removeDropsOnlyTheAddressedRow() {
        let a = makeTrashedNote("a")
        let b = makeTrashedNote("b")
        let contact = Contact(givenName: "C")
        var contents = TrashContents(notes: [a, b], contacts: [contact])

        contents.remove(TrashItemID(kind: .note, id: a.id))
        #expect(contents.notes.map(\.id) == [b.id])
        #expect(contents.contacts.map(\.id) == [contact.id])

        contents.remove(TrashItemID(kind: .contact, id: a.id)) // wrong kind: no-op
        #expect(contents.contacts.map(\.id) == [contact.id])
    }

    // MARK: - Album row count = what Restore brings back

    @Test func trashedPhotoCountCountsOnlySameStampPhotos() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxTrashVMTests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let container = ModelContainerFactory.inMemory()
        let fileStore = PhotoFileStore(rootURL: root)
        let photos = SwiftDataPhotoRepository(container: container, fileStore: fileStore)
        let notes = SwiftDataNoteRepository(container: container)
        let contacts = SwiftDataContactRepository(container: container)
        let trash = SwiftDataTrashRepository(photoRepository: photos, noteRepository: notes,
                                             contactRepository: contacts)

        let album = try photos.createAlbum(name: "Trip")
        let data = TestPNG.data()
        let earlier = try photos.insertPhoto(try await fileStore.store(data: data), albumId: album.id)!
        let withAlbum1 = try photos.insertPhoto(try await fileStore.store(data: data), albumId: album.id)!
        let withAlbum2 = try photos.insertPhoto(try await fileStore.store(data: data), albumId: album.id)!
        try photos.deletePhotos([earlier])          // trashed individually first
        try photos.deleteAlbum(album)               // stamps the two live photos

        let viewModel = TrashViewModel(repository: trash)
        viewModel.reload()
        #expect(viewModel.trashedPhotoCount(in: album) == 2)

        // Restore brings back exactly that set.
        try trash.restore([TrashItemID(kind: .album, id: album.id)])
        #expect(Set(photos.photos(in: album).map(\.id)) == [withAlbum1.id, withAlbum2.id])
        #expect(photos.trashedPhotos().map(\.id) == [earlier.id])
        // A live album has nothing "under" it in the trash.
        #expect(viewModel.trashedPhotoCount(in: album) == 0)
    }
}
