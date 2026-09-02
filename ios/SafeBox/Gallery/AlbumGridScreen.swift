import SwiftUI
import PhotosUI

struct AlbumGridScreen: View {
    @State private var viewModel: AlbumGridViewModel
    let container: AppContainer

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
                ContentUnavailableView {
                    Label("No photos yet", systemImage: "photo")
                } description: {
                    Text("Copied into SafeBox — you can remove the originals in Photos.")
                } actions: {
                    Button("Import photos") { presentPicker() }
                        .buttonStyle(.borderedProminent)
                }
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
        .overlay(alignment: .bottom) {
            if viewModel.importer.isImporting {
                importProgress
            }
        }
        .photosPicker(isPresented: $showPicker, selection: $pickedItems,
                      matching: .images, photoLibrary: .shared())
        .onChange(of: showPicker) { _, presented in
            if !presented {
                let items = pickedItems
                pickedItems = []
                Task {
                    await viewModel.importPicked(items)
                    // Clear suppression after the round-trip fully completes.
                    container.lockCoordinator.systemUIDidDismiss()
                }
            }
        }
        .confirmationDialog("Delete \(viewModel.selection.count) photos? This cannot be undone.",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                Task { await viewModel.deleteSelected() }
            }
            Button("Cancel", role: .cancel) {}
        }
        .navigationDestination(item: $pagerStart) { photo in
            PhotoPagerScreen(
                photos: viewModel.photos,
                startPhoto: photo,
                fileStore: container.photoFileStore,
                otherAlbums: viewModel.otherAlbums,
                onDelete: { p in Task { await viewModel.delete(p) } },
                onMove: { p, album in viewModel.move(p, to: album) }
            )
        }
        .onAppear { viewModel.reload() }
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
                Button("Cancel") { viewModel.exitSelectMode() }
            } else {
                if !viewModel.photos.isEmpty {
                    Button("Select") { viewModel.isSelecting = true }
                }
                Button { presentPicker() } label: { Image(systemName: "plus") }
            }
        }
    }

    private var importProgress: some View {
        HStack(spacing: 10) {
            ProgressView()
            Text("Importing \(viewModel.importer.completedCount)/\(viewModel.importer.totalCount)…")
                .font(.callout)
        }
        .padding(12)
        .background(.regularMaterial, in: Capsule())
        .padding(.bottom, 24)
        .task(id: viewModel.importer.completedCount) {
            viewModel.reload()
        }
    }
}
