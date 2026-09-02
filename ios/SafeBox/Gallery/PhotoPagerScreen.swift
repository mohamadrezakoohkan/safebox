import SwiftUI
import UIKit

/// Full-screen pager: horizontal paging between photos; zoom per page.
/// Page-swiping is owned by the TabView; while zoomed, the UIScrollView's own
/// pan consumes horizontal drags. Zoom resets on page change.
struct PhotoPagerScreen: View {
    let photos: [Photo]
    let startPhoto: Photo
    let fileStore: PhotoFileStore
    let otherAlbums: [Album]
    let onDelete: (Photo) -> Void
    let onMove: (Photo, Album) -> Void

    @State private var currentId: UUID
    @State private var localPhotos: [Photo]
    @State private var resetToken = 0
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    init(photos: [Photo], startPhoto: Photo, fileStore: PhotoFileStore, otherAlbums: [Album],
         onDelete: @escaping (Photo) -> Void, onMove: @escaping (Photo, Album) -> Void) {
        self.photos = photos
        self.startPhoto = startPhoto
        self.fileStore = fileStore
        self.otherAlbums = otherAlbums
        self.onDelete = onDelete
        self.onMove = onMove
        _currentId = State(initialValue: startPhoto.id)
        _localPhotos = State(initialValue: photos)
    }

    private var currentPhoto: Photo? {
        localPhotos.first { $0.id == currentId }
    }

    var body: some View {
        TabView(selection: $currentId) {
            ForEach(localPhotos) { photo in
                PagerPageView(photo: photo, fileStore: fileStore, resetToken: resetToken)
                    .tag(photo.id)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(Color.black.ignoresSafeArea())
        .onChange(of: currentId) { _, _ in
            resetToken += 1 // zoom resets on page change
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !otherAlbums.isEmpty {
                    Menu {
                        ForEach(otherAlbums) { album in
                            Button(album.name) { moveCurrent(to: album) }
                        }
                    } label: {
                        Image(systemName: "folder")
                    }
                }
                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Image(systemName: "trash")
                }
            }
        }
        .confirmationDialog("Delete this photo? This cannot be undone.",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { deleteCurrent() }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func deleteCurrent() {
        guard let photo = currentPhoto else { return }
        onDelete(photo)
        removeLocally(photo)
    }

    private func moveCurrent(to album: Album) {
        guard let photo = currentPhoto else { return }
        onMove(photo, album)
        removeLocally(photo)
    }

    private func removeLocally(_ photo: Photo) {
        guard let index = localPhotos.firstIndex(where: { $0.id == photo.id }) else { return }
        localPhotos.remove(at: index)
        if localPhotos.isEmpty {
            dismiss()
        } else {
            currentId = localPhotos[min(index, localPhotos.count - 1)].id
        }
    }
}

private struct PagerPageView: View {
    let photo: Photo
    let fileStore: PhotoFileStore
    let resetToken: Int

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                ZoomableImageView(image: image, resetToken: resetToken)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task(id: photo.fileName) {
            // Downsample to ~screen scale — never decode 48MP originals whole.
            let maxPixel = UIScreen.main.scale * max(UIScreen.main.bounds.width,
                                                     UIScreen.main.bounds.height)
            image = await ImageLoader.shared.loadImage(
                at: fileStore.photoURL(fileName: photo.fileName),
                maxPixel: maxPixel,
                cacheKey: "full:" + photo.fileName
            )
        }
    }
}
