import Foundation
import Observation

@MainActor
@Observable
final class NotesListViewModel {
    let repository: any NoteRepository
    private let defaults: UserDefaults

    private(set) var notes: [Note] = []
    private(set) var allTags: [Tag] = []
    var searchText = ""
    var filterTag: Tag?

    /// The persisted sort mode (decisions §4). Read from UserDefaults at init —
    /// this object is rebuilt on every unlock, so the choice has to outlive it.
    private(set) var sort: NoteSort

    // MARK: Selection (P6) — mirrors `AlbumGridViewModel`.
    //
    // Owned by this object, which `MainTabView` creates and which is torn down
    // with the vault on lock, so selection resets on lock by construction; the
    // only in-life reset is `exitSelectMode()`.

    private(set) var isSelecting = false
    private(set) var selection: Set<UUID> = []

    init(repository: any NoteRepository, defaults: UserDefaults = .standard) {
        self.repository = repository
        self.defaults = defaults
        self.sort = SortPreferences.noteSort(defaults: defaults)
    }

    /// Persists the choice first, then reorders — so a lock (or a crash) right
    /// after the tap still comes back in the order the user picked.
    func setSort(_ newSort: NoteSort) {
        guard newSort != sort else { return }
        sort = newSort
        SortPreferences.setNoteSort(newSort, defaults: defaults)
        reload()
    }

    func reload() {
        notes = repository.notes(sortedBy: sort)
        allTags = repository.tags()
        // Ids that are no longer live (deleted elsewhere) drop out so the
        // "N selected" title and the bulk delete never disagree.
        if !selection.isEmpty {
            selection.formIntersection(notes.map(\.id))
        }
    }

    /// Keeps the repository order (the chosen sort mode); adds a contains-match
    /// search over title + body and the optional tag filter.
    ///
    /// Matching goes through `SearchFold` — the vault's single fold (N1), so
    /// this per-tab search and global search can never disagree. It used to be
    /// case-insensitive only, which made "cafe" miss a note about a "Café".
    var visibleNotes: [Note] {
        var result = notes
        if let filterTag {
            result = result.filter { note in note.tags.contains { $0.id == filterTag.id } }
        }
        let folded = SearchFold.foldedQuery(searchText)
        if !folded.isEmpty {
            result = result.filter {
                SearchFold.foldedContainsAny([$0.title, $0.body], foldedQuery: folded)
            }
        }
        return result
    }

    func createNote() -> Note? {
        let note = try? repository.createNote(body: "")
        reload()
        return note
    }

    /// Soft delete (the note goes to Recently deleted).
    func delete(_ note: Note) {
        try? repository.delete(note: note)
        reload()
    }

    /// Undo path — restores a whole batch in one call.
    func restore(ids: [UUID]) {
        try? repository.restore(ids: ids)
        reload()
    }

    // MARK: - Selection

    /// The selected LIVE notes (a selected note hidden by the current search
    /// or tag filter is still selected — the count and the delete agree).
    var selectedNotes: [Note] {
        notes.filter { selection.contains($0.id) }
    }

    /// Long-press entry: enters selection mode with the pressed row selected.
    func enterSelectMode(selecting note: Note? = nil) {
        isSelecting = true
        if let note {
            selection.insert(note.id)
        }
    }

    func toggleSelection(_ note: Note) {
        guard isSelecting else { return }
        if selection.contains(note.id) {
            selection.remove(note.id)
        } else {
            selection.insert(note.id)
        }
    }

    /// Cancel: leaves selection mode and clears the selection.
    func exitSelectMode() {
        isSelecting = false
        selection = []
    }

    /// Bulk soft delete: ONE repository call carrying every selected id
    /// (decisions §6), then selection mode ends. Returns the trashed ids for
    /// the undo toast; empty when nothing was selected.
    @discardableResult
    func deleteSelected() -> [UUID] {
        let targets = selectedNotes
        exitSelectMode()
        guard !targets.isEmpty else { return [] }
        let ids = targets.map(\.id)
        try? repository.delete(notes: targets)
        reload()
        return ids
    }
}
