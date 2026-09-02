import SwiftUI
import UIKit

/// Grid/card cell image: loads the stored thumbnail file only — the master
/// file is never decoded at grid time.
struct PhotoThumbnailView: View {
    let photo: Photo
    let fileStore: PhotoFileStore

    @State private var image: UIImage?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Rectangle().fill(Color(.secondarySystemFill))
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .task(id: photo.thumbFileName) {
            image = await ImageLoader.shared.loadImage(
                at: fileStore.thumbnailURL(thumbFileName: photo.thumbFileName),
                maxPixel: PhotoFileStore.thumbnailMaxPixel,
                cacheKey: "thumb:" + photo.thumbFileName
            )
        }
    }
}
