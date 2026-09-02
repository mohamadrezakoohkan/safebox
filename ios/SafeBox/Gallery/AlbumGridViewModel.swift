import Foundation
import Observation
import PhotosUI
import SwiftUI

@MainActor
@Observable
final class AlbumGridViewModel {
    let album: Album
    private let repository: any PhotoRepository
    let importer: PhotoImporter

    private(set) var photos: [Photo] = []
    var isSelecting = false
    var selection: Set<UUID> = []

    init(album: Album, repository: any PhotoRepository, importer: PhotoImporter) {
        self.album = album
        self.repository = repository
        self.importer = importer
    }

    func reload() {
        photos = repository.photos(in: album)
    }

    var selectedPhotos: [Photo] {
        photos.filter { selection.contains($0.id) }
    }

    var otherAlbums: [Album] {
        repository.albums().filter { $0.id != album.id }
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

    func deleteSelected() async {
        try? await repository.deletePhotos(selectedPhotos)
        exitSelectMode()
        reload()
    }

    func delete(_ photo: Photo) async {
        try? await repository.deletePhotos([photo])
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
    /// even if the vault locked during the picker round-trip.
    func importPicked(_ items: [PhotosPickerItem]) async {
        await importer.importItems(items, albumId: album.id)
        reload()
    }
}
