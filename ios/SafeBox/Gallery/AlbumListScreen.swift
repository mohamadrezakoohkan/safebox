import SwiftUI

/// Album card grid: cover thumbnail (derived), name, photo count.
struct AlbumListScreen: View {
    @State private var viewModel: AlbumListViewModel
    let container: AppContainer

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
        NavigationStack {
            Group {
                if viewModel.albums.isEmpty {
                    ContentUnavailableView {
                        Label("No albums yet", systemImage: "photo.on.rectangle.angled")
                    } actions: {
                        Button("Create album") { showCreateAlert = true }
                            .buttonStyle(.borderedProminent)
                    }
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 16) {
                            ForEach(viewModel.albums) { album in
                                NavigationLink {
                                    AlbumGridScreen(
                                        viewModel: AlbumGridViewModel(album: album,
                                                                      repository: container.photoRepository,
                                                                      importer: container.photoImporter),
                                        container: container
                                    )
                                } label: {
                                    albumCard(album)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button("Rename") {
                                        renameText = album.name
                                        albumToRename = album
                                    }
                                    Button("Delete", role: .destructive) {
                                        albumToDelete = album
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("Gallery")
            .toolbar {
                Button {
                    newAlbumName = ""
                    showCreateAlert = true
                } label: {
                    Image(systemName: "plus")
                }
            }
            .alert("New album", isPresented: $showCreateAlert) {
                TextField("Name", text: $newAlbumName)
                Button("Create") { viewModel.createAlbum(named: newAlbumName) }
                Button("Cancel", role: .cancel) {}
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
                Button("Cancel", role: .cancel) { albumToRename = nil }
            }
            .confirmationDialog(
                deleteMessage,
                isPresented: Binding(
                    get: { albumToDelete != nil },
                    set: { if !$0 { albumToDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete album", role: .destructive) {
                    if let album = albumToDelete {
                        Task { await viewModel.deleteAlbum(album) }
                    }
                    albumToDelete = nil
                }
                Button("Cancel", role: .cancel) { albumToDelete = nil }
            }
            .onAppear { viewModel.reload() }
        }
    }

    private var deleteMessage: String {
        guard let album = albumToDelete else { return "" }
        return "Delete album and its \(album.photos.count) photos? This cannot be undone."
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
            Text("\(viewModel.photoCount(for: album)) photos")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
