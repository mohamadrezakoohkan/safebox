import Foundation
import UIKit
import ImageIO

/// Async file→UIImage loading with an NSCache. Full-size images are decoded
/// with CGImageSource downsampling — never a full-resolution UIImage decode.
final class ImageLoader: @unchecked Sendable {
    static let shared = ImageLoader()

    private let cache = NSCache<NSString, UIImage>()

    init() {
        cache.countLimit = 300
    }

    func cachedImage(forKey key: String) -> UIImage? {
        cache.object(forKey: key as NSString)
    }

    func loadImage(at url: URL, maxPixel: CGFloat, cacheKey: String) async -> UIImage? {
        if let cached = cache.object(forKey: cacheKey as NSString) { return cached }
        let image = await Task.detached(priority: .userInitiated) { () -> UIImage? in
            Self.downsample(at: url, maxPixel: maxPixel)
        }.value
        if let image {
            cache.setObject(image, forKey: cacheKey as NSString)
        }
        return image
    }

    static func downsample(at url: URL, maxPixel: CGFloat) -> UIImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
