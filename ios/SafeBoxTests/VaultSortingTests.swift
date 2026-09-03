import Foundation
import Testing
import UIKit
@testable import SafeBox

/// Every sort mode's order, INCLUDING the tie-breakers (decisions §4):
///   albums: name → createdAt asc → id · date_created → id · photo_count →
///           name → id · manual → sortIndex → id
///   notes:  title → updatedAt desc → id (empty titles last) ·
///           date_modified / date_created → id
@MainActor
struct VaultSortingTests {
    /// Ascending, comparable ids so every "→ id" tie-break is predictable.
    private func uuid(_ n: Int) -> UUID {
        UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", n))!
    }

    private let epoch = Date(timeIntervalSince1970: 1_700_000_000)

    private func date(_ offset: TimeInterval) -> Date { epoch.addingTimeInterval(offset) }

    private func album(_ n: Int, _ name: String, created: TimeInterval = 0, index: Int = 0) -> Album {
        Album(id: uuid(n), name: name, createdAt: date(created), sortIndex: index)
    }

    private func note(_ n: Int, _ body: String, created: TimeInterval = 0,
                      updated: TimeInterval = 0) -> Note {
        Note(id: uuid(n), body: body, createdAt: date(created), updatedAt: date(updated))
    }

    private func order(_ albums: [Album], _ mode: AlbumSort,
                       counts: [UUID: Int] = [:]) -> [String] {
        VaultSorting.sorted(albums, by: mode) { counts[$0.id] ?? 0 }.map(\.name)
    }

    private func order(_ notes: [Note], _ mode: NoteSort) -> [UUID] {
        VaultSorting.sorted(notes, by: mode).map(\.id)
    }

    // MARK: - Albums: manual (the default)

    @Test func manualFollowsSortIndexAndBreaksTiesById() {
        let a = album(3, "third", index: 2)
        let b = album(1, "first", index: 0)
        let c = album(2, "second", index: 1)
        #expect(order([a, b, c], .manual) == ["first", "second", "third"])

        // Two albums that somehow share an index stay in a stable id order.
        let x = album(9, "x", index: 5)
        let y = album(4, "y", index: 5)
        #expect(order([x, y], .manual) == ["y", "x"])
    }

    // MARK: - Albums: name

    @Test func nameIsAToZCaseAndDiacriticInsensitive() {
        let albums = [album(1, "zebra"), album(2, "Éclair"), album(3, "apple"), album(4, "Banana")]
        #expect(order(albums, .name) == ["apple", "Banana", "Éclair", "zebra"])
    }

    @Test func nameTiesBreakByCreatedAtAscendingThenId() {
        // Same folded name ("cafe"), different creation instants → oldest first.
        let newer = album(1, "Café", created: 200)
        let older = album(2, "cafe", created: 100)
        #expect(order([newer, older], .name) == ["cafe", "Café"])

        // Same folded name AND the same instant → id decides.
        let high = album(8, "CAFE", created: 100)
        let low = album(5, "cafè", created: 100)
        #expect(VaultSorting.sorted([high, low], by: .name) { _ in 0 }.map(\.id) == [uuid(5), uuid(8)])
    }

    @Test func nameIgnoresSurroundingWhitespace() {
        let albums = [album(1, "  beta"), album(2, "alpha  ")]
        #expect(order(albums, .name) == ["alpha  ", "  beta"])
    }

    // MARK: - Albums: date created

    @Test func dateCreatedIsNewestFirstAndBreaksTiesById() {
        let albums = [album(1, "old", created: 10), album(2, "new", created: 30),
                      album(3, "mid", created: 20)]
        #expect(order(albums, .dateCreated) == ["new", "mid", "old"])

        let sameInstantHigh = album(7, "b", created: 50)
        let sameInstantLow = album(6, "a", created: 50)
        #expect(VaultSorting.sorted([sameInstantHigh, sameInstantLow], by: .dateCreated) { _ in 0 }
            .map(\.id) == [uuid(6), uuid(7)])
    }

    // MARK: - Albums: photo count

    @Test func photoCountIsMostFirstAndBreaksTiesByNameThenId() {
        let many = album(1, "many")
        let fewA = album(3, "Delta")
        let fewB = album(2, "charlie")
        let none = album(4, "empty")
        let counts = [many.id: 9, fewA.id: 2, fewB.id: 2, none.id: 0]
        #expect(order([none, fewA, many, fewB], .photoCount, counts: counts)
                == ["many", "charlie", "Delta", "empty"])

        // Equal count AND equal folded name → id.
        let high = album(8, "same")
        let low = album(5, "SAME")
        let tied = [high.id: 4, low.id: 4]
        #expect(VaultSorting.sorted([high, low], by: .photoCount) { tied[$0.id] ?? 0 }
            .map(\.id) == [uuid(5), uuid(8)])
    }

    // MARK: - Notes: date modified (the default)

    @Test func dateModifiedIsNewestFirstAndBreaksTiesById() {
        let notes = [note(1, "a", updated: 10), note(2, "b", updated: 30), note(3, "c", updated: 20)]
        #expect(order(notes, .dateModified) == [uuid(2), uuid(3), uuid(1)])

        let tied = [note(9, "x", updated: 5), note(4, "y", updated: 5)]
        #expect(order(tied, .dateModified) == [uuid(4), uuid(9)])
    }

    // MARK: - Notes: date created

    @Test func noteDateCreatedIsNewestFirstAndIgnoresModification() {
        // Oldest note edited last: date_created must NOT reorder on updatedAt.
        let notes = [note(1, "a", created: 10, updated: 99),
                     note(2, "b", created: 30, updated: 1),
                     note(3, "c", created: 20, updated: 50)]
        #expect(order(notes, .dateCreated) == [uuid(2), uuid(3), uuid(1)])

        let tied = [note(9, "x", created: 7), note(4, "y", created: 7)]
        #expect(order(tied, .dateCreated) == [uuid(4), uuid(9)])
    }

    // MARK: - Notes: title

    @Test func titleIsAToZCaseAndDiacriticInsensitive() {
        let notes = [note(1, "zebra"), note(2, "Éclair"), note(3, "apple"), note(4, "Banana")]
        #expect(VaultSorting.sorted(notes, by: .title).map(\.title)
                == ["apple", "Banana", "Éclair", "zebra"])
    }

    @Test func notesWithAnEmptyDerivedTitleSortLast() {
        let titled = note(1, "beta")
        let untitled = note(2, "")           // derives to an empty title
        let whitespace = note(3, "   \n  ")  // also derives to an empty title
        let alpha = note(4, "alpha")
        let sorted = VaultSorting.sorted([untitled, titled, whitespace, alpha], by: .title)
        #expect(sorted.map(\.id) == [uuid(4), uuid(1), uuid(2), uuid(3)])
        #expect(sorted.suffix(2).allSatisfy { $0.title.trimmingCharacters(in: .whitespaces).isEmpty })
    }

    @Test func titleTiesBreakByUpdatedAtDescendingThenId() {
        // Same folded title, different edit times → most recently edited first.
        let stale = note(1, "Recipe", updated: 10)
        let fresh = note(2, "recipe", updated: 40)
        #expect(order([stale, fresh], .title) == [uuid(2), uuid(1)])

        // Same folded title AND the same edit instant → id.
        let high = note(9, "RECIPE", updated: 40)
        let low = note(4, "recipé", updated: 40)
        #expect(order([high, low], .title) == [uuid(4), uuid(9)])
    }

    // MARK: - Fold

    @Test func theFoldMatchesTheContactSortKeyFold() {
        #expect(VaultSorting.foldedKey("Éclair") == VaultSorting.foldedKey("eclair"))
        #expect(VaultSorting.foldedKey("  Ärger ") == "arger")
        #expect(VaultSorting.foldedKey("   ").isEmpty)
        // Same folding Contact.sortKey applies, so every A–Z list agrees.
        let contact = Contact(givenName: "Éclair")
        #expect(contact.sortKey == VaultSorting.foldedKey("Éclair"))
    }

    // MARK: - Sorting is total (no comparator crashes / no dropped rows)

    @Test func everyModeReturnsEveryRowExactlyOnce() {
        let albums = (1...6).map { album($0, ["b", "a", "b", "C", "á", ""][$0 - 1], created: Double($0 % 3), index: $0 % 2) }
        for mode in AlbumSort.allCases {
            let sorted = VaultSorting.sorted(albums, by: mode) { _ in albums.count % 3 }
            #expect(Set(sorted.map(\.id)) == Set(albums.map(\.id)))
            #expect(sorted.count == albums.count)
        }
        let notes = (1...6).map { note($0, ["b", "a", "", "C", "á", "b"][$0 - 1], created: Double($0 % 2), updated: Double($0 % 3)) }
        for mode in NoteSort.allCases {
            let sorted = VaultSorting.sorted(notes, by: mode)
            #expect(Set(sorted.map(\.id)) == Set(notes.map(\.id)))
            #expect(sorted.count == notes.count)
        }
    }

    // MARK: - Repository integration: photo_count counts LIVE photos only

    @Test func photoCountOrderIgnoresTrashedPhotos() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxSortTests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let fileStore = PhotoFileStore(rootURL: root)
        let repo = SwiftDataPhotoRepository(container: ModelContainerFactory.inMemory(),
                                            fileStore: fileStore)

        let big = try repo.createAlbum(name: "big")     // 3 photos, then 1 trashed
        let small = try repo.createAlbum(name: "small") // 2 photos
        var bigPhotos: [Photo] = []
        for _ in 0..<3 {
            bigPhotos.append(try repo.insertPhoto(try await fileStore.store(data: pngData()), albumId: big.id)!)
        }
        for _ in 0..<2 {
            _ = try repo.insertPhoto(try await fileStore.store(data: pngData()), albumId: small.id)
        }
        #expect(repo.albums(sortedBy: .photoCount).map(\.name) == ["big", "small"])

        // Trashing two of "big"'s photos drops it below "small".
        try repo.deletePhotos(Array(bigPhotos.prefix(2)))
        #expect(repo.albums(sortedBy: .photoCount).map(\.name) == ["small", "big"])
        // ... and restoring them puts it back on top.
        try repo.restorePhotos(ids: bigPhotos.prefix(2).map(\.id))
        #expect(repo.albums(sortedBy: .photoCount).map(\.name) == ["big", "small"])
    }

    @Test func theRepositoryAppliesEveryAlbumModeAndHidesTrashedAlbums() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxSortTests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let repo = SwiftDataPhotoRepository(container: ModelContainerFactory.inMemory(),
                                            fileStore: PhotoFileStore(rootURL: root))
        _ = try repo.createAlbum(name: "zulu")   // sortIndex 0, created first
        _ = try repo.createAlbum(name: "alpha")  // sortIndex 1
        let gone = try repo.createAlbum(name: "gone")     // sortIndex 2, trashed below
        try repo.deleteAlbum(gone)

        #expect(repo.albums(sortedBy: .manual).map(\.name) == ["zulu", "alpha"])
        #expect(repo.albums().map(\.name) == ["zulu", "alpha"]) // convenience == manual
        #expect(repo.albums(sortedBy: .name).map(\.name) == ["alpha", "zulu"])
        #expect(repo.albums(sortedBy: .dateCreated).map(\.name) == ["alpha", "zulu"])
        #expect(repo.albums(sortedBy: .photoCount).map(\.name) == ["alpha", "zulu"]) // 0 == 0 → name
    }

    @Test func theRepositoryAppliesEveryNoteModeAndHidesTrashedNotes() throws {
        let repo = SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
        let zulu = try repo.createNote(body: "zulu")
        let alpha = try repo.createNote(body: "alpha")
        let untitled = try repo.createNote(body: "")
        let gone = try repo.createNote(body: "gone")
        try repo.delete(note: gone)
        // Touch "zulu" so it is the most recently modified but still the oldest.
        try repo.save(note: zulu, body: "zulu edited")

        #expect(repo.notes(sortedBy: .dateModified).map(\.id) == [zulu.id, untitled.id, alpha.id])
        #expect(repo.notes().map(\.id) == [zulu.id, untitled.id, alpha.id]) // convenience == modified
        #expect(repo.notes(sortedBy: .dateCreated).map(\.id) == [untitled.id, alpha.id, zulu.id])
        #expect(repo.notes(sortedBy: .title).map(\.id) == [alpha.id, zulu.id, untitled.id])
    }

    private func pngData() -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8), format: format)
        return renderer.image { ctx in
            UIColor.systemTeal.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }.pngData()!
    }
}
