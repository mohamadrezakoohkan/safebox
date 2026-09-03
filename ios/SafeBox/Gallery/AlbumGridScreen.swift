import SwiftUI
import PhotosUI

struct AlbumGridScreen: View {
    @State private var viewModel: AlbumGridViewModel
    let container: AppContainer
    @Environment(UndoCenter.self) private var undoCenter: UndoCenter?

    @State private var showPicker = false
    @State private var pickedItems: [PhotosPickerItem] = []
    @State private var confirmDelete = false
    @State private var pagerStart: Photo?

    init(viewModel: AlbumGridViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    private let columns = [
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2),
    ]

    var body: some View {
        Group {
            if viewModel.photos.isEmpty && !viewModel.importer.isImporting {
                EmptyStateView(.noPhotos) { presentPicker() }
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 2) {
                        ForEach(viewModel.photos) { photo in
                            gridCell(photo)
                        }
                    }
                }
            }
        }
        .navigationTitle(viewModel.album.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
        // While the album is still empty the pill sits centered in the empty
        // grid area (decisions §2); once photos land it drops to the bottom.
        .overlay(alignment: viewModel.photos.isEmpty ? .center : .bottom) {
            if viewModel.importer.isImporting {
                importProgress
            }
        }
        .animation(.default, value: viewModel.photos.isEmpty)
        // Mixed media (N3): videos arrive as files, never as Data.
        .photosPicker(isPresented: $showPicker, selection: $pickedItems,
                      matching: .any(of: [.images, .videos]), photoLibrary: .shared())
        .onChange(of: showPicker) { _, presented in
            if !presented {
                let items = pickedItems
                pickedItems = []
                Task {
                    let failedVideos = await viewModel.importPicked(items)
                    // Progress stays item-count based (decisions §9); a video
                    // that could not be imported gets its own visible notice.
                    if failedVideos > 0 {
                        undoCenter?.postNotice(message: VaultCopy.videoImportFailed)
                    }
                    // Clear suppression after the round-trip fully completes.
                    container.lockCoordinator.systemUIDidDismiss()
                }
            }
        }
        .confirmationDialog(deleteSelectionTitle, isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                deletePhotos(viewModel.selectedPhotos)
            }
            Button(VaultCopy.cancelAction, role: .cancel) {}
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
        .navigationDestination(item: $pagerStart) { photo in
            PhotoPagerScreen(
                photos: viewModel.photos,
                startPhoto: photo,
                fileStore: container.photoFileStore,
                otherAlbums: viewModel.otherAlbums,
                onDelete: { p in deletePhotos([p]) },
                onMove: { p, album in viewModel.move(p, to: album) }
            )
        }
        .onAppear { viewModel.reload() }
    }

    private var deleteSelectionTitle: String {
        let count = viewModel.selection.count
        return count == 1 ? VaultCopy.confirmDeletePhoto : VaultCopy.confirmDeletePhotos(count)
    }

    /// Soft delete + undo toast, shared by the grid selection and the pager.
    /// The closure carries ids only and restores through the view model so the
    /// grid reloads (the pager mirrors its own list and does not re-insert).
    private func deletePhotos(_ photos: [Photo]) {
        let viewModel = viewModel
        let ids = viewModel.delete(photos)
        if viewModel.isSelecting { viewModel.exitSelectMode() }
        guard !ids.isEmpty else { return }
        let message = ids.count == 1 ? VaultCopy.deletedPhoto : VaultCopy.deletedPhotos(ids.count)
        undoCenter?.post(message: message) {
            viewModel.restorePhotos(ids: ids)
        }
    }

    private func presentPicker() {
        // Suppress auto-lock for the app-initiated picker round-trip.
        container.lockCoordinator.systemUIWillPresent()
        showPicker = true
    }

    private func gridCell(_ photo: Photo) -> some View {
        Button {
            if viewModel.isSelecting {
                viewModel.toggleSelection(photo)
            } else {
                pagerStart = photo
            }
        } label: {
            PhotoThumbnailView(photo: photo, fileStore: container.photoFileStore)
                .aspectRatio(1, contentMode: .fill)
                // Play glyph (centered) + duration pill (bottom-leading); the
                // selection indicator below stays bottom-trailing.
                .overlay {
                    if photo.isVideo {
                        VideoCellOverlay(durationMs: photo.durationMs)
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    if viewModel.isSelecting {
                        Image(systemName: viewModel.selection.contains(photo.id)
                              ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(.white)
                            .shadow(radius: 2)
                            .padding(6)
                    }
                }
        }
        .buttonStyle(.plain)
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            if viewModel.isSelecting {
                if !viewModel.selection.isEmpty {
                    Menu {
                        ForEach(viewModel.otherAlbums) { album in
                            Button(album.name) { viewModel.moveSelected(to: album) }
                        }
                    } label: {
                        Image(systemName: "folder")
                    }
                    Button(role: .destructive) {
                        confirmDelete = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
                Button(VaultCopy.cancelAction) { viewModel.exitSelectMode() }
            } else {
                if !viewModel.photos.isEmpty {
                    Button(VaultCopy.selectAction) { viewModel.isSelecting = true }
                }
                Button { presentPicker() } label: { Image(systemName: "plus") }
            }
        }
    }

    private var importProgress: some View {
        HStack(spacing: 10) {
            ProgressView()
            Text(VaultCopy.importProgress(done: viewModel.importer.completedCount,
                                          total: viewModel.importer.totalCount))
                .font(.callout)
        }
        .padding(12)
        .background(.regularMaterial, in: Capsule())
        .padding(.bottom, viewModel.photos.isEmpty ? 0 : 24)
        .task(id: viewModel.importer.completedCount) {
            viewModel.reload()
        }
    }
}
