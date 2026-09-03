import SwiftUI

struct NoteEditorScreen: View {
    @State private var viewModel: NoteEditorViewModel
    /// Called with the note's id after the trash button soft-deletes it; the
    /// list screen posts the undo toast from here.
    let onDeleted: (UUID) -> Void
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(viewModel: NoteEditorViewModel, onDeleted: @escaping (UUID) -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onDeleted = onDeleted
    }

    var body: some View {
        VStack(spacing: 0) {
            TagChipsView(viewModel: viewModel)
            Divider()
            if viewModel.showPreview {
                MarkdownPreview(markdown: viewModel.draftBody)
            } else {
                TextEditor(text: Bindable(viewModel).draftBody)
                    .font(.body)
                    .padding(.horizontal, 12)
                    .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(viewModel.note.title.isEmpty ? NoteDerivation.emptyTitleFallback : viewModel.note.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    viewModel.flush()
                    viewModel.showPreview.toggle()
                } label: {
                    Image(systemName: viewModel.showPreview ? "pencil" : "eye")
                }
                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Image(systemName: "trash")
                }
            }
        }
        .onChange(of: viewModel.draftBody) { _, _ in
            viewModel.bodyChanged()
        }
        .onChange(of: scenePhase) { _, phase in
            // Mandatory synchronous flush — a debounced write racing the
            // device lock could otherwise be lost.
            if phase != .active { viewModel.flush() }
        }
        .onDisappear {
            // No-op after delete: the view model refuses to flush a trashed note.
            viewModel.flush()
        }
        .confirmationDialog(VaultCopy.confirmDeleteNote,
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                let id = viewModel.note.id
                viewModel.delete()
                onDeleted(id)
                dismiss()
            }
            Button(VaultCopy.cancelAction, role: .cancel) {}
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
    }
}
