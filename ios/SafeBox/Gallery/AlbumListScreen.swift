import SwiftUI

/// Album card grid: cover thumbnail (derived), name, photo count.
///
/// Root of the Gallery tab's stack. The `NavigationStack` itself lives in
/// `MainTabView` (its path is owned by `VaultNavigator`, so global search can
/// reset this tab to the list and push an album); this screen declares the
/// stack's destinations.
struct AlbumListScreen: View {
    @State private var viewModel: AlbumListViewModel
    let container: AppContainer
    @Environment(UndoCenter.self) private var undoCenter: UndoCenter?
    /// Optional so previews (and any host without `MainTabView` above) render.
    @Environment(VaultNavigator.self) private var navigator: VaultNavigator?

    @State private var showCreateAlert = false
    @State private var newAlbumName = ""
    @State private var albumToRename: Album?
    @State private var renameText = ""
    @State private var albumToDelete: Album?

    init(viewModel: AlbumListViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 16)]

    var body: some View {
        Group {
            if viewModel.albums.isEmpty {
                EmptyStateView(.noAlbums) {
                    newAlbumName = ""
                    showCreateAlert = true
                }
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(viewModel.albums) { album in
                            NavigationLink(value: GalleryRoute.album(album.id)) {
                                albumCard(album)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("Rename") {
                                    renameText = album.name
                                    albumToRename = album
                                }
                                Button(VaultCopy.deleteAction, role: .destructive) {
                                    albumToDelete = album
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle(VaultCopy.vaultTabGallery)
        // Browsing toolbar: search · sort · new album.
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { navigator?.presentSearch() } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel(VaultCopy.searchTitle)
                SortMenu(selection: viewModel.sort) { viewModel.setSort($0) }
                Button {
                    newAlbumName = ""
                    showCreateAlert = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        // The Gallery stack's destinations. Routes carry ids, so a route that
        // outlives its row simply renders nothing.
        .navigationDestination(for: GalleryRoute.self) { route in
            destination(route)
        }
        .alert("New album", isPresented: $showCreateAlert) {
            TextField("Name", text: $newAlbumName)
            Button("Create") { viewModel.createAlbum(named: newAlbumName) }
            Button(VaultCopy.cancelAction, role: .cancel) {}
        }
        .alert("Rename album", isPresented: Binding(
            get: { albumToRename != nil },
            set: { if !$0 { albumToRename = nil } }
        )) {
            TextField("Name", text: $renameText)
            Button("Rename") {
                if let album = albumToRename { viewModel.renameAlbum(album, to: renameText) }
                albumToRename = nil
            }
            Button(VaultCopy.cancelAction, role: .cancel) { albumToRename = nil }
        }
        .confirmationDialog(
            deleteTitle,
            isPresented: Binding(
                get: { albumToDelete != nil },
                set: { if !$0 { albumToDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                if let album = albumToDelete {
                    deleteAlbum(album)
                }
                albumToDelete = nil
            }
            Button(VaultCopy.cancelAction, role: .cancel) { albumToDelete = nil }
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
        .onAppear { viewModel.reload() }
    }

    @ViewBuilder
    private func destination(_ route: GalleryRoute) -> some View {
        switch route {
        case .album(let id):
            if let album = container.photoRepository.album(withId: id) {
                AlbumGridScreen(
                    viewModel: AlbumGridViewModel(album: album,
                                                  repository: container.photoRepository,
                                                  importer: container.photoImporter),
                    container: container
                )
            }
        }
    }

    /// Counts live photos only — trashed photos are not "its photos" any more.
    private var deleteTitle: String {
        guard let album = albumToDelete else { return "" }
        return VaultCopy.confirmDeleteAlbum(photoCount: viewModel.photoCount(for: album))
    }

    /// Soft delete + undo toast. The closure carries the id only, never the
    /// model, and restores through the view model so the grid reloads.
    private func deleteAlbum(_ album: Album) {
        let id = album.id
        let viewModel = viewModel
        viewModel.deleteAlbum(album)
        undoCenter?.post(message: VaultCopy.deletedAlbum) {
            viewModel.restoreAlbums(ids: [id])
        }
    }

    private func albumCard(_ album: Album) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemFill))
                if let cover = viewModel.coverPhoto(for: album) {
                    PhotoThumbnailView(photo: cover, fileStore: container.photoFileStore)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    Image(systemName: "photo")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                }
            }
            .aspectRatio(1, contentMode: .fit)
            Text(album.name)
                .font(.headline)
                .lineLimit(1)
            // Same §10 ID as the trash rows and N1's album search rows, so the
            // three surfaces that show "N photos" cannot drift apart.
            Text(VaultCopy.trashPhotoCount(viewModel.photoCount(for: album)))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
