import Foundation
import Observation

@MainActor
@Observable
final class AlbumListViewModel {
    private let repository: any PhotoRepository
    private let defaults: UserDefaults
    private(set) var albums: [Album] = []

    /// The persisted sort mode (decisions §4). Read from UserDefaults at init —
    /// this object is rebuilt on every unlock, so the choice has to outlive it.
    private(set) var sort: AlbumSort

    init(repository: any PhotoRepository, defaults: UserDefaults = .standard) {
        self.repository = repository
        self.defaults = defaults
        self.sort = SortPreferences.albumSort(defaults: defaults)
    }

    func reload() {
        albums = repository.albums(sortedBy: sort)
    }

    /// Persists the choice first, then reorders — so a lock (or a crash) right
    /// after the tap still comes back in the order the user picked.
    func setSort(_ newSort: AlbumSort) {
        guard newSort != sort else { return }
        sort = newSort
        SortPreferences.setAlbumSort(newSort, defaults: defaults)
        reload()
    }

    /// Derived cover: first LIVE photo by sortIndex — nothing to dangle on
    /// delete/move, and trashed photos never surface as covers.
    func coverPhoto(for album: Album) -> Photo? {
        repository.photos(in: album).first
    }

    /// Live photos only (the relationship also holds trashed ones).
    func photoCount(for album: Album) -> Int {
        repository.photos(in: album).count
    }

    func createAlbum(named name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        _ = try? repository.createAlbum(name: trimmed)
        reload()
    }

    func renameAlbum(_ album: Album, to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        try? repository.renameAlbum(album, to: trimmed)
        reload()
    }

    /// Soft delete (album + its live photos go to Recently deleted).
    func deleteAlbum(_ album: Album) {
        try? repository.deleteAlbum(album)
        reload()
    }

    /// Undo path: brings the albums (and the photos trashed with them) back.
    func restoreAlbums(ids: [UUID]) {
        try? repository.restoreAlbums(ids: ids)
        reload()
    }
}
