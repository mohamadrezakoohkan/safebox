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
        #expect(repo.photos(in: album).map(\.id) == [first?.id, second?.id].compactMap { $0 })
    }

    @Test func deletePhotoRemovesRowAndBothFiles() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let stored = try await fileStore.store(data: makePNGData())
        let photo = try repo.insertPhoto(stored, albumId: album.id)!
        let fileURL = fileStore.photoURL(fileName: photo.fileName)
        let thumbURL = fileStore.thumbnailURL(thumbFileName: photo.thumbFileName)
        #expect(fileExists(fileURL))
        #expect(fileExists(thumbURL))

        try await repo.deletePhotos([photo])
        #expect(repo.photos(in: album).isEmpty)
        #expect(!fileExists(fileURL))
        #expect(!fileExists(thumbURL))
    }

    @Test func deleteAlbumRemovesAllPhotoFiles() async throws {
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
        try await repo.deleteAlbum(album)
        #expect(repo.albums().isEmpty)
        for url in urls {
            #expect(!fileExists(url)) // no orphan files after album delete
        }
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

    @Test func derivedAlbumCoverIsFirstPhotoBySortIndex() async throws {
        let (repo, fileStore, root) = makeStack()
        defer { try? FileManager.default.removeItem(at: root) }
        let album = try repo.createAlbum(name: "A")
        let first = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)!
        _ = try repo.insertPhoto(try await fileStore.store(data: makePNGData()), albumId: album.id)
        #expect(repo.photos(in: album).first?.id == first.id)
        // Deleting the cover: the next photo becomes the derived cover.
        try await repo.deletePhotos([first])
        #expect(repo.photos(in: album).first != nil)
        #expect(repo.photos(in: album).first?.id != first.id)
    }
}
