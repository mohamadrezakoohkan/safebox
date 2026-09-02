import Foundation
import SwiftData

/// Versioned schema from day one — retrofitting versioning after users have
/// stores is the painful part.
enum SchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] {
        [Album.self, Photo.self, Note.self, Tag.self, Contact.self]
    }
}

enum SafeBoxMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}

enum ModelContainerFactory {
    /// All persistent data lives in one relocatable directory:
    /// <Application Support>/SafeBox/ — backup-excluded, per-file protected.
    static func vaultDirectoryURL() -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("SafeBox", isDirectory: true)
    }

    static func live() throws -> ModelContainer {
        let root = vaultDirectoryURL()
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)

        // Backup exclusion — one of two separate at-rest mechanisms (§6).
        var url = root
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)

        let storeURL = root.appendingPathComponent("store.sqlite")
        let configuration = ModelConfiguration(url: storeURL)
        let container = try ModelContainer(
            for: Schema(versionedSchema: SchemaV1.self),
            migrationPlan: SafeBoxMigrationPlan.self,
            configurations: configuration
        )

        // Per-file protection applied to the store and its sidecars — directory
        // attributes don't reliably propagate. .completeUnlessOpen, not
        // .complete: writes after device lock (autosave flush) must not fail.
        for suffix in ["", "-wal", "-shm"] {
            let path = storeURL.path + suffix
            if FileManager.default.fileExists(atPath: path) {
                try? FileManager.default.setAttributes(
                    [.protectionKey: FileProtectionType.completeUnlessOpen],
                    ofItemAtPath: path
                )
            }
        }
        return container
    }

    static func inMemory() -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        // No migrationPlan here: SwiftData traps when a staged migration plan
        // is combined with an in-memory store (previews/tests only).
        // swiftlint:disable:next force_try
        return try! ModelContainer(
            for: Schema(versionedSchema: SchemaV1.self),
            configurations: configuration
        )
    }
}
