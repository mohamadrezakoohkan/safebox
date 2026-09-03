import Foundation
import Testing
@testable import SafeBox

/// P6 multi-select on the notes list (decisions §6).
@MainActor
struct NotesListViewModelTests {
    private func makeViewModel(seeding bodies: [String]) -> (NotesListViewModel, SpyNoteRepository, [Note]) {
        let repo = SpyNoteRepository()
        let notes = repo.seed(bodies)
        let viewModel = NotesListViewModel(repository: repo)
        viewModel.reload()
        return (viewModel, repo, notes)
    }

    // MARK: - Selection add / remove / clear

    @Test func aFreshViewModelStartsOutsideSelectionMode() {
        // `MainTabView` builds a new view model on every unlock, so this is
        // also the state after a lock: no mode, no selection, by construction.
        let (viewModel, _, _) = makeViewModel(seeding: ["a", "b"])
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
        #expect(viewModel.selectedNotes.isEmpty)
    }

    @Test func longPressEntersSelectionModeWithThePressedRowSelected() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["a", "b", "c"])
        viewModel.enterSelectMode(selecting: notes[1])
        #expect(viewModel.isSelecting)
        #expect(viewModel.selection == [notes[1].id])
        #expect(viewModel.selectedNotes.map(\.id) == [notes[1].id])
    }

    @Test func toggleAddsAndRemovesWhileSelecting() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["a", "b", "c"])
        viewModel.enterSelectMode()
        viewModel.toggleSelection(notes[0])
        viewModel.toggleSelection(notes[2])
        #expect(viewModel.selection == [notes[0].id, notes[2].id])
        viewModel.toggleSelection(notes[0])
        #expect(viewModel.selection == [notes[2].id])
        viewModel.toggleSelection(notes[2])
        #expect(viewModel.selection.isEmpty)
        #expect(viewModel.isSelecting) // an empty selection does not leave the mode
    }

    @Test func toggleIsIgnoredOutsideSelectionMode() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["a"])
        viewModel.toggleSelection(notes[0])
        #expect(viewModel.selection.isEmpty)
        #expect(!viewModel.isSelecting)
    }

    @Test func exitClearsTheSelectionAndLeavesTheMode() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["a", "b"])
        viewModel.enterSelectMode(selecting: notes[0])
        viewModel.toggleSelection(notes[1])
        viewModel.exitSelectMode()
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
        // Re-entering starts from nothing.
        viewModel.enterSelectMode()
        #expect(viewModel.selection.isEmpty)
    }

    @Test func reloadDropsSelectedIdsThatAreNoLongerLive() throws {
        let (viewModel, repo, notes) = makeViewModel(seeding: ["a", "b", "c"])
        viewModel.enterSelectMode(selecting: notes[0])
        viewModel.toggleSelection(notes[1])
        try repo.delete(note: notes[0]) // deleted elsewhere (e.g. the editor)
        viewModel.reload()
        #expect(viewModel.selection == [notes[1].id])
        #expect(viewModel.isSelecting)
    }

    @Test func selectionSurvivesASearchThatHidesTheRow() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["apple", "banana"])
        viewModel.enterSelectMode(selecting: notes[0])
        viewModel.searchText = "banana"
        #expect(viewModel.visibleNotes.map(\.id) == [notes[1].id])
        // The count and the delete target agree: the hidden row is still selected.
        #expect(viewModel.selection == [notes[0].id])
        #expect(viewModel.selectedNotes.map(\.id) == [notes[0].id])
    }

    // MARK: - Bulk delete: one repository call with N ids

    @Test func deleteSelectedCallsTheRepositoryOnceWithEveryId() {
        let (viewModel, repo, notes) = makeViewModel(seeding: ["a", "b", "c", "d", "e"])
        viewModel.enterSelectMode(selecting: notes[0])
        viewModel.toggleSelection(notes[2])
        viewModel.toggleSelection(notes[4])
        let expected: Set<UUID> = [notes[0].id, notes[2].id, notes[4].id]

        let ids = viewModel.deleteSelected()

        #expect(repo.deleteCalls.count == 1)
        #expect(Set(repo.deleteCalls[0]) == expected)
        #expect(Set(ids) == expected)
        // Selection mode ended and the list reloaded without the trashed rows.
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
        #expect(Set(viewModel.notes.map(\.id)) == [notes[1].id, notes[3].id])
        #expect(Set(repo.trashedNotes().map(\.id)) == expected)
    }

    @Test func deleteSelectedWithNothingSelectedIsANoOpThatStillExits() {
        let (viewModel, repo, _) = makeViewModel(seeding: ["a", "b"])
        viewModel.enterSelectMode()
        let ids = viewModel.deleteSelected()
        #expect(ids.isEmpty)
        #expect(repo.deleteCalls.isEmpty)
        #expect(!viewModel.isSelecting)
        #expect(viewModel.notes.count == 2)
    }

    @Test func undoRestoresTheWholeBatchInOneCall() {
        let (viewModel, repo, notes) = makeViewModel(seeding: ["a", "b", "c"])
        viewModel.enterSelectMode(selecting: notes[0])
        viewModel.toggleSelection(notes[1])
        let ids = viewModel.deleteSelected()
        #expect(viewModel.notes.count == 1)

        viewModel.restore(ids: ids) // what the toast's Undo closure calls

        #expect(repo.restoreCalls == [ids])
        #expect(Set(viewModel.notes.map(\.id)) == Set(notes.map(\.id)))
        #expect(repo.trashedNotes().isEmpty)
    }

    @Test func bulkDeleteAgainstSwiftDataMovesEveryNoteToTheTrashWithOneStamp() throws {
        let repo = SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
        let a = try repo.createNote(body: "a")
        let b = try repo.createNote(body: "b")
        let c = try repo.createNote(body: "c")
        let viewModel = NotesListViewModel(repository: repo)
        viewModel.reload()
        viewModel.enterSelectMode(selecting: a)
        viewModel.toggleSelection(c)

        let ids = viewModel.deleteSelected()

        #expect(Set(ids) == [a.id, c.id])
        #expect(viewModel.notes.map(\.id) == [b.id])
        let trashed = repo.trashedNotes()
        #expect(Set(trashed.map(\.id)) == [a.id, c.id])
        #expect(Set(trashed.compactMap(\.deletedAt)).count == 1) // one call, one stamp
    }

    // MARK: - Per-tab search uses the shared fold (N1)

    @Test func theNotesSearchIsDiacriticInsensitive() {
        // Before N1 this search was case-insensitive only, so "cafe" missed a
        // note about a "Café". Per-tab and global search now share `SearchFold`.
        let (viewModel, _, notes) = makeViewModel(seeding: ["Café au lait", "tea"])
        viewModel.searchText = "cafe"
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])
    }

    @Test func theNotesSearchMatchesAnAccentedQueryAgainstPlainText() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["Cafe au lait", "tea"])
        viewModel.searchText = "café"
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])
    }

    @Test func theNotesSearchStaysCaseInsensitiveAndTrimsTheQuery() {
        let (viewModel, _, notes) = makeViewModel(seeding: ["Groceries\nmilk", "tea"])
        viewModel.searchText = "  MILK "
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])
        viewModel.searchText = "   "
        #expect(viewModel.visibleNotes.count == 2) // whitespace-only is not a search
    }

    // MARK: - Search field vs. selection mode (P6 review polish)

    @Test func theSearchFieldIsAttachedOnlyWhileBrowsing() {
        // Decisions §6: selection mode shows exactly the count title, Cancel
        // and Delete — no search field over a selection.
        #expect(SelectionSearchPolicy.showsSearchField(isSelecting: false))
        #expect(!SelectionSearchPolicy.showsSearchField(isSelecting: true))
    }

    @Test func enteringAndLeavingSelectionModeKeepsTheTypedQuery() {
        // The field goes away while selecting, but the query is view-model
        // state: it must come back with the field, and the visible rows must
        // stay filtered the same way throughout.
        let (viewModel, _, notes) = makeViewModel(seeding: ["Groceries\nmilk", "tea"])
        viewModel.searchText = "milk"
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])

        viewModel.enterSelectMode(selecting: notes[0])
        #expect(viewModel.searchText == "milk")
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])

        viewModel.exitSelectMode()
        #expect(viewModel.searchText == "milk")
        #expect(viewModel.visibleNotes.map(\.id) == [notes[0].id])
    }
}
