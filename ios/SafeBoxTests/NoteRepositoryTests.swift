import Foundation
import Testing
@testable import SafeBox

@MainActor
struct NoteRepositoryTests {
    private func makeRepository() -> SwiftDataNoteRepository {
        SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
    }

    @Test func createAndFetchSortedByUpdatedAt() throws {
        let repo = makeRepository()
        let first = try repo.createNote(body: "first")
        let second = try repo.createNote(body: "second")
        try repo.save(note: second, body: "second updated")
        let notes = repo.notes()
        #expect(notes.count == 2)
        #expect(notes.first?.id == second.id)
        _ = first
    }

    @Test func saveRecomputesDerivedFields() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "")
        try repo.save(note: note, body: "# Groceries\n- milk\n- bread")
        #expect(note.title == "Groceries")
        #expect(note.snippet == "milk bread")
        #expect(note.body == "# Groceries\n- milk\n- bread")
    }

    @Test func updatedAtBumpsOnlyOnRealChange() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "hello")
        let stamp = note.updatedAt
        try repo.save(note: note, body: "hello") // no change
        #expect(note.updatedAt == stamp)
        try repo.save(note: note, body: "hello world")
        #expect(note.updatedAt > stamp)
    }

    // MARK: - Soft delete (P3). Replaces iteration-1 `deleteRemovesNote`:
    // delete now moves the note to the trash; purge removes the row.

    @Test func deleteIsSoftAndHidesTheNoteFromTheList() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "gone")
        try repo.delete(note: note)
        #expect(repo.notes().isEmpty)
        #expect(repo.trashedNotes().map(\.id) == [note.id])
        #expect(note.deletedAt != nil)
    }

    @Test func restoreBringsTheNoteBackAndPurgeRemovesIt() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "# Keep me")
        try repo.delete(note: note)

        try repo.restore(ids: [note.id])
        #expect(repo.notes().map(\.id) == [note.id])
        #expect(repo.trashedNotes().isEmpty)
        #expect(note.deletedAt == nil)
        #expect(note.title == "Keep me")

        try repo.delete(note: note)
        try repo.purge(ids: [note.id])
        #expect(repo.notes().isEmpty)
        #expect(repo.trashedNotes().isEmpty)
    }

    @Test func bulkDeleteTrashesEveryNoteWithOneStamp() throws {
        let repo = makeRepository()
        let a = try repo.createNote(body: "a")
        let b = try repo.createNote(body: "b")
        let c = try repo.createNote(body: "c")
        try repo.delete(notes: [a, b])
        #expect(repo.notes().map(\.id) == [c.id])
        #expect(Set(repo.trashedNotes().map(\.id)) == [a.id, b.id])
        #expect(a.deletedAt == b.deletedAt)
    }

    @Test func purgeExpiredRemovesOnlyExpiredNotes() throws {
        let repo = makeRepository()
        let expired = try repo.createNote(body: "old")
        let fresh = try repo.createNote(body: "new")
        try repo.delete(notes: [expired, fresh])
        expired.deletedAt = Date.now.addingTimeInterval(-31 * 86_400)

        repo.purgeExpired(now: .now)

        #expect(repo.trashedNotes().map(\.id) == [fresh.id])
        #expect(repo.notes().isEmpty)
    }

    // MARK: - Tags

    @Test func tagCreationDedupesCaseInsensitive() throws {
        let repo = makeRepository()
        let a = try repo.findOrCreateTag(named: "Work")
        let b = try repo.findOrCreateTag(named: "work")
        let c = try repo.findOrCreateTag(named: " WORK ")
        #expect(a.id == b.id)
        #expect(a.id == c.id)
        #expect(repo.tags().count == 1)
    }

    @Test func tagAssignmentAndRemoval() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "tagged")
        let tag = try repo.findOrCreateTag(named: "personal")
        try repo.setTags([tag], on: note)
        #expect(note.tags.count == 1)
        try repo.setTags([], on: note)
        #expect(note.tags.isEmpty)
        // A tag with zero notes is kept.
        #expect(repo.tags().count == 1)
    }

    /// Rewritten for P3: a soft-deleted note keeps its tags (so a restore brings
    /// them back); the tag itself survives both the soft delete and the purge.
    @Test func deletingNoteKeepsTags() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "note")
        let tag = try repo.findOrCreateTag(named: "keepme")
        try repo.setTags([tag], on: note)

        try repo.delete(note: note)
        #expect(repo.tags().count == 1)
        #expect(note.tags.map(\.id) == [tag.id])               // relation kept for restore

        try repo.restore(ids: [note.id])
        #expect(note.tags.map(\.id) == [tag.id])

        try repo.delete(note: note)
        try repo.purge(ids: [note.id])
        #expect(repo.tags().count == 1)
        #expect(repo.tags().first?.notes.isEmpty == true)
    }

    /// A `NotesRoute` can outlive the note it points at (Delete now in Recently
    /// deleted, expiry, Erase everything). The by-id lookup is LIVE-only so the
    /// destination resolves to nil and the stack pops.
    @Test func lookupByIdSkipsTrashedNotesAndReturnsThemAfterRestore() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "routed")
        #expect(repo.note(withId: note.id)?.id == note.id)

        try repo.delete(note: note)
        #expect(repo.note(withId: note.id) == nil)

        try repo.restore(ids: [note.id])
        #expect(repo.note(withId: note.id)?.id == note.id)

        try repo.delete(note: note)
        try repo.purge(ids: [note.id])
        #expect(repo.note(withId: note.id) == nil)
        #expect(repo.note(withId: UUID()) == nil)
    }
}
