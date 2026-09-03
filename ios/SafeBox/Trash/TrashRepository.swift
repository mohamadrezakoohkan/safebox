import Foundation

/// Retention rules for "Recently deleted" (decisions §3, §11): items stay for
/// 30 days from their `deletedAt` stamp, then are purged (rows AND files) at
/// app start and on every transition to Unlocked.
enum TrashPolicy {
    static let retentionDays = 30
    static var retention: TimeInterval { TimeInterval(retentionDays) * 86_400 }

    static func expiryDate(deletedAt: Date) -> Date {
        deletedAt.addingTimeInterval(retention)
    }

    static func isExpired(deletedAt: Date, now: Date) -> Bool {
        expiryDate(deletedAt: deletedAt) <= now
    }

    /// Whole days until expiry, rounded up (a freshly trashed item reads
    /// "30 days left"; anything past expiry reads 0).
    static func daysLeft(deletedAt: Date, now: Date) -> Int {
        let remaining = expiryDate(deletedAt: deletedAt).timeIntervalSince(now)
        guard remaining > 0 else { return 0 }
        return Int((remaining / 86_400).rounded(.up))
    }
}

enum TrashItemKind: Hashable, Sendable {
    case album
    case photo
    case note
    case contact
}

/// Type-tagged id of one trashed row. Undo closures and the trash screen carry
/// these instead of model objects, so nothing captures a `@Model` reference.
struct TrashItemID: Hashable, Sendable {
    let kind: TrashItemKind
    let id: UUID
}

/// Everything currently in the trash, grouped by type, most recently deleted
/// first. `photos` holds only photos whose album is live (or missing): photos
/// trashed together with their album are represented by the album row.
struct TrashContents {
    var albums: [Album] = []
    var photos: [Photo] = []
    var notes: [Note] = []
    var contacts: [Contact] = []

    var isEmpty: Bool {
        albums.isEmpty && photos.isEmpty && notes.isEmpty && contacts.isEmpty
    }

    /// Drops one row from the grouped contents. The trash view model prunes
    /// its copy with this BEFORE awaiting a purge, so a body re-evaluation
    /// during the await can never read a deleted-and-saved `@Model`.
    mutating func remove(_ item: TrashItemID) {
        switch item.kind {
        case .album: albums.removeAll { $0.id == item.id }
        case .photo: photos.removeAll { $0.id == item.id }
        case .note: notes.removeAll { $0.id == item.id }
        case .contact: contacts.removeAll { $0.id == item.id }
        }
    }
}

@MainActor
protocol TrashRepository: AnyObject {
    func contents() -> TrashContents
    func restore(_ items: [TrashItemID]) throws
    /// Hard delete: rows and, for photos/albums, both files.
    func purge(_ items: [TrashItemID]) async throws
    func emptyAll() async throws
    func purgeExpired(now: Date) async
}

/// Composes the three entity repositories: they own the SwiftData fetches and
/// the file deletes; this type only groups, routes by kind, and applies the
/// "photo under a trashed album is shown under the album" rule.
@MainActor
final class SwiftDataTrashRepository: TrashRepository {
    private let photoRepository: any PhotoRepository
    private let noteRepository: any NoteRepository
    private let contactRepository: any ContactRepository

    init(photoRepository: any PhotoRepository,
         noteRepository: any NoteRepository,
         contactRepository: any ContactRepository) {
        self.photoRepository = photoRepository
        self.noteRepository = noteRepository
        self.contactRepository = contactRepository
    }

    func contents() -> TrashContents {
        TrashContents(
            albums: photoRepository.trashedAlbums().sorted(by: Self.mostRecentFirst),
            photos: photoRepository.trashedPhotos()
                .filter { $0.album?.deletedAt == nil }
                .sorted(by: Self.mostRecentFirst),
            notes: noteRepository.trashedNotes().sorted(by: Self.mostRecentFirst),
            contacts: contactRepository.trashedContacts().sorted(by: Self.mostRecentFirst)
        )
    }

    func restore(_ items: [TrashItemID]) throws {
        let grouped = Self.group(items)
        try photoRepository.restoreAlbums(ids: grouped[.album] ?? [])
        try photoRepository.restorePhotos(ids: grouped[.photo] ?? [])
        try noteRepository.restore(ids: grouped[.note] ?? [])
        try contactRepository.restore(ids: grouped[.contact] ?? [])
    }

    func purge(_ items: [TrashItemID]) async throws {
        let grouped = Self.group(items)
        try await photoRepository.purgeAlbums(ids: grouped[.album] ?? [])
        try await photoRepository.purgePhotos(ids: grouped[.photo] ?? [])
        try noteRepository.purge(ids: grouped[.note] ?? [])
        try contactRepository.purge(ids: grouped[.contact] ?? [])
    }

    func emptyAll() async throws {
        // Albums first (they take their photos with them), then whatever
        // trashed photos remain — re-fetched, never reused across the purge.
        try await photoRepository.purgeAlbums(ids: photoRepository.trashedAlbums().map(\.id))
        try await photoRepository.purgePhotos(ids: photoRepository.trashedPhotos().map(\.id))
        try noteRepository.purge(ids: noteRepository.trashedNotes().map(\.id))
        try contactRepository.purge(ids: contactRepository.trashedContacts().map(\.id))
    }

    func purgeExpired(now: Date) async {
        await photoRepository.purgeExpired(now: now)
        noteRepository.purgeExpired(now: now)
        contactRepository.purgeExpired(now: now)
    }

    // MARK: - Helpers

    private static func group(_ items: [TrashItemID]) -> [TrashItemKind: [UUID]] {
        var result: [TrashItemKind: [UUID]] = [:]
        for item in items {
            result[item.kind, default: []].append(item.id)
        }
        return result
    }

    private static func mostRecentFirst(_ a: Album, _ b: Album) -> Bool {
        (a.deletedAt ?? .distantPast) > (b.deletedAt ?? .distantPast)
    }

    private static func mostRecentFirst(_ a: Photo, _ b: Photo) -> Bool {
        (a.deletedAt ?? .distantPast) > (b.deletedAt ?? .distantPast)
    }

    private static func mostRecentFirst(_ a: Note, _ b: Note) -> Bool {
        (a.deletedAt ?? .distantPast) > (b.deletedAt ?? .distantPast)
    }

    private static func mostRecentFirst(_ a: Contact, _ b: Contact) -> Bool {
        (a.deletedAt ?? .distantPast) > (b.deletedAt ?? .distantPast)
    }
}
