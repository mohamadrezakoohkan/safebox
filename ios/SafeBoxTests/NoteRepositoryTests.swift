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

    @Test func deleteRemovesNote() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "gone")
        try repo.delete(note: note)
        #expect(repo.notes().isEmpty)
    }

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

    @Test func deletingNoteKeepsTags() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "note")
        let tag = try repo.findOrCreateTag(named: "keepme")
        try repo.setTags([tag], on: note)
        try repo.delete(note: note)
        #expect(repo.tags().count == 1)
        #expect(repo.tags().first?.notes.isEmpty == true)
    }
}
