import Foundation
import Observation

@MainActor
@Observable
final class TrashViewModel {
    private let repository: any TrashRepository
    private let now: () -> Date

    private(set) var contents = TrashContents()

    init(repository: any TrashRepository, now: @escaping () -> Date = { .now }) {
        self.repository = repository
        self.now = now
    }

    func reload() {
        contents = repository.contents()
    }

    func restore(_ item: TrashItemID) {
        try? repository.restore([item])
        reload()
    }

    /// The row leaves `contents` synchronously, before the first suspension:
    /// SwiftUI may re-evaluate the list body while the purge is in flight, and
    /// it must never touch a `@Model` that has already been deleted and saved.
    func purge(_ item: TrashItemID) async {
        contents.remove(item)
        try? await repository.purge([item])
        reload()
    }

    /// Same rule as `purge`: clear first, then await.
    func emptyAll() async {
        contents = TrashContents()
        try? await repository.emptyAll()
        reload()
    }

    func daysLeft(deletedAt: Date?) -> Int {
        let current = now()
        return TrashPolicy.daysLeft(deletedAt: deletedAt ?? current, now: current)
    }

    /// Photo count shown under an album row: only the photos that carry the
    /// album's own stamp — exactly the set Restore brings back (decisions §3).
    /// Photos trashed individually earlier stay in the trash on restore and
    /// are therefore not counted here, although a purge removes them too.
    func trashedPhotoCount(in album: Album) -> Int {
        guard let stamp = album.deletedAt else { return 0 }
        return album.photos.filter { $0.deletedAt == stamp }.count
    }

    /// Cover for an album row: its first trashed photo by `sortIndex`.
    func coverPhoto(for album: Album) -> Photo? {
        album.photos
            .filter { $0.deletedAt != nil }
            .min { $0.sortIndex < $1.sortIndex }
    }
}
