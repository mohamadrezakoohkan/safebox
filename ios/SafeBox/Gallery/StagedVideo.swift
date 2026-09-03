import CoreTransferable
import Foundation
import os
import UniformTypeIdentifiers

/// The staging area for picked videos: `<vault>/Staging/`.
///
/// Videos are received from the photo picker as FILES, never as `Data` — a
/// 4 GB clip must not be loaded whole into memory. The file the picker hands
/// over lives only for the duration of the import closure, so it is copied
/// somewhere durable first. That somewhere is deliberately **inside the vault
/// directory**, not `FileManager.temporaryDirectory`, which
/// `PhotoFileStore.cleanTemporaryDirectory()` wipes on every lock — a lock
/// during the picker round-trip would otherwise delete the bytes mid-import.
/// Staged files are moved into `Photos/` by `PhotoFileStore.store(videoAt:)`;
/// anything left behind (a crash, a kill) is cleared at the next launch by
/// `PhotoFileStore.cleanStagingDirectory()`.
enum VaultStaging {
    static let directoryName = "Staging"

    static func directoryURL(in root: URL) -> URL {
        root.appendingPathComponent(directoryName, isDirectory: true)
    }

    /// The live vault's staging directory — the fallback when no importer has
    /// declared one (see `destinationDirectoryURL()`).
    static func liveDirectoryURL() -> URL {
        directoryURL(in: ModelContainerFactory.vaultDirectoryURL())
    }

    /// Where the `Transferable` import closure stages to.
    ///
    /// The closure is a static context with no access to the container, so the
    /// owning `PhotoImporter` declares its `PhotoFileStore`'s staging directory
    /// before every import (`setDestinationDirectory`). Without that a preview
    /// or test store would stage into the REAL vault directory — bytes written
    /// outside the store that is supposed to own them. The lock is here because
    /// the import closure runs on an arbitrary thread.
    private static let destination = OSAllocatedUnfairLock<URL?>(initialState: nil)

    /// Declares the staging directory for subsequent imports; `nil` restores
    /// the live-vault fallback.
    static func setDestinationDirectory(_ url: URL?) {
        destination.withLock { $0 = url }
    }

    static func destinationDirectoryURL() -> URL {
        destination.withLock { $0 } ?? liveDirectoryURL()
    }

    /// Copies `source` into `directory` under a fresh name that keeps the real
    /// extension, applying the same at-rest protection as every other vault
    /// file. Returns the staged URL.
    @discardableResult
    static func stage(_ source: URL, in directory: URL) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let ext = source.pathExtension.lowercased()
        let name = ext.isEmpty ? UUID().uuidString : "\(UUID().uuidString).\(ext)"
        let destination = directory.appendingPathComponent(name)
        try FileManager.default.copyItem(at: source, to: destination)
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUnlessOpen],
            ofItemAtPath: destination.path
        )
        return destination
    }

    /// `stage(_:in:)` into the directory the current importer declared (the
    /// live vault's when none has).
    @discardableResult
    static func stageCurrent(_ source: URL) throws -> URL {
        try stage(source, in: destinationDirectoryURL())
    }
}

/// File-backed `Transferable` used for picked videos.
///
/// `FileRepresentation` (not `DataRepresentation`) is the whole point: the
/// system copies the asset to a temporary URL and hands us the URL, so the
/// original bytes never pass through memory and never get re-encoded.
struct StagedVideo: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            StagedVideo(url: try VaultStaging.stageCurrent(received.file))
        }
    }
}
