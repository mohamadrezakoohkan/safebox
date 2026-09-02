import Foundation
import PhotosUI
import SwiftUI
import Observation

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

    init(fileStore: PhotoFileStore, repository: any PhotoRepository) {
        self.fileStore = fileStore
        self.repository = repository
    }

    func importItems(_ items: [PhotosPickerItem], albumId: UUID) async {
        guard !items.isEmpty else { return }
        isImporting = true
        totalCount = items.count
        completedCount = 0
        defer {
            isImporting = false
        }
        for item in items {
            // Original bytes, untouched. Failed items are skipped atomically —
            // no orphan row, no orphan file.
            guard let data = try? await item.loadTransferable(type: Data.self) else {
                completedCount += 1
                continue
            }
            guard let stored = try? await fileStore.store(data: data) else {
                completedCount += 1
                continue
            }
            do {
                try repository.insertPhoto(stored, albumId: albumId)
            } catch {
                await fileStore.delete(fileName: stored.fileName, thumbFileName: stored.thumbFileName)
            }
            completedCount += 1
        }
    }
}
