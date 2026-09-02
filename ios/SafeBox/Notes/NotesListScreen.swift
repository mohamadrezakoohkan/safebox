import SwiftUI

struct NotesListScreen: View {
    @State private var viewModel: NotesListViewModel
    let container: AppContainer

    @State private var noteToDelete: Note?
    @State private var editingNote: Note?

    init(viewModel: NotesListViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.notes.isEmpty {
                    ContentUnavailableView {
                        Label("No notes yet", systemImage: "note.text")
                    } actions: {
                        Button("New note") { createNote() }
                            .buttonStyle(.borderedProminent)
                    }
                } else if viewModel.visibleNotes.isEmpty {
                    ContentUnavailableView.search
                } else {
                    notesList
                }
            }
            .navigationTitle("Notes")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if !viewModel.allTags.isEmpty {
                        filterMenu
                    }
                    Button { createNote() } label: { Image(systemName: "square.and.pencil") }
                }
            }
            .searchable(text: Bindable(viewModel).searchText)
            .navigationDestination(item: $editingNote) { note in
                NoteEditorScreen(viewModel: NoteEditorViewModel(note: note,
                                                                repository: viewModel.repository))
                    .onDisappear { viewModel.reload() }
            }
            .confirmationDialog("Delete this note? This cannot be undone.",
                                isPresented: Binding(
                                    get: { noteToDelete != nil },
                                    set: { if !$0 { noteToDelete = nil } }
                                ), titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let note = noteToDelete { viewModel.delete(note) }
                    noteToDelete = nil
                }
                Button("Cancel", role: .cancel) { noteToDelete = nil }
            }
            .onAppear { viewModel.reload() }
        }
    }

    private var notesList: some View {
        List {
            ForEach(viewModel.visibleNotes) { note in
                Button {
                    editingNote = note
                } label: {
                    noteRow(note)
                }
                .buttonStyle(.plain)
                .swipeActions {
                    // Confirmation, no undo (pinned parity decision).
                    Button("Delete", role: .destructive) { noteToDelete = note }
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
        editingNote = viewModel.createNote()
    }
}
