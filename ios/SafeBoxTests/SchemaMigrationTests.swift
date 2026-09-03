import Foundation
import SwiftData
import Testing
@testable import SafeBox

/// The single iteration-2 migration (decisions §0): a store written with
/// `SchemaV1` reopens through the production `ModelContainerFactory.onDisk`
/// path (SchemaV2 + `SafeBoxMigrationPlan`) with every row intact and the new
/// columns at their defaults. Cannot go through `inMemory()` (no plan there),
/// so this test uses an on-disk store in a temp directory.
@MainActor
struct SchemaMigrationTests {
    private func makeTemporaryDirectory() throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxMigration-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    @Test func migrationPlanIsASingleLightweightV1ToV2Stage() {
        #expect(SafeBoxMigrationPlan.schemas.count == 2)
        #expect(SafeBoxMigrationPlan.schemas[0].versionIdentifier == SchemaV1.versionIdentifier)
        #expect(SafeBoxMigrationPlan.schemas[1].versionIdentifier == SchemaV2.versionIdentifier)
        #expect(SafeBoxMigrationPlan.stages.count == 1)
        #expect(SchemaV1.versionIdentifier == Schema.Version(1, 0, 0))
        #expect(SchemaV2.versionIdentifier == Schema.Version(2, 0, 0))
        #expect(SchemaV1.models.count == 5)
        #expect(SchemaV2.models.count == 5)
    }

    @Test func v1StoreReopensAsV2WithDataIntactAndNewColumnsAtDefaults() throws {
        let dir = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: dir) }
        let storeURL = dir.appendingPathComponent("store.sqlite")

        let albumId = UUID()
        let photoId = UUID()
        let noteId = UUID()
        let contactId = UUID()

        // 1. Write a v1 store exactly as iteration 1 did (V1 schema, no plan).
        do {
            let v1 = try ModelContainer(
                for: Schema(versionedSchema: SchemaV1.self),
                configurations: ModelConfiguration(url: storeURL)
            )
            let context = v1.mainContext
            let album = SchemaV1.Album(id: albumId, name: "Trips", sortIndex: 3)
            context.insert(album)
            let photo = SchemaV1.Photo(id: photoId, fileName: "\(photoId.uuidString).jpg",
                                       thumbFileName: "\(photoId.uuidString).jpg", mimeType: "image/jpeg",
                                       width: 40, height: 30, byteCount: 1_234, sortIndex: 0, album: album)
            context.insert(photo)
            let note = SchemaV1.Note(id: noteId, body: "# Hello\nworld")
            context.insert(note)
            let tag = SchemaV1.Tag(name: "work", colorIndex: 2)
            context.insert(tag)
            note.tags = [tag]
            let contact = SchemaV1.Contact(id: contactId, givenName: "Ada", familyName: "Lovelace",
                                           phones: [LabeledValue(label: "mobile", value: "+1 555 0100")])
            context.insert(contact)
            try context.save()
        }

        // 2. Reopen through the production path: SchemaV2 + SafeBoxMigrationPlan.
        do {
            let v2 = try ModelContainerFactory.onDisk(storeURL: storeURL)
            let context = v2.mainContext

            let albums = try context.fetch(FetchDescriptor<Album>())
            #expect(albums.count == 1)
            #expect(albums.first?.id == albumId)
            #expect(albums.first?.name == "Trips")
            #expect(albums.first?.sortIndex == 3)
            #expect(albums.first?.deletedAt == nil)
            #expect(albums.first?.photos.count == 1)

            let photos = try context.fetch(FetchDescriptor<Photo>())
            #expect(photos.count == 1)
            #expect(photos.first?.id == photoId)
            #expect(photos.first?.width == 40)
            #expect(photos.first?.byteCount == 1_234)
            #expect(photos.first?.album?.id == albumId)
            #expect(photos.first?.deletedAt == nil)
            #expect(photos.first?.mediaType == MediaType.photo.rawValue)   // literal default written by the migration
            #expect(photos.first?.durationMs == nil)

            let notes = try context.fetch(FetchDescriptor<Note>())
            #expect(notes.count == 1)
            #expect(notes.first?.id == noteId)
            #expect(notes.first?.body == "# Hello\nworld")
            #expect(notes.first?.title == "Hello")
            #expect(notes.first?.deletedAt == nil)
            #expect(notes.first?.tags.map(\.name) == ["work"])

            let contacts = try context.fetch(FetchDescriptor<Contact>())
            #expect(contacts.count == 1)
            #expect(contacts.first?.id == contactId)
            #expect(contacts.first?.displayName == "Ada Lovelace")
            #expect(contacts.first?.phones == [LabeledValue(label: "mobile", value: "+1 555 0100")])
            #expect(contacts.first?.deletedAt == nil)

            // The repositories' live filters see the migrated rows.
            let fileStore = PhotoFileStore(rootURL: dir)
            #expect(SwiftDataPhotoRepository(container: v2, fileStore: fileStore).albums().count == 1)
            #expect(SwiftDataNoteRepository(container: v2).notes().count == 1)
            #expect(SwiftDataContactRepository(container: v2).contacts().count == 1)
        }

        // 3. A second open of the migrated store is a no-op.
        let again = try ModelContainerFactory.onDisk(storeURL: storeURL)
        #expect(try again.mainContext.fetch(FetchDescriptor<Album>()).count == 1)
        #expect(try again.mainContext.fetch(FetchDescriptor<Photo>()).count == 1)
        #expect(try again.mainContext.fetch(FetchDescriptor<Note>()).count == 1)
        #expect(try again.mainContext.fetch(FetchDescriptor<Contact>()).count == 1)
    }
}
