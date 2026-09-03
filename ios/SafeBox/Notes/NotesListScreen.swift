import SwiftUI

/// Root of the Notes tab's stack. The `NavigationStack` lives in `MainTabView`
/// (its path is owned by `VaultNavigator`, so global search can reset this tab
/// to the list and push an editor); this screen declares the destinations.
struct NotesListScreen: View {
    @State private var viewModel: NotesListViewModel
    let container: AppContainer
    @Environment(UndoCenter.self) private var undoCenter: UndoCenter?
    /// Optional so previews (and any host without `MainTabView` above) render.
    @Environment(VaultNavigator.self) private var navigator: VaultNavigator?

    /// What the single confirm dialog is about: one swiped note, or the whole
    /// selection (P6). One dialog, one Delete, one toast either way.
    private enum PendingDelete {
        case single(Note)
        case selection
    }

    @State private var pendingDelete: PendingDelete?

    init(viewModel: NotesListViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    var body: some View {
        Group {
            if viewModel.visibleNotes.isEmpty {
                // "No notes yet" with a New note action, or "No results"
                // under a query / tag filter — the content decides whether
                // the action button renders (decisions §2).
                EmptyStateView(.forNotes(query: viewModel.searchText,
                                         hasTagFilter: viewModel.filterTag != nil)) {
                    createNote()
                }
            } else {
                notesList
            }
        }
        .navigationTitle(viewModel.isSelecting
                         ? VaultCopy.selectionCount(viewModel.selection.count)
                         : VaultCopy.vaultTabNotes)
        .navigationBarTitleDisplayMode(viewModel.isSelecting ? .inline : .automatic)
        .toolbar { toolbarContent }
        // Browsing only: selection mode keeps exactly the count title, Cancel
        // and Delete (§6). The query survives the round trip.
        .searchableWhileBrowsing(isSelecting: viewModel.isSelecting,
                                 text: Bindable(viewModel).searchText)
        // The Notes stack's destination. The route carries the id, so an
        // editor opened from global search resolves the same way as a tap.
        .navigationDestination(for: NotesRoute.self) { route in
            destination(route)
        }
        .confirmationDialog(deleteDialogTitle,
                            isPresented: Binding(
                                get: { pendingDelete != nil },
                                set: { if !$0 { pendingDelete = nil } }
                            ), titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                switch pendingDelete {
                case .single(let note): deleteNote(note)
                case .selection: deleteSelected()
                case .none: break
                }
                pendingDelete = nil
            }
            Button(VaultCopy.cancelAction, role: .cancel) { pendingDelete = nil }
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
        .onAppear { viewModel.reload() }
    }

    @ViewBuilder
    private func destination(_ route: NotesRoute) -> some View {
        switch route {
        case .note(let noteId):
            if let note = viewModel.repository.note(withId: noteId) {
                NoteEditorScreen(viewModel: NoteEditorViewModel(note: note,
                                                                repository: viewModel.repository),
                                 onDeleted: { deletedId in noteWasDeleted(ids: [deletedId]) })
                    .onDisappear { viewModel.reload() }
            } else {
                // The note was trashed or purged while its route was still on
                // the stack: pop instead of pushing a screen with nothing in
                // it. `onAppear` runs after the update, so the mutation is
                // never made during a view evaluation.
                Color.clear.onAppear { navigator?.dismiss(.notes(route)) }
            }
        }
    }

    /// Pushes onto this tab's path. Rows are not `NavigationLink`s (P6: a link
    /// navigates on any touch-up, which would fight the long-press entry into
    /// selection mode), so navigation is explicit.
    private func open(_ note: Note) {
        navigator?.notesPath.append(.note(note.id))
    }

    // MARK: - List

    private var notesList: some View {
        List {
            ForEach(viewModel.visibleNotes) { note in
                let isSelected = viewModel.selection.contains(note.id)
                HStack(spacing: 12) {
                    if viewModel.isSelecting {
                        SelectionIndicator(isSelected: isSelected)
                    }
                    noteRow(note)
                }
                .selectableListRow(isSelecting: viewModel.isSelecting, isSelected: isSelected,
                                   onTap: {
                                       // Rows never navigate while selecting.
                                       if viewModel.isSelecting {
                                           viewModel.toggleSelection(note)
                                       } else {
                                           open(note)
                                       }
                                   },
                                   onLongPress: {
                                       if !viewModel.isSelecting {
                                           viewModel.enterSelectMode(selecting: note)
                                       }
                                   })
                .swipeActions {
                    // Swipe-to-delete is unavailable while selecting (§6);
                    // otherwise confirmation, then soft delete + undo toast.
                    if !viewModel.isSelecting {
                        Button(VaultCopy.deleteAction, role: .destructive) { pendingDelete = .single(note) }
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private func noteRow(_ note: Note) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(note.title.isEmpty ? NoteDerivation.emptyTitleFallback : note.title)
                .font(.headline)
                .lineLimit(1)
            if !note.snippet.isEmpty {
                Text(note.snippet)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            HStack(spacing: 6) {
                Text(note.updatedAt, format: .relative(presentation: .named))
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                ForEach(note.tags) { tag in
                    TagChip(tag: tag, isSelected: false)
                }
            }
        }
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Toolbar
    //
    // Two shapes, switched on `viewModel.isSelecting`:
    //   selecting  → leading Cancel · trailing Delete (disabled at 0), title
    //                "N selected" (inline)
    //   browsing   → trailing group: search · sort · [tag filter] · compose
    // Everything new goes in the BROWSING trailing group only; selection mode
    // keeps exactly Cancel + Delete.

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        if viewModel.isSelecting {
            ToolbarItem(placement: .topBarLeading) {
                Button(VaultCopy.cancelAction) { viewModel.exitSelectMode() }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) {
                    pendingDelete = .selection
                } label: {
                    Image(systemName: "trash")
                }
                .disabled(viewModel.selection.isEmpty)
                .accessibilityLabel(VaultCopy.deleteAction)
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { navigator?.presentSearch() } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel(VaultCopy.searchTitle)
                SortMenu(selection: viewModel.sort) { viewModel.setSort($0) }
                if !viewModel.allTags.isEmpty {
                    filterMenu
                }
                Button { createNote() } label: { Image(systemName: "square.and.pencil") }
            }
        }
    }

    private var filterMenu: some View {
        Menu {
            Button {
                viewModel.filterTag = nil
            } label: {
                if viewModel.filterTag == nil {
                    Label("All notes", systemImage: "checkmark")
                } else {
                    Text("All notes")
                }
            }
            ForEach(viewModel.allTags) { tag in
                Button {
                    viewModel.filterTag = tag
                } label: {
                    if viewModel.filterTag?.id == tag.id {
                        Label(tag.name, systemImage: "checkmark")
                    } else {
                        Text(tag.name)
                    }
                }
            }
        } label: {
            Image(systemName: viewModel.filterTag == nil
                  ? "line.3.horizontal.decrease.circle"
                  : "line.3.horizontal.decrease.circle.fill")
        }
    }

    private func createNote() {
        if let note = viewModel.createNote() {
            open(note)
        }
    }

    // MARK: - Delete + undo

    /// Title of the single confirm dialog: the swiped note, or the selection
    /// with its count (singular copy when exactly one is selected).
    private var deleteDialogTitle: String {
        switch pendingDelete {
        case .single, .none:
            return VaultCopy.confirmDeleteNote
        case .selection:
            let count = viewModel.selection.count
            return count == 1 ? VaultCopy.confirmDeleteNote : VaultCopy.confirmDeleteNotes(count)
        }
    }

    /// Swipe path: the list performs the soft delete, then posts the toast.
    private func deleteNote(_ note: Note) {
        let id = note.id
        viewModel.delete(note)
        noteWasDeleted(ids: [id])
    }

    /// Bulk path (P6): one repository call for the whole selection, selection
    /// mode ends, one toast whose Undo restores the whole batch.
    private func deleteSelected() {
        let ids = viewModel.deleteSelected()
        guard !ids.isEmpty else { return }
        noteWasDeleted(ids: ids)
    }

    /// Shared tail for every path (swipe, trash button in the editor, bulk):
    /// reload and offer Undo, which restores through this view model.
    private func noteWasDeleted(ids: [UUID]) {
        let viewModel = viewModel
        viewModel.reload()
        let message = ids.count == 1 ? VaultCopy.deletedNote : VaultCopy.deletedNotes(ids.count)
        undoCenter?.post(message: message) {
            viewModel.restore(ids: ids)
        }
    }
}
