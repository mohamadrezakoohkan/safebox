import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Actor owning all media file IO: original writes (byte-for-byte, real
/// extension — stills as `Data`, videos as a moved file), thumbnail and video
/// poster generation, deletes, the startup orphan sweep (backstop only), and
/// tmp/ + Staging/ cleanup.
actor PhotoFileStore {
    struct StoredPhoto: Sendable {
        let id: UUID
        let fileName: String
        let thumbFileName: String
        let mimeType: String
        let width: Int
        let height: Int
        let byteCount: Int
        /// `MediaType` raw value — `photo` for stills, `video` for anything
        /// stored through `store(videoAt:)` (N3).
        var mediaType: String = MediaType.photo.rawValue
        /// Videos only (N3); `nil` for stills.
        var durationMs: Int? = nil
    }

    enum StoreError: Error, Equatable {
        case unreadableImage
        /// The staged file carries no readable video track, or no poster frame
        /// could be extracted from it.
        case unreadableVideo
    }

    static let thumbnailMaxPixel: CGFloat = 600 // ~300pt @2x

    nonisolated let rootURL: URL
    nonisolated var photosURL: URL { rootURL.appendingPathComponent("Photos", isDirectory: true) }
    nonisolated var thumbnailsURL: URL { rootURL.appendingPathComponent("Thumbnails", isDirectory: true) }
    /// Where picked videos land before they are moved into `Photos/`
    /// (`VaultStaging`). Inside the vault, never the system tmp dir.
    nonisolated var stagingURL: URL { VaultStaging.directoryURL(in: rootURL) }

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

    /// Stores a picked VIDEO by moving the staged original into `Photos/`
    /// byte-for-byte under its real extension — no re-encode, no transcode, and
    /// the bytes are never held in memory. The poster frame goes through the
    /// same thumbnail writer stills use, so the grid stays uniform.
    ///
    /// The staged file is consumed: on success it has been moved, on failure it
    /// is deleted, so a failed import leaves neither a row nor a file.
    func store(videoAt stagedURL: URL) async throws -> StoredPhoto {
        do {
            try ensureDirectories()
            let probe = try await VideoProbe.probe(url: stagedURL)
            let attributes = try? FileManager.default.attributesOfItem(atPath: stagedURL.path)
            let byteCount = (attributes?[.size] as? NSNumber)?.intValue

            let id = UUID()
            let ext = stagedURL.pathExtension.lowercased()
            let fileName = ext.isEmpty ? "\(id.uuidString).mov" : "\(id.uuidString).\(ext)"
            let thumbFileName = "\(id.uuidString).jpg"
            let destination = photosURL.appendingPathComponent(fileName)

            // A move, not a copy: same volume, so the bytes are untouched.
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: stagedURL, to: destination)
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.completeUnlessOpen],
                ofItemAtPath: destination.path
            )

            guard let poster = probe.poster else {
                try? FileManager.default.removeItem(at: destination)
                throw StoreError.unreadableVideo
            }
            do {
                try writeThumbnail(poster, to: thumbnailsURL.appendingPathComponent(thumbFileName))
            } catch {
                try? FileManager.default.removeItem(at: destination)
                throw error
            }

            let size = byteCount ?? (try? Data(contentsOf: destination, options: .alwaysMapped).count) ?? 0
            return StoredPhoto(id: id, fileName: fileName, thumbFileName: thumbFileName,
                               mimeType: probe.mimeType, width: probe.width, height: probe.height,
                               byteCount: size,
                               mediaType: MediaType.video.rawValue, durationMs: probe.durationMs)
        } catch {
            // Never leave vault-bound bytes lying in Staging/.
            try? FileManager.default.removeItem(at: stagedURL)
            throw error
        }
    }

    private func writeThumbnail(from source: CGImageSource, to url: URL) throws {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.thumbnailMaxPixel,
            kCGImageSourceCreateThumbnailWithTransform: true,
        ]
        guard let thumb = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw StoreError.unreadableImage
        }
        try writeThumbnail(thumb, to: url)
    }

    /// The ONE thumbnail writer, shared by stills (a downsampled
    /// `CGImageSource` thumbnail) and videos (the `AVAssetImageGenerator`
    /// poster frame): one JPEG, longest edge ≤ `thumbnailMaxPixel`, written
    /// with the same at-rest protection as every other vault file.
    private func writeThumbnail(_ image: CGImage, to url: URL) throws {
        let thumb = Self.downscaled(image, maxPixel: Self.thumbnailMaxPixel) ?? image
        guard let destination = CGImageDestinationCreateWithURL(
            url as CFURL, UTType.jpeg.identifier as CFString, 1, nil
        ) else {
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

    /// Aspect-preserving downscale so the longest edge is `maxPixel`. Returns
    /// `nil` when the image already fits (the common case: both producers cap
    /// the size themselves) or when a bitmap context cannot be made.
    static func downscaled(_ image: CGImage, maxPixel: CGFloat) -> CGImage? {
        let longest = CGFloat(max(image.width, image.height))
        guard longest > maxPixel, longest > 0, maxPixel > 0 else { return nil }
        let scale = maxPixel / longest
        let width = max(1, Int((CGFloat(image.width) * scale).rounded()))
        let height = max(1, Int((CGFloat(image.height) * scale).rounded()))
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                  data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                  space: space,
                  bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                      | CGBitmapInfo.byteOrder32Little.rawValue
              ) else {
            return nil
        }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage()
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

    /// Erase-everything path: removes every stored byte — originals, thumbs
    /// and anything staged mid-import.
    func deleteAll() {
        try? FileManager.default.removeItem(at: photosURL)
        try? FileManager.default.removeItem(at: thumbnailsURL)
        try? FileManager.default.removeItem(at: stagingURL)
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

    /// Clears `Staging/` (decisions §9). Staged videos are moved out by
    /// `store(videoAt:)`, so anything still here is the residue of a crash or
    /// a kill mid-import: unreferenced by any row and unreachable by the user.
    /// Runs at launch only — never on lock, which would delete the bytes of an
    /// import still in flight through the picker round-trip.
    func cleanStagingDirectory() {
        try? FileManager.default.removeItem(at: stagingURL)
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
