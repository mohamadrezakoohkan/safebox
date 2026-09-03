import Foundation
import SwiftData

/// Versioned schema from day one — retrofitting versioning after users have
/// stores is the painful part. `SchemaV1` (Persistence/SchemaV1.swift) is the
/// frozen iteration-1 snapshot; `SchemaV2` (Persistence/Models.swift) is live.
/// The single iteration-2 migration is additive (nullable `deletedAt` columns,
/// `mediaType` with a literal default, nullable `durationMs`), so it is a
/// lightweight stage (decisions §0).
enum SafeBoxMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self, SchemaV2.self] }
    static var stages: [MigrationStage] { [migrateV1toV2] }

    static let migrateV1toV2 = MigrationStage.lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)
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

        return try onDisk(storeURL: root.appendingPathComponent("store.sqlite"))
    }

    /// Opens (or creates) the store at `storeURL` with the live schema and the
    /// migration plan — the one code path both `live()` and the migration test
    /// go through, so the test exercises exactly what the app runs.
    static func onDisk(storeURL: URL) throws -> ModelContainer {
        let configuration = ModelConfiguration(url: storeURL)
        let container = try ModelContainer(
            for: Schema(versionedSchema: SchemaV2.self),
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
            for: Schema(versionedSchema: SchemaV2.self),
            configurations: configuration
        )
    }
}
