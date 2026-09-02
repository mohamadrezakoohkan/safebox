import Foundation
import Observation

@MainActor
@Observable
final class AlbumListViewModel {
    private let repository: any PhotoRepository
    private(set) var albums: [Album] = []

    init(repository: any PhotoRepository) {
        self.repository = repository
    }

    func reload() {
        albums = repository.albums()
    }

    /// Derived cover: first photo by sortIndex — nothing to dangle on delete/move.
    func coverPhoto(for album: Album) -> Photo? {
        repository.photos(in: album).first
    }

    func photoCount(for album: Album) -> Int {
        album.photos.count
    }

    func createAlbum(named name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        try? repository.createAlbum(name: trimmed)
        reload()
    }

    func renameAlbum(_ album: Album, to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        try? repository.renameAlbum(album, to: trimmed)
        reload()
    }

    func deleteAlbum(_ album: Album) async {
        try? await repository.deleteAlbum(album)
        reload()
    }
}
