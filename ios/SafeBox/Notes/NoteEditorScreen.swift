import SwiftUI

struct NoteEditorScreen: View {
    @State private var viewModel: NoteEditorViewModel
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(viewModel: NoteEditorViewModel) {
        _viewModel = State(initialValue: viewModel)
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
            viewModel.flush()
        }
        .confirmationDialog("Delete this note? This cannot be undone.",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                viewModel.delete()
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
