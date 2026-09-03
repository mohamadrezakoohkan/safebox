import Foundation
import Observation
import PhotosUI
import SwiftUI

@MainActor
@Observable
final class AlbumGridViewModel {
    let album: Album
    private let repository: any PhotoRepository
    private let defaults: UserDefaults
    let importer: PhotoImporter

    private(set) var photos: [Photo] = []
    var isSelecting = false
    var selection: Set<UUID> = []

    init(album: Album, repository: any PhotoRepository, importer: PhotoImporter,
         defaults: UserDefaults = .standard) {
        self.album = album
        self.repository = repository
        self.importer = importer
        self.defaults = defaults
    }

    func reload() {
        photos = repository.photos(in: album)
    }

    var selectedPhotos: [Photo] {
        photos.filter { selection.contains($0.id) }
    }

    /// Move-to-album targets, in the order the user chose for the gallery
    /// (decisions §4) — the menu must not silently fall back to `manual`.
    var otherAlbums: [Album] {
        repository.albums(sortedBy: SortPreferences.albumSort(defaults: defaults))
            .filter { $0.id != album.id }
    }

    func toggleSelection(_ photo: Photo) {
        if selection.contains(photo.id) {
            selection.remove(photo.id)
        } else {
            selection.insert(photo.id)
        }
    }

    func exitSelectMode() {
        isSelecting = false
        selection = []
    }

    /// Soft-deletes the selection; returns the trashed ids for the undo toast.
    @discardableResult
    func deleteSelected() -> [UUID] {
        let ids = delete(selectedPhotos)
        exitSelectMode()
        return ids
    }

    /// Soft-deletes the photos (files stay until purge); returns their ids.
    @discardableResult
    func delete(_ photos: [Photo]) -> [UUID] {
        guard !photos.isEmpty else { return [] }
        let ids = photos.map(\.id)
        try? repository.deletePhotos(photos)
        reload()
        return ids
    }

    /// Undo path: photos return at their original `sortIndex`.
    func restorePhotos(ids: [UUID]) {
        try? repository.restorePhotos(ids: ids)
        reload()
    }

    func moveSelected(to target: Album) {
        try? repository.movePhotos(selectedPhotos, to: target)
        exitSelectMode()
        reload()
    }

    func move(_ photo: Photo, to target: Album) {
        try? repository.movePhotos([photo], to: target)
        reload()
    }

    /// Import runs at the repository level keyed by albumId, so it completes
    /// even if the vault locked during the picker round-trip. Returns how many
    /// videos failed, so the screen can surface `video_import_failed` (N3).
    @discardableResult
    func importPicked(_ items: [PhotosPickerItem]) async -> Int {
        let failedVideos = await importer.importItems(items, albumId: album.id)
        reload()
        return failedVideos
    }
}
