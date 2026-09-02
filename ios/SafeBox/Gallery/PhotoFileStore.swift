import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Actor owning all photo file IO: original writes (byte-for-byte, real
/// extension), thumbnail generation, deletes, the startup orphan sweep
/// (backstop only), and tmp/ staging cleanup.
actor PhotoFileStore {
    struct StoredPhoto: Sendable {
        let id: UUID
        let fileName: String
        let thumbFileName: String
        let mimeType: String
        let width: Int
        let height: Int
        let byteCount: Int
    }

    enum StoreError: Error {
        case unreadableImage
    }

    static let thumbnailMaxPixel: CGFloat = 600 // ~300pt @2x

    nonisolated let rootURL: URL
    nonisolated var photosURL: URL { rootURL.appendingPathComponent("Photos", isDirectory: true) }
    nonisolated var thumbnailsURL: URL { rootURL.appendingPathComponent("Thumbnails", isDirectory: true) }

    init(rootURL: URL) {
        self.rootURL = rootURL
    }

    private func ensureDirectories() throws {
        for url in [photosURL, thumbnailsURL] {
            try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        }
    }

    /// Writes the ORIGINAL bytes untouched (no re-encode, ever) under the real
    /// detected extension, and generates the downsampled thumbnail file.
    func store(data: Data) throws -> StoredPhoto {
        try ensureDirectories()
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0 else {
            throw StoreError.unreadableImage
        }
        let typeIdentifier = CGImageSourceGetType(source) as String?
        let utType = typeIdentifier.flatMap { UTType($0) }
        let ext = utType?.preferredFilenameExtension ?? "jpg"
        let mime = utType?.preferredMIMEType ?? "image/jpeg"

        var width = 0
        var height = 0
        if let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] {
            width = (props[kCGImagePropertyPixelWidth] as? Int) ?? 0
            height = (props[kCGImagePropertyPixelHeight] as? Int) ?? 0
        }

        let id = UUID()
        let fileName = "\(id.uuidString).\(ext)"
        let thumbFileName = "\(id.uuidString).jpg"

        // At-rest protection applied per-file at write time.
        try data.write(to: photosURL.appendingPathComponent(fileName),
                       options: [.atomic, .completeFileProtectionUnlessOpen])

        try writeThumbnail(from: source, to: thumbnailsURL.appendingPathComponent(thumbFileName))

        return StoredPhoto(id: id, fileName: fileName, thumbFileName: thumbFileName,
                           mimeType: mime, width: width, height: height, byteCount: data.count)
    }

    private func writeThumbnail(from source: CGImageSource, to url: URL) throws {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.thumbnailMaxPixel,
            kCGImageSourceCreateThumbnailWithTransform: true,
        ]
        guard let thumb = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary),
              let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else {
            throw StoreError.unreadableImage
        }
        let props: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.8]
        CGImageDestinationAddImage(destination, thumb, props as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw StoreError.unreadableImage
        }
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUnlessOpen],
            ofItemAtPath: url.path
        )
    }

    /// Deletes bytes, not just rows — both the full-size file and the thumbnail.
    func delete(fileName: String, thumbFileName: String) {
        try? FileManager.default.removeItem(at: photosURL.appendingPathComponent(fileName))
        try? FileManager.default.removeItem(at: thumbnailsURL.appendingPathComponent(thumbFileName))
    }

    nonisolated func photoURL(fileName: String) -> URL {
        photosURL.appendingPathComponent(fileName)
    }

    nonisolated func thumbnailURL(thumbFileName: String) -> URL {
        thumbnailsURL.appendingPathComponent(thumbFileName)
    }

    /// Erase-everything path: removes every stored byte, originals and thumbs.
    func deleteAll() {
        try? FileManager.default.removeItem(at: photosURL)
        try? FileManager.default.removeItem(at: thumbnailsURL)
    }

    /// Startup backstop only (crash recovery) — never the deletion mechanism.
    func sweepOrphans(knownFileNames: Set<String>, knownThumbFileNames: Set<String>) {
        let fm = FileManager.default
        if let files = try? fm.contentsOfDirectory(atPath: photosURL.path) {
            for file in files where !knownFileNames.contains(file) {
                try? fm.removeItem(at: photosURL.appendingPathComponent(file))
            }
        }
        if let thumbs = try? fm.contentsOfDirectory(atPath: thumbnailsURL.path) {
            for file in thumbs where !knownThumbFileNames.contains(file) {
                try? fm.removeItem(at: thumbnailsURL.appendingPathComponent(file))
            }
        }
    }

    /// PhotosPicker stages transferred data in tmp/; cleaned on every lock
    /// transition and on launch so no vault-bound bytes linger outside SafeBox/.
    func cleanTemporaryDirectory() {
        let fm = FileManager.default
        let tmp = fm.temporaryDirectory
        if let items = try? fm.contentsOfDirectory(at: tmp, includingPropertiesForKeys: nil) {
            for item in items {
                try? fm.removeItem(at: item)
            }
        }
    }
}
