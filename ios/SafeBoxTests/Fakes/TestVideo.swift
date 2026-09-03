import AVFoundation
import CoreGraphics
import CoreVideo
import Foundation

/// Generates a tiny but REAL video file at test time with `AVAssetWriter`
/// (a run of solid-color frames), so the video tests exercise AVFoundation
/// end to end without a binary fixture checked into the repo.
///
/// Everything is `nonisolated`: `AVAssetWriter` and friends are not `Sendable`
/// and never leave this file's isolation domain.
enum TestVideo {
    enum FixtureError: Error {
        case writerFailed(String)
        case noPixelBufferPool
    }

    /// Writes a `.mov` of `frameCount` frames at `fps` (so the duration is
    /// `frameCount / fps` seconds) and returns the URL it was given.
    @discardableResult
    static func write(to url: URL,
                      width: Int = 160,
                      height: Int = 120,
                      frameCount: Int = 30,
                      fps: Int32 = 30) async throws -> URL {
        try? FileManager.default.removeItem(at: url)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)

        let writer = try AVAssetWriter(outputURL: url, fileType: .mov)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: width,
            AVVideoHeightKey: height,
        ])
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height,
            ]
        )
        guard writer.canAdd(input) else { throw FixtureError.writerFailed("cannot add input") }
        writer.add(input)
        guard writer.startWriting() else {
            throw FixtureError.writerFailed(writer.error?.localizedDescription ?? "startWriting")
        }
        writer.startSession(atSourceTime: .zero)
        guard let pool = adaptor.pixelBufferPool else { throw FixtureError.noPixelBufferPool }

        for frame in 0..<frameCount {
            while !input.isReadyForMoreMediaData {
                try await Task.sleep(for: .milliseconds(5))
            }
            let buffer = try pixelBuffer(from: pool, width: width, height: height, frame: frame)
            let time = CMTime(value: CMTimeValue(frame), timescale: fps)
            guard adaptor.append(buffer, withPresentationTime: time) else {
                throw FixtureError.writerFailed(writer.error?.localizedDescription ?? "append")
            }
        }

        input.markAsFinished()
        writer.endSession(atSourceTime: CMTime(value: CMTimeValue(frameCount), timescale: fps))
        await writer.finishWriting()
        guard writer.status == .completed else {
            throw FixtureError.writerFailed(writer.error?.localizedDescription ?? "finishWriting")
        }
        return url
    }

    /// A solid frame whose color walks with the frame index, so consecutive
    /// frames are not identical and the encoder produces real content.
    private static func pixelBuffer(from pool: CVPixelBufferPool,
                                    width: Int,
                                    height: Int,
                                    frame: Int) throws -> CVPixelBuffer {
        var buffer: CVPixelBuffer?
        guard CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer) == kCVReturnSuccess,
              let pixelBuffer = buffer else {
            throw FixtureError.noPixelBufferPool
        }
        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }
        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer),
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                  data: base, width: width, height: height, bitsPerComponent: 8,
                  bytesPerRow: CVPixelBufferGetBytesPerRow(pixelBuffer), space: space,
                  bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                      | CGBitmapInfo.byteOrder32Little.rawValue
              ) else {
            throw FixtureError.writerFailed("bitmap context")
        }
        let shade = CGFloat(frame % 32) / 32.0
        context.setFillColor(red: 0.2 + shade * 0.6, green: 0.35, blue: 0.8 - shade * 0.4, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return pixelBuffer
    }
}
