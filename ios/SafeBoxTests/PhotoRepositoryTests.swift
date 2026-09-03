import Foundation
import Testing
import UIKit
@testable import SafeBox

@MainActor
struct PhotoRepositoryTests {
    private func makeStack() -> (SwiftDataPhotoRepository, PhotoFileStore, URL) {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxTests-\(UUID().uuidString)", isDirectory: true)
        let fileStore = PhotoFileStore(rootURL: root)
        let repo = SwiftDataPhotoRepository(container: ModelContainerFactory.inMemory(),
                                            fileStore: fileStore)
        return (repo, fileStore, root)
    }

    private func makePNGData() -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1 // pixel-exact dimensions regardless of device scale
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 12), format: format)
        let image = renderer.image { ctx in
            UIColor.systemOrange.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 20, height: 12))
        }
        return image.pngData()!
    }

    private func fileExists(_ url: URL) -> Bool {
        FileManager.default.fileExists(atPath: url.path)
    }

    private func fileURLs(_ photo: Photo, _ fileStore: PhotoFileStore) -> (URL, URL) {
        (fileStore.photoURL(fileName: photo.fileName), fileStore.thumbnailURL(thumbFileName: photo.thumbFileName))
    }

    private let thirtyOneDaysAgo = Date.now.addingTimeInterval(-31 * 86_400)

    // MARK: - Albums & import

    @Test func albumCRUD() throws {
        let (repo, _, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "Trips")
        #expect(repo.albums().count == 1)
        try repo.renameAlbum(album, to: "Holidays")
        #expect(repo.albums().first?.name == "Holidays")
    }

    @Test func storePreservesOriginalBytesAndDetectsType() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = repo
        let data = makePNGData()
        let stored = try await fileStore.store(data: data)
        #expect(stored.fileName.hasSuffix(".png"))     // real extension
        #expect(stored.mimeType == "image/png")
        #expect(stored.width == 20)
        #expect(stored.height == 12)
        #expect(stored.byteCount == data.count)
        #expect(stored.mediaType == MediaType.photo.rawValue)
        #expect(stored.durationMs == nil)
        // Byte-for-byte original.
        let written = try Data(contentsOf: fileStore.photoURL(fileName: stored.fileName))
        #expect(written == data)
        // Thumbnail generated alongside.
        #expect(fileExists(fileStore.thumbnailURL(thumbFileName: stored.thumbFileName)))
    }

    @Test func insertPhotoAssignsImportOrder() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let first = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)
        let second = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)
        #expect(first?.sortIndex == 0)
        #expect(second?.sortIndex == 1)
        #expect(first?.mediaType == MediaType.photo.rawValue)
        #expect(first?.durationMs == nil)
        #expect(first?.deletedAt == nil)
        #expect(repo.photos(in: album).map(\.id) == [first?.id, second?.id].compactMap { $0 })
    }

    @Test func movePhotoBetweenAlbums() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try repo.createAlbum(name: "Source")
        let target = try repo.createAlbum(name: "Target")
        let photo = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: source.id)!
        try repo.movePhotos([photo], to: target)
        #expect(repo.photos(in: source).isEmpty)
        #expect(repo.photos(in: target).map(\.id) == [photo.id])
    }

    @Test func photoMetadataReflectsStoredValues() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let data = makePNGData()
        let before = Date()
        let inserted = try repo.insertPhoto(try await fileStore.store(data: data), albumId: album.id)!

        // Snapshot from the row as the pager sees it, not from the insert result.
        let photo = try #require(repo.photos(in: album).first { $0.id == inserted.id })
        let metadata = PhotoMetadata(photo: photo)
        #expect(metadata.id == inserted.id)
        #expect(metadata.width == 20)
        #expect(metadata.height == 12)
        #expect(metadata.byteCount == data.count)
        #expect(metadata.mimeType == "image/png")
        #expect(metadata.importedAt >= before && metadata.importedAt <= Date())
        #expect(metadata.durationMs == nil) // stills carry no Duration row

        let rows = metadata.rows(locale: Locale(identifier: "en_US"))
        #expect(rows.count == 4)
        #expect(rows[0].value == "20 × 12")
        #expect(rows[2].value == "PNG")
        #expect(rows.allSatisfy { !$0.value.isEmpty })
    }

    @Test func derivedAlbumCoverIsFirstPhotoBySortIndex() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let first = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        _ = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)
        #expect(repo.photos(in: album).first?.id == first.id)
        // Deleting the cover: the next photo becomes the derived cover — a
        // trashed photo never surfaces as a cover.
        try repo.deletePhotos([first])
        #expect(repo.photos(in: album).first != nil)
        #expect(repo.photos(in: album).first?.id != first.id)
    }

    // MARK: - Soft delete (P3). Replaces the iteration-1 tests
    // `deletePhotoRemovesRowAndBothFiles` / `deleteAlbumRemovesAllPhotoFiles`:
    // files now survive a delete and are removed at purge time.

    @Test func deletePhotoIsSoftAndKeepsBothFilesUntilPurge() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let photo = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        let (fileURL, thumbURL) = fileURLs(photo, fileStore)

        try repo.deletePhotos([photo])
        // Gone from the live list, present in the trash, files intact.
        #expect(repo.photos(in: album).isEmpty)
        #expect(repo.trashedPhotos().map(\.id) == [photo.id])
        #expect(photo.deletedAt != nil)
        #expect(fileExists(fileURL))
        #expect(fileExists(thumbURL))

        // Purge removes the row AND both files.
        try await repo.purgePhotos(ids: [photo.id])
        #expect(repo.trashedPhotos().isEmpty)
        #expect(repo.photos(in: album).isEmpty)
        #expect(!fileExists(fileURL))
        #expect(!fileExists(thumbURL))
    }

    @Test func deleteAlbumIsSoftAndPurgeRemovesAllPhotoFiles() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        var urls: [URL] = []
        for _ in 0..<3 {
            let stored = try await fileStore.store(data: makePNGData())
            _ = try repo.insertPhoto(stored, albumId: album.id)
            urls.append(fileStore.photoURL(fileName: stored.fileName))
            urls.append(fileStore.thumbnailURL(thumbFileName: stored.thumbFileName))
        }

        try repo.deleteAlbum(album)
        #expect(repo.albums().isEmpty)
        #expect(repo.trashedAlbums().map(\.id) == [album.id])
        #expect(repo.trashedPhotos().count == 3)
        // Album and its photos share ONE stamp.
        #expect(Set(repo.trashedPhotos().map(\.deletedAt)) == [album.deletedAt])
        for url in urls {
            #expect(fileExists(url)) // files survive the soft delete
        }

        try await repo.purgeAlbums(ids: [album.id])
        #expect(repo.trashedAlbums().isEmpty)
        #expect(repo.trashedPhotos().isEmpty)
        for url in urls {
            #expect(!fileExists(url)) // no orphan files after purge
        }
    }

    @Test func restorePhotoReturnsInPlaceWithFilesAndAlbumMembership() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        var photos: [Photo] = []
        for _ in 0..<3 {
            photos.append(try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!)
        }
        let middle = photos[1]
        let (fileURL, thumbURL) = fileURLs(middle, fileStore)

        try repo.deletePhotos([middle])
        #expect(repo.photos(in: album).map(\.id) == [photos[0].id, photos[2].id])

        try repo.restorePhotos(ids: [middle.id])
        #expect(middle.deletedAt == nil)
        #expect(middle.sortIndex == 1)                         // original position kept
        #expect(middle.album?.id == album.id)                  // membership kept
        #expect(repo.photos(in: album).map(\.id) == photos.map(\.id))
        #expect(repo.trashedPhotos().isEmpty)
        #expect(fileExists(fileURL))
        #expect(fileExists(thumbURL))
    }

    @Test func albumSoftDeleteAndRestoreRoundTripsItsPhotos() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        var photos: [Photo] = []
        for _ in 0..<3 {
            photos.append(try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!)
        }
        // One photo trashed on its own, earlier, with its own stamp.
        try repo.deletePhotos([photos[1]])
        let earlierStamp = photos[1].deletedAt

        try repo.deleteAlbum(album)
        #expect(repo.albums().isEmpty)
        #expect(photos[1].deletedAt == earlierStamp)           // not re-stamped
        #expect(photos[0].deletedAt == album.deletedAt)
        #expect(photos[2].deletedAt == album.deletedAt)

        try repo.restoreAlbums(ids: [album.id])
        #expect(repo.albums().map(\.id) == [album.id])
        // Photos trashed WITH the album come back; the earlier one stays trashed.
        #expect(repo.photos(in: album).map(\.id) == [photos[0].id, photos[2].id])
        #expect(repo.trashedPhotos().map(\.id) == [photos[1].id])
    }

    @Test func restoringPhotoUnderTrashedAlbumRestoresTheAlbumRow() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let kept = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        let other = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        try repo.deleteAlbum(album)

        try repo.restorePhotos(ids: [kept.id])
        #expect(repo.albums().map(\.id) == [album.id])         // album row back so the photo is reachable
        #expect(repo.photos(in: album).map(\.id) == [kept.id])
        #expect(repo.trashedPhotos().map(\.id) == [other.id])  // sibling stays trashed
    }

    @Test func importIntoTrashedAlbumLandsInTrashWithTheAlbumStamp() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        try repo.deleteAlbum(album)
        // A picker round-trip finishing after the album was trashed.
        let late = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        #expect(late.deletedAt == album.deletedAt)
        try repo.restoreAlbums(ids: [album.id])
        #expect(repo.photos(in: album).map(\.id) == [late.id])
    }

    @Test func purgeExpiredRemovesOnlyExpiredPhotosAndAlbums() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let expired = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        let fresh = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        let expiredAlbum = try repo.createAlbum(name: "Old")
        let inExpiredAlbum = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: expiredAlbum.id)!
        let (expiredFile, expiredThumb) = fileURLs(expired, fileStore)
        let (freshFile, freshThumb) = fileURLs(fresh, fileStore)
        let (oldAlbumFile, oldAlbumThumb) = fileURLs(inExpiredAlbum, fileStore)

        try repo.deletePhotos([expired, fresh])
        try repo.deleteAlbum(expiredAlbum)
        // Age the expired ones past the 30-day retention.
        expired.deletedAt = thirtyOneDaysAgo
        expiredAlbum.deletedAt = thirtyOneDaysAgo
        inExpiredAlbum.deletedAt = thirtyOneDaysAgo

        await repo.purgeExpired(now: .now)

        #expect(repo.trashedPhotos().map(\.id) == [fresh.id])  // non-expired survives
        #expect(repo.trashedAlbums().isEmpty)
        #expect(!fileExists(expiredFile))
        #expect(!fileExists(expiredThumb))
        #expect(!fileExists(oldAlbumFile))
        #expect(!fileExists(oldAlbumThumb))
        #expect(fileExists(freshFile))
        #expect(fileExists(freshThumb))
    }

    // MARK: - Orphan sweep

    @Test func orphanSweepRemovesUnreferencedFiles() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let kept = try await fileStore.store(data: makePNGData())
        _ = try repo.insertPhoto(kept, albumId: album.id)
        // Simulate a crash between file write and row insert.
        let orphan = try await fileStore.store(data: makePNGData())

        await repo.performOrphanSweep()

        #expect(fileExists(fileStore.photoURL(fileName: kept.fileName)))
        #expect(!fileExists(fileStore.photoURL(fileName: orphan.fileName)))
        #expect(!fileExists(fileStore.thumbnailURL(thumbFileName: orphan.thumbFileName)))
    }

    @Test func orphanSweepSparesFilesOfTrashedRows() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let trashedPhoto = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        let trashedAlbum = try repo.createAlbum(name: "B")
        let underTrashedAlbum = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: trashedAlbum.id)!
        try repo.deletePhotos([trashedPhoto])
        try repo.deleteAlbum(trashedAlbum)
        let orphan = try await fileStore.store(data: makePNGData())

        await repo.performOrphanSweep()

        // The sweep enumerates trashed rows too: their files must survive.
        for photo in [trashedPhoto, underTrashedAlbum] {
            let (fileURL, thumbURL) = fileURLs(photo, fileStore)
            #expect(fileExists(fileURL))
            #expect(fileExists(thumbURL))
        }
        #expect(!fileExists(fileStore.photoURL(fileName: orphan.fileName)))
    }
}
