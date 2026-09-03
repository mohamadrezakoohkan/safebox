import Foundation
@testable import SafeBox

/// In-memory `NoteRepository` that records every call, so view-model tests can
/// assert "one bulk call with N ids" without SwiftData. Models are created
/// un-inserted (`Note(body:)`); only their scalar columns are read.
@MainActor
final class SpyNoteRepository: NoteRepository {
    var liveNotes: [Note] = []
    var trashed: [Note] = []
    var allTags: [Tag] = []

    /// One entry per `delete(notes:)` call, each carrying the ids passed.
    private(set) var deleteCalls: [[UUID]] = []
    /// One entry per `restore(ids:)` call.
    private(set) var restoreCalls: [[UUID]] = []

    @discardableResult
    func seed(_ bodies: [String]) -> [Note] {
        let notes = bodies.map { Note(body: $0) }
        liveNotes.append(contentsOf: notes)
        return notes
    }

    /// One entry per `notes(sortedBy:)` call, so a view-model test can assert
    /// which mode was asked for.
    private(set) var sortsRequested: [NoteSort] = []

    /// Returns the live notes in seed order and only RECORDS the mode: the
    /// ordering itself belongs to the real repository and is covered by
    /// `VaultSortingTests` / `NoteRepositoryTests`, and keeping seed order here
    /// keeps the selection tests readable.
    func notes(sortedBy sort: NoteSort) -> [Note] {
        sortsRequested.append(sort)
        return liveNotes
    }

    /// Live rows only, like the real repository: a trashed id resolves to nil
    /// so a stale route pops instead of pushing an empty screen.
    func note(withId id: UUID) -> Note? {
        liveNotes.first { $0.id == id }
    }

    @discardableResult
    func createNote(body: String) throws -> Note {
        let note = Note(body: body)
        liveNotes.insert(note, at: 0)
        return note
    }

    func save(note: Note, body: String) throws {
        note.body = body
    }

    func delete(note: Note) throws {
        try delete(notes: [note])
    }

    func delete(notes: [Note]) throws {
        deleteCalls.append(notes.map(\.id))
        let ids = Set(notes.map(\.id))
        let stamp = Date.now
        for note in liveNotes where ids.contains(note.id) {
            note.deletedAt = stamp
            trashed.append(note)
        }
        liveNotes.removeAll { ids.contains($0.id) }
    }

    func tags() -> [Tag] { allTags }

    @discardableResult
    func findOrCreateTag(named name: String) throws -> Tag {
        if let existing = allTags.first(where: { $0.name == name }) { return existing }
        let tag = Tag(name: name, colorIndex: allTags.count % 6)
        allTags.append(tag)
        return tag
    }

    func setTags(_ tags: [Tag], on note: Note) throws {}

    func trashedNotes() -> [Note] { trashed }

    func restore(ids: [UUID]) throws {
        restoreCalls.append(ids)
        let wanted = Set(ids)
        for note in trashed where wanted.contains(note.id) {
            note.deletedAt = nil
            liveNotes.append(note)
        }
        trashed.removeAll { wanted.contains($0.id) }
    }

    func purge(ids: [UUID]) throws {
        let wanted = Set(ids)
        trashed.removeAll { wanted.contains($0.id) }
    }

    func purgeExpired(now: Date) {}
}
