import Foundation
import SwiftData

/// Albums and photos. Since P3 every delete is a SOFT delete (`deletedAt =
/// now`); `albums(sortedBy:)` / `photos(in:)` return live rows only. Files are
/// removed only by `purgeAlbums` / `purgePhotos` / `purgeExpired` (and
/// `VaultNuker`).
@MainActor
protocol PhotoRepository: AnyObject {
    /// Live albums in the requested order (decisions §4). Sorting happens here,
    /// on the fetched list — never in a view body.
    func albums(sortedBy sort: AlbumSort) -> [Album]
    @discardableResult
    func createAlbum(name: String) throws -> Album
    func renameAlbum(_ album: Album, to name: String) throws
    /// Soft-deletes the album and its live photos with one shared stamp.
    func deleteAlbum(_ album: Album) throws
    /// Live photos of `album`, by `sortIndex`.
    func photos(in album: Album) -> [Photo]
    func album(withId id: UUID) -> Album?
    @discardableResult
    func insertPhoto(_ stored: PhotoFileStore.StoredPhoto, albumId: UUID) throws -> Photo?
    /// Soft-deletes the photos (files untouched).
    func deletePhotos(_ photos: [Photo]) throws
    func movePhotos(_ photos: [Photo], to album: Album) throws
    /// Enumerates ALL photo rows, trashed ones included — a trashed photo keeps
    /// its files until purge.
    func performOrphanSweep() async

    // MARK: Trash (P3)

    func trashedAlbums() -> [Album]
    /// Every trashed photo, including those under a trashed album.
    func trashedPhotos() -> [Photo]
    /// Clears the album's stamp and the stamp of photos that carry the SAME
    /// stamp; photos trashed individually earlier stay in the trash.
    func restoreAlbums(ids: [UUID]) throws
    /// Photos return in place (`sortIndex` is untouched). If the photo's album
    /// is trashed the album row is restored too, so the photo is reachable.
    func restorePhotos(ids: [UUID]) throws
    /// Hard delete: every photo of the album (any stamp) — both files and the
    /// row — then the album row.
    func purgeAlbums(ids: [UUID]) async throws
    /// Hard delete: both files and the row.
    func purgePhotos(ids: [UUID]) async throws
    /// Purges every trashed album/photo whose retention has run out.
    func purgeExpired(now: Date) async
}

extension PhotoRepository {
    /// Albums in the default (manual / `sortIndex`) order — what every caller
    /// that has no user preference to honour wants.
    func albums() -> [Album] { albums(sortedBy: .manual) }
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

    // MARK: - Albums

    func albums(sortedBy sort: AlbumSort) -> [Album] {
        let descriptor = FetchDescriptor<Album>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.sortIndex)]
        )
        let live = (try? context.fetch(descriptor)) ?? []
        // `photos(in:)` is the LIVE query — trashed photos must not count
        // towards the photo_count order (P3).
        return VaultSorting.sorted(live, by: sort) { self.photos(in: $0).count }
    }

    func album(withId id: UUID) -> Album? {
        var descriptor = FetchDescriptor<Album>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first
    }

    @discardableResult
    func createAlbum(name: String) throws -> Album {
        // Next index over ALL albums (trashed ones return in place on restore).
        let all = (try? context.fetch(FetchDescriptor<Album>())) ?? []
        let nextIndex = (all.map(\.sortIndex).max() ?? -1) + 1
        let album = Album(name: name, sortIndex: nextIndex)
        context.insert(album)
        try context.save()
        return album
    }

    func renameAlbum(_ album: Album, to name: String) throws {
        album.name = name
        try context.save()
    }

    func deleteAlbum(_ album: Album) throws {
        // One stamp for the album and the photos it held while live, so
        // restoreAlbums can tell them from photos trashed individually earlier.
        let stamp = Date.now
        album.deletedAt = stamp
        for photo in album.photos where photo.deletedAt == nil {
            photo.deletedAt = stamp
        }
        try context.save()
    }

    // MARK: - Photos

    func photos(in album: Album) -> [Photo] {
        // The relationship includes trashed photos — filter explicitly.
        album.photos
            .filter { $0.deletedAt == nil }
            .sorted { $0.sortIndex < $1.sortIndex }
    }

    @discardableResult
    func insertPhoto(_ stored: PhotoFileStore.StoredPhoto, albumId: UUID) throws -> Photo? {
        guard let album = album(withId: albumId) else { return nil }
        let nextIndex = (album.photos.map(\.sortIndex).max() ?? -1) + 1
        let photo = Photo(id: stored.id, fileName: stored.fileName, thumbFileName: stored.thumbFileName,
                          mimeType: stored.mimeType, width: stored.width, height: stored.height,
                          byteCount: stored.byteCount, sortIndex: nextIndex,
                          mediaType: stored.mediaType, durationMs: stored.durationMs, album: album)
        // An import finishing after its album was trashed lands in the trash
        // with the album's stamp, so restoring the album brings it back too.
        photo.deletedAt = album.deletedAt
        context.insert(photo)
        try context.save()
        return photo
    }

    func deletePhotos(_ photos: [Photo]) throws {
        let stamp = Date.now
        for photo in photos {
            photo.deletedAt = stamp
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
        // Deliberately unfiltered: trashed rows still own their files.
        let descriptor = FetchDescriptor<Photo>()
        let all = (try? context.fetch(descriptor)) ?? []
        let fileNames = Set(all.map(\.fileName))
        let thumbNames = Set(all.map(\.thumbFileName))
        await fileStore.sweepOrphans(knownFileNames: fileNames, knownThumbFileNames: thumbNames)
    }

    // MARK: - Trash

    func trashedAlbums() -> [Album] {
        let descriptor = FetchDescriptor<Album>(predicate: #Predicate { $0.deletedAt != nil })
        return (try? context.fetch(descriptor)) ?? []
    }

    func trashedPhotos() -> [Photo] {
        let descriptor = FetchDescriptor<Photo>(predicate: #Predicate { $0.deletedAt != nil })
        return (try? context.fetch(descriptor)) ?? []
    }

    func restoreAlbums(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for album in fetchAlbums(ids: ids) {
            guard let stamp = album.deletedAt else { continue }
            album.deletedAt = nil
            for photo in album.photos where photo.deletedAt == stamp {
                photo.deletedAt = nil
            }
        }
        try context.save()
    }

    func restorePhotos(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for photo in fetchPhotos(ids: ids) {
            photo.deletedAt = nil
            if let album = photo.album, album.deletedAt != nil {
                album.deletedAt = nil
            }
        }
        try context.save()
    }

    // Purge order: rows are deleted and SAVED first, files second. A crash
    // between the two steps then leaves orphan files (picked up by the next
    // orphan sweep), never zombie trash rows whose files are already gone.

    func purgeAlbums(ids: [UUID]) async throws {
        guard !ids.isEmpty else { return }
        var files: [(fileName: String, thumbFileName: String)] = []
        for album in fetchAlbums(ids: ids) {
            // Every photo regardless of stamp; the cascade would remove rows
            // only, so the file names are collected explicitly.
            for photo in album.photos {
                files.append((photo.fileName, photo.thumbFileName))
                context.delete(photo)
            }
            context.delete(album)
        }
        try context.save()
        for file in files {
            await fileStore.delete(fileName: file.fileName, thumbFileName: file.thumbFileName)
        }
    }

    func purgePhotos(ids: [UUID]) async throws {
        guard !ids.isEmpty else { return }
        var files: [(fileName: String, thumbFileName: String)] = []
        for photo in fetchPhotos(ids: ids) {
            files.append((photo.fileName, photo.thumbFileName))
            context.delete(photo)
        }
        try context.save()
        for file in files {
            await fileStore.delete(fileName: file.fileName, thumbFileName: file.thumbFileName)
        }
    }

    func purgeExpired(now: Date) async {
        let expiredAlbums = trashedAlbums()
            .filter { TrashPolicy.isExpired(deletedAt: $0.deletedAt ?? now, now: now) }
            .map(\.id)
        try? await purgeAlbums(ids: expiredAlbums)
        let expiredPhotos = trashedPhotos()
            .filter { TrashPolicy.isExpired(deletedAt: $0.deletedAt ?? now, now: now) }
            .map(\.id)
        try? await purgePhotos(ids: expiredPhotos)
    }

    // MARK: - Fetch helpers

    private func fetchAlbums(ids: [UUID]) -> [Album] {
        let descriptor = FetchDescriptor<Album>(predicate: #Predicate { ids.contains($0.id) })
        return (try? context.fetch(descriptor)) ?? []
    }

    private func fetchPhotos(ids: [UUID]) -> [Photo] {
        let descriptor = FetchDescriptor<Photo>(predicate: #Predicate { ids.contains($0.id) })
        return (try? context.fetch(descriptor)) ?? []
    }
}
