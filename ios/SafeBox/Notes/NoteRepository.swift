import Foundation
import SwiftData

/// Notes and tags. Since P3 `delete` is a SOFT delete (`deletedAt = now`);
/// `notes(sortedBy:)` returns live rows only. Tags are never trashed.
@MainActor
protocol NoteRepository: AnyObject {
    /// Live notes in the requested order (decisions §4). Sorting happens here,
    /// on the fetched list — never in a view body.
    func notes(sortedBy sort: NoteSort) -> [Note]
    /// By-id lookup for a navigation route (N1): a path carries ids, never
    /// models, so the destination re-fetches. `nil` once the row is gone.
    func note(withId id: UUID) -> Note?
    @discardableResult
    func createNote(body: String) throws -> Note
    func save(note: Note, body: String) throws
    func delete(note: Note) throws
    /// Bulk soft delete in one call (P6 multi-select).
    func delete(notes: [Note]) throws
    func tags() -> [Tag]
    @discardableResult
    func findOrCreateTag(named name: String) throws -> Tag
    func setTags(_ tags: [Tag], on note: Note) throws

    // MARK: Trash (P3)

    func trashedNotes() -> [Note]
    func restore(ids: [UUID]) throws
    /// Hard delete of the rows (notes own no files).
    func purge(ids: [UUID]) throws
    func purgeExpired(now: Date)
}

extension NoteRepository {
    /// Notes in the default (date_modified, newest first) order.
    func notes() -> [Note] { notes(sortedBy: .dateModified) }
}

@MainActor
final class SwiftDataNoteRepository: NoteRepository {
    // The container must be retained: ModelContext references it weakly, and a
    // deallocated container traps on the first model operation.
    private let container: ModelContainer
    private let context: ModelContext

    init(container: ModelContainer) {
        self.container = container
        self.context = container.mainContext
    }

    func notes(sortedBy sort: NoteSort) -> [Note] {
        let descriptor = FetchDescriptor<Note>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
        )
        let live = (try? context.fetch(descriptor)) ?? []
        return VaultSorting.sorted(live, by: sort)
    }

    /// LIVE rows only: a route can outlive the note it points at (Delete now in
    /// Recently deleted, expiry, Erase everything), and a trashed note must not
    /// render as a pushed editor.
    func note(withId id: UUID) -> Note? {
        var descriptor = FetchDescriptor<Note>(
            predicate: #Predicate { $0.id == id && $0.deletedAt == nil }
        )
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first
    }

    @discardableResult
    func createNote(body: String = "") throws -> Note {
        let note = Note(body: body)
        context.insert(note)
        try context.save()
        return note
    }

    func save(note: Note, body: String) throws {
        guard note.body != body else { return }
        note.body = body
        let derived = NoteDerivation.derive(from: body)
        note.title = derived.title
        note.snippet = derived.snippet
        note.updatedAt = .now // bumps only on real change (guarded above)
        try context.save()
    }

    func delete(note: Note) throws {
        try delete(notes: [note])
    }

    func delete(notes: [Note]) throws {
        let stamp = Date.now
        for note in notes {
            note.deletedAt = stamp
        }
        try context.save()
    }

    func tags() -> [Tag] {
        let descriptor = FetchDescriptor<Tag>(sortBy: [SortDescriptor(\.name)])
        return (try? context.fetch(descriptor)) ?? []
    }

    @discardableResult
    func findOrCreateTag(named name: String) throws -> Tag {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        // Dedupe by case-insensitive name.
        if let existing = tags().first(where: { $0.name.caseInsensitiveCompare(trimmed) == .orderedSame }) {
            return existing
        }
        let colorIndex = tags().count % 6
        let tag = Tag(name: trimmed, colorIndex: colorIndex)
        context.insert(tag)
        try context.save()
        return tag
    }

    func setTags(_ tags: [Tag], on note: Note) throws {
        note.tags = tags
        try context.save()
    }

    // MARK: - Trash

    func trashedNotes() -> [Note] {
        let descriptor = FetchDescriptor<Note>(predicate: #Predicate { $0.deletedAt != nil })
        return (try? context.fetch(descriptor)) ?? []
    }

    func restore(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for note in fetch(ids: ids) {
            note.deletedAt = nil
        }
        try context.save()
    }

    func purge(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for note in fetch(ids: ids) {
            context.delete(note)
        }
        try context.save()
    }

    func purgeExpired(now: Date) {
        let expired = trashedNotes()
            .filter { TrashPolicy.isExpired(deletedAt: $0.deletedAt ?? now, now: now) }
            .map(\.id)
        try? purge(ids: expired)
    }

    private func fetch(ids: [UUID]) -> [Note] {
        let descriptor = FetchDescriptor<Note>(predicate: #Predicate { ids.contains($0.id) })
        return (try? context.fetch(descriptor)) ?? []
    }
}
