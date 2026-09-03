import SwiftUI

/// "Recently deleted" (decisions §3): sections Albums / Photos / Notes /
/// Contacts, each row with Restore and Delete now, toolbar Empty → confirm →
/// purge everything. Pushed from the Settings Data section; lives under
/// `MainTabView`, so it is torn down with the vault on lock.
struct TrashScreen: View {
    @State private var viewModel: TrashViewModel
    let container: AppContainer

    @State private var confirmEmpty = false

    init(viewModel: TrashViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    var body: some View {
        Group {
            if viewModel.contents.isEmpty {
                EmptyStateView(.trashEmpty)
            } else {
                trashList
            }
        }
        .navigationTitle(VaultCopy.trashTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button(VaultCopy.trashEmpty) { confirmEmpty = true }
                .disabled(viewModel.contents.isEmpty)
        }
        // The one place (besides Erase everything) where "This cannot be
        // undone." is still true.
        .confirmationDialog(VaultCopy.trashEmptyConfirmTitle, isPresented: $confirmEmpty,
                            titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                Task { await viewModel.emptyAll() }
            }
            Button(VaultCopy.cancelAction, role: .cancel) {}
        } message: {
            Text(VaultCopy.trashEmptyConfirmBody)
        }
        .onAppear { viewModel.reload() }
    }

    private var trashList: some View {
        List {
            if !viewModel.contents.albums.isEmpty {
                Section(VaultCopy.trashSectionAlbums) {
                    ForEach(viewModel.contents.albums) { album in
                        trashRow(TrashItemID(kind: .album, id: album.id),
                                 title: album.name,
                                 subtitle: subtitle(VaultCopy.trashPhotoCount(viewModel.trashedPhotoCount(in: album)),
                                                    deletedAt: album.deletedAt)) {
                            if let cover = viewModel.coverPhoto(for: album) {
                                PhotoThumbnailView(photo: cover, fileStore: container.photoFileStore)
                            } else {
                                iconTile("photo.on.rectangle.angled")
                            }
                        }
                    }
                }
            }
            if !viewModel.contents.photos.isEmpty {
                Section(VaultCopy.trashSectionPhotos) {
                    ForEach(viewModel.contents.photos) { photo in
                        trashRow(TrashItemID(kind: .photo, id: photo.id),
                                 title: photo.album?.name ?? VaultCopy.trashSectionPhotos,
                                 subtitle: subtitle(nil, deletedAt: photo.deletedAt)) {
                            PhotoThumbnailView(photo: photo, fileStore: container.photoFileStore)
                        }
                    }
                }
            }
            if !viewModel.contents.notes.isEmpty {
                Section(VaultCopy.trashSectionNotes) {
                    ForEach(viewModel.contents.notes) { note in
                        trashRow(TrashItemID(kind: .note, id: note.id),
                                 title: note.title.isEmpty ? NoteDerivation.emptyTitleFallback : note.title,
                                 subtitle: subtitle(nil, deletedAt: note.deletedAt)) {
                            iconTile("note.text")
                        }
                    }
                }
            }
            if !viewModel.contents.contacts.isEmpty {
                Section(VaultCopy.trashSectionContacts) {
                    ForEach(viewModel.contents.contacts) { contact in
                        trashRow(TrashItemID(kind: .contact, id: contact.id),
                                 title: contact.displayName,
                                 subtitle: subtitle(nil, deletedAt: contact.deletedAt)) {
                            iconTile("person.crop.circle")
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Rows

    /// Leading tile · title + subtitle · Restore · Delete now (red trash
    /// button). Both §3 row actions are visible on the row; Delete now is also
    /// a trailing swipe action and, with Restore, in the context menu (no
    /// per-row confirm).
    private func trashRow<Leading: View>(_ item: TrashItemID, title: String, subtitle: String,
                                         @ViewBuilder leading: () -> Leading) -> some View {
        HStack(spacing: 12) {
            leading()
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Button(VaultCopy.trashRestore) { viewModel.restore(item) }
                .buttonStyle(.bordered)
                .controlSize(.small)
            Button(role: .destructive) {
                Task { await viewModel.purge(item) }
            } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .accessibilityLabel(VaultCopy.trashDeleteNow)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                Task { await viewModel.purge(item) }
            } label: {
                Label(VaultCopy.trashDeleteNow, systemImage: "trash")
            }
        }
        .contextMenu {
            Button { viewModel.restore(item) } label: {
                Label(VaultCopy.trashRestore, systemImage: "arrow.uturn.backward")
            }
            Button(role: .destructive) {
                Task { await viewModel.purge(item) }
            } label: {
                Label(VaultCopy.trashDeleteNow, systemImage: "trash")
            }
        }
    }

    private func iconTile(_ systemImage: String) -> some View {
        ZStack {
            Rectangle().fill(Color(.secondarySystemFill))
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(.secondary)
        }
    }

    private func subtitle(_ detail: String?, deletedAt: Date?) -> String {
        let daysLeft = VaultCopy.trashDaysLeft(viewModel.daysLeft(deletedAt: deletedAt))
        guard let detail else { return daysLeft }
        return "\(detail) · \(daysLeft)"
    }
}
