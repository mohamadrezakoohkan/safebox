import Foundation
import SwiftData

@MainActor
protocol PhotoRepository: AnyObject {
    func albums() -> [Album]
    @discardableResult
    func createAlbum(name: String) throws -> Album
    func renameAlbum(_ album: Album, to name: String) throws
    func deleteAlbum(_ album: Album) async throws
    func photos(in album: Album) -> [Photo]
    func album(withId id: UUID) -> Album?
    @discardableResult
    func insertPhoto(_ stored: PhotoFileStore.StoredPhoto, albumId: UUID) throws -> Photo?
    func deletePhotos(_ photos: [Photo]) async throws
    func movePhotos(_ photos: [Photo], to album: Album) throws
    func performOrphanSweep() async
}

@MainActor
final class SwiftDataPhotoRepository: PhotoRepository {
    // The container must be retained: ModelContext references it weakly, and a
    // deallocated container traps on the first model operation.
    private let container: ModelContainer
    private let context: ModelContext
    private let fileStore: PhotoFileStore

    init(container: ModelContainer, fileStore: PhotoFileStore) {
        self.container = container
        self.context = container.mainContext
        self.fileStore = fileStore
    }

    func albums() -> [Album] {
        let descriptor = FetchDescriptor<Album>(sortBy: [SortDescriptor(\.sortIndex)])
        return (try? context.fetch(descriptor)) ?? []
    }

    func album(withId id: UUID) -> Album? {
        var descriptor = FetchDescriptor<Album>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first
    }

    @discardableResult
    func createAlbum(name: String) throws -> Album {
        let nextIndex = (albums().map(\.sortIndex).max() ?? -1) + 1
        let album = Album(name: name, sortIndex: nextIndex)
        context.insert(album)
        try context.save()
        return album
    }

    func renameAlbum(_ album: Album, to name: String) throws {
        album.name = name
        try context.save()
    }

    func deleteAlbum(_ album: Album) async throws {
        // Enumerate and delete each photo's files FIRST — the SwiftData cascade
        // removes rows without touching the file store.
        let albumPhotos = photos(in: album)
        for photo in albumPhotos {
            await fileStore.delete(fileName: photo.fileName, thumbFileName: photo.thumbFileName)
        }
        context.delete(album)
        try context.save()
    }

    func photos(in album: Album) -> [Photo] {
        album.photos.sorted { $0.sortIndex < $1.sortIndex }
    }

    @discardableResult
    func insertPhoto(_ stored: PhotoFileStore.StoredPhoto, albumId: UUID) throws -> Photo? {
        guard let album = album(withId: albumId) else { return nil }
        let nextIndex = (album.photos.map(\.sortIndex).max() ?? -1) + 1
        let photo = Photo(id: stored.id, fileName: stored.fileName, thumbFileName: stored.thumbFileName,
                          mimeType: stored.mimeType, width: stored.width, height: stored.height,
                          byteCount: stored.byteCount, sortIndex: nextIndex, album: album)
        context.insert(photo)
        try context.save()
        return photo
    }

    func deletePhotos(_ photos: [Photo]) async throws {
        for photo in photos {
            await fileStore.delete(fileName: photo.fileName, thumbFileName: photo.thumbFileName)
            context.delete(photo)
        }
        try context.save()
    }

    func movePhotos(_ photos: [Photo], to album: Album) throws {
        var nextIndex = (album.photos.map(\.sortIndex).max() ?? -1) + 1
        for photo in photos {
            photo.album = album
            photo.sortIndex = nextIndex
            nextIndex += 1
        }
        try context.save()
    }

    func performOrphanSweep() async {
        let descriptor = FetchDescriptor<Photo>()
        let all = (try? context.fetch(descriptor)) ?? []
        let fileNames = Set(all.map(\.fileName))
        let thumbNames = Set(all.map(\.thumbFileName))
        await fileStore.sweepOrphans(knownFileNames: fileNames, knownThumbFileNames: thumbNames)
    }
}
