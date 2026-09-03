import AVFoundation
import CoreGraphics
import Foundation
import UniformTypeIdentifiers

/// Which frame of a video becomes its poster thumbnail (decisions §11:
/// `min(1 s, duration / 2)`). Pure so the rule is pinned by a unit test and
/// stays identical to Android's.
enum PosterFrame {
    /// The preferred offset for anything at least two seconds long.
    static let preferredOffsetSeconds: Double = 1.0

    /// `min(1 s, duration / 2)`. A zero, negative, infinite or NaN duration
    /// (a still-image "video", a broken file) yields the very first frame.
    static func offsetSeconds(durationSeconds: Double) -> Double {
        guard durationSeconds.isFinite, durationSeconds > 0 else { return 0 }
        return min(preferredOffsetSeconds, durationSeconds / 2)
    }
}

/// Everything `PhotoFileStore` needs to file a video away, read once from the
/// staged original. No re-encode happens anywhere in this type — the poster
/// frame is the only pixel data it produces.
///
/// `@unchecked Sendable`: the only reference member is `poster`, and `CGImage`
/// is immutable (CoreGraphics documents it as Sendable). The explicit
/// conformance keeps the value crossing the `PhotoFileStore` actor boundary
/// regardless of SDK annotation drift.
struct VideoProbeResult: @unchecked Sendable {
    let durationMs: Int
    /// Transform-aware: a portrait clip recorded by a landscape sensor reports
    /// portrait dimensions, exactly like its poster frame.
    let width: Int
    let height: Int
    let mimeType: String
    let poster: CGImage?
}

/// Reads duration, display size and one poster frame out of a video file.
///
/// Every member is `nonisolated` and takes/returns `Sendable` values, so the
/// non-`Sendable` AVFoundation objects (`AVURLAsset`, `AVAssetImageGenerator`)
/// never leave this file's isolation domain and the `PhotoFileStore` actor can
/// simply `await` the result.
enum VideoProbe {
    enum ProbeError: Error {
        case noVideoTrack
    }

    static let fallbackMIMEType = "video/quicktime"

    /// Loads metadata and extracts the poster frame. Throws only when the file
    /// carries no video track at all; a failed frame extraction degrades to
    /// `poster == nil` so a playable file is never rejected over its thumbnail.
    static func probe(url: URL) async throws -> VideoProbeResult {
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration)
        let seconds = duration.isNumeric ? CMTimeGetSeconds(duration) : 0
        let durationMs = milliseconds(fromSeconds: seconds)

        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else { throw ProbeError.noVideoTrack }
        let (naturalSize, transform) = try await track.load(.naturalSize, .preferredTransform)
        let displayed = naturalSize.applying(transform)
        let width = Int(abs(displayed.width).rounded())
        let height = Int(abs(displayed.height).rounded())

        let poster = try? await posterImage(asset: asset, durationSeconds: seconds)

        return VideoProbeResult(durationMs: durationMs,
                                width: width,
                                height: height,
                                mimeType: mimeType(for: url),
                                poster: poster)
    }

    /// `min(1 s, duration / 2)` through `AVAssetImageGenerator`, already
    /// capped at the thumbnail's max pixel size and rotated upright.
    private static func posterImage(asset: AVURLAsset, durationSeconds: Double) async throws -> CGImage {
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: PhotoFileStore.thumbnailMaxPixel,
                                       height: PhotoFileStore.thumbnailMaxPixel)
        // Short clips must not snap to a keyframe on the far side of the file.
        // The half-second forward tolerance is deliberate: an exact-frame
        // extraction on a long-GOP clip decodes from the previous keyframe and
        // can take seconds during an import, so the poster is allowed to land
        // on the next keyframe within 500 ms of the §11 offset. Android's
        // `MediaMetadataRetriever` picks its own nearest frame, so the two
        // platforms' posters may differ by a frame — accepted, the offset rule
        // is what is shared, not the exact frame.
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = CMTime(seconds: 0.5, preferredTimescale: 600)
        let offset = PosterFrame.offsetSeconds(durationSeconds: durationSeconds)
        let time = CMTime(seconds: offset, preferredTimescale: 600)
        return try await generator.image(at: time).image
    }

    /// Rounded to the nearest millisecond; never negative.
    static func milliseconds(fromSeconds seconds: Double) -> Int {
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int((seconds * 1000).rounded())
    }

    /// MIME type from the file's own type identifier, falling back to the
    /// extension and finally to QuickTime. The stored value feeds the Details
    /// sheet's Type row through `MediaMetadataFormatter.typeLabel`.
    static func mimeType(for url: URL) -> String {
        if let contentType = try? url.resourceValues(forKeys: [.contentTypeKey]).contentType,
           let mime = contentType.preferredMIMEType {
            return mime
        }
        let ext = url.pathExtension.lowercased()
        if !ext.isEmpty, let type = UTType(filenameExtension: ext), let mime = type.preferredMIMEType {
            return mime
        }
        return fallbackMIMEType
    }
}
