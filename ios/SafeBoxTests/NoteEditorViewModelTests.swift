import Foundation
import Testing
@testable import SafeBox

@MainActor
struct NoteEditorViewModelTests {
    private func makeRepository() -> SwiftDataNoteRepository {
        SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
    }

    /// P3 review follow-up: the trash button flushes the on-screen draft
    /// BEFORE the soft delete, so Undo restores exactly what the user saw.
    @Test func deleteFlushesThePendingDraftBeforeTrashingTheNote() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "saved text")
        let viewModel = NoteEditorViewModel(note: note, repository: repo)

        viewModel.draftBody = "saved text plus the last keystrokes"
        viewModel.bodyChanged() // autosave armed, not yet fired
        viewModel.delete()

        #expect(note.body == "saved text plus the last keystrokes")
        #expect(note.deletedAt != nil)
        #expect(viewModel.isDeleted)
        #expect(repo.trashedNotes().map(\.id) == [note.id])

        try repo.restore(ids: [note.id])
        #expect(repo.notes().first?.body == "saved text plus the last keystrokes")
    }

    @Test func flushAfterDeleteIsANoOp() throws {
        let repo = makeRepository()
        let note = try repo.createNote(body: "original")
        let viewModel = NoteEditorViewModel(note: note, repository: repo)
        viewModel.delete()

        viewModel.draftBody = "typed after the delete somehow"
        viewModel.flush() // the onDisappear flush that follows the dismiss
        #expect(note.body == "original")
    }
}
