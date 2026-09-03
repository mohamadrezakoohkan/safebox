import Foundation
import PhotosUI
import SwiftUI
import Observation
import UniformTypeIdentifiers

/// Container-owned importer: keyed by albumId so an in-flight import completes
/// at the repository level even if the vault locked mid round-trip.
@MainActor
@Observable
final class PhotoImporter {
    private let fileStore: PhotoFileStore
    private let repository: any PhotoRepository

    private(set) var isImporting = false
    private(set) var completedCount = 0
    private(set) var totalCount = 0
    /// Videos that could not be staged, probed or filed away in the last run
    /// (N3). The grid turns a non-zero count into the `video_import_failed`
    /// notice. Reset at the start of every import.
    private(set) var failedVideoCount = 0

    init(fileStore: PhotoFileStore, repository: any PhotoRepository) {
        self.fileStore = fileStore
        self.repository = repository
    }

    /// True when the picker item is a movie, i.e. it must be received as a
    /// FILE (`StagedVideo`) rather than as `Data` — a video is never loaded
    /// whole into memory.
    static func isVideo(_ item: PhotosPickerItem) -> Bool {
        item.supportedContentTypes.contains { $0.conforms(to: .movie) }
    }

    /// Item-count based progress (decisions §9: byte-based progress deferred).
    /// Returns the number of videos that failed.
    @discardableResult
    func importItems(_ items: [PhotosPickerItem], albumId: UUID) async -> Int {
        // Videos are staged by a static `Transferable` closure, which has no
        // way to reach this importer: tell it which store owns this import, so
        // a preview/test container never stages into the real vault directory.
        VaultStaging.setDestinationDirectory(fileStore.stagingURL)
        guard !items.isEmpty else { return 0 }
        isImporting = true
        totalCount = items.count
        completedCount = 0
        failedVideoCount = 0
        defer {
            isImporting = false
        }
        for item in items {
            if Self.isVideo(item) {
                await importVideo(item, albumId: albumId)
            } else {
                await importStill(item, albumId: albumId)
            }
            completedCount += 1
        }
        return failedVideoCount
    }

    /// Original bytes, untouched. Failed items are skipped atomically — no
    /// orphan row, no orphan file.
    private func importStill(_ item: PhotosPickerItem, albumId: UUID) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        guard let stored = try? await fileStore.store(data: data) else { return }
        await insert(stored, albumId: albumId)
    }

    /// Videos travel as files: the picker copy is staged inside the vault by
    /// `StagedVideo`, then MOVED into `Photos/` byte-for-byte. `store(videoAt:)`
    /// consumes the staged file either way, so a failure leaves nothing behind.
    private func importVideo(_ item: PhotosPickerItem, albumId: UUID) async {
        guard let staged = try? await item.loadTransferable(type: StagedVideo.self) else {
            failedVideoCount += 1
            return
        }
        guard let stored = try? await fileStore.store(videoAt: staged.url) else {
            failedVideoCount += 1
            return
        }
        await insert(stored, albumId: albumId)
    }

    private func insert(_ stored: PhotoFileStore.StoredPhoto, albumId: UUID) async {
        do {
            try repository.insertPhoto(stored, albumId: albumId)
        } catch {
            await fileStore.delete(fileName: stored.fileName, thumbFileName: stored.thumbFileName)
        }
    }
}
