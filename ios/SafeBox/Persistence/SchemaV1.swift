import Foundation
import SwiftData

/// Verbatim snapshot of the iteration-1 models. Frozen: never edit these
/// classes — `SafeBoxMigrationPlan` needs the exact shape users' v1 stores were
/// written with so the lightweight `V1 → V2` stage can be inferred. Live code
/// uses the file-scope typealiases in `Models.swift` (`SchemaV2`).
enum SchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] {
        [Album.self, Photo.self, Note.self, Tag.self, Contact.self]
    }

    @Model
    final class Album {
        @Attribute(.unique) var id: UUID
        var name: String
        var createdAt: Date
        var sortIndex: Int
        @Relationship(deleteRule: .cascade, inverse: \Photo.album) var photos: [Photo]

        init(id: UUID = UUID(), name: String, createdAt: Date = .now, sortIndex: Int) {
            self.id = id
            self.name = name
            self.createdAt = createdAt
            self.sortIndex = sortIndex
            self.photos = []
        }
    }

    @Model
    final class Photo {
        @Attribute(.unique) var id: UUID
        var fileName: String
        var thumbFileName: String
        var mimeType: String
        var width: Int
        var height: Int
        var byteCount: Int
        var importedAt: Date
        var sortIndex: Int
        var album: Album?

        init(id: UUID = UUID(), fileName: String, thumbFileName: String, mimeType: String,
             width: Int, height: Int, byteCount: Int, importedAt: Date = .now, sortIndex: Int, album: Album) {
            self.id = id
            self.fileName = fileName
            self.thumbFileName = thumbFileName
            self.mimeType = mimeType
            self.width = width
            self.height = height
            self.byteCount = byteCount
            self.importedAt = importedAt
            self.sortIndex = sortIndex
            self.album = album
        }
    }

    @Model
    final class Note {
        @Attribute(.unique) var id: UUID
        var title: String
        var snippet: String
        var body: String
        var createdAt: Date
        var updatedAt: Date
        var tags: [Tag]

        init(id: UUID = UUID(), body: String = "", createdAt: Date = .now, updatedAt: Date = .now) {
            self.id = id
            self.body = body
            let derived = NoteDerivation.derive(from: body)
            self.title = derived.title
            self.snippet = derived.snippet
            self.createdAt = createdAt
            self.updatedAt = updatedAt
            self.tags = []
        }
    }

    @Model
    final class Tag {
        @Attribute(.unique) var id: UUID
        @Attribute(.unique) var name: String
        var colorIndex: Int
        @Relationship(deleteRule: .nullify, inverse: \Note.tags) var notes: [Note]

        init(id: UUID = UUID(), name: String, colorIndex: Int) {
            self.id = id
            self.name = name
            self.colorIndex = colorIndex
            self.notes = []
        }
    }

    @Model
    final class Contact {
        @Attribute(.unique) var id: UUID
        var givenName: String?
        var familyName: String?
        var organization: String?
        var phones: [LabeledValue]
        var emails: [LabeledValue]
        var address: String?
        var notes: String?
        var createdAt: Date
        var updatedAt: Date

        init(id: UUID = UUID(), givenName: String? = nil, familyName: String? = nil,
             organization: String? = nil, phones: [LabeledValue] = [], emails: [LabeledValue] = [],
             address: String? = nil, notes: String? = nil, createdAt: Date = .now, updatedAt: Date = .now) {
            self.id = id
            self.givenName = givenName
            self.familyName = familyName
            self.organization = organization
            self.phones = phones
            self.emails = emails
            self.address = address
            self.notes = notes
            self.createdAt = createdAt
            self.updatedAt = updatedAt
        }
    }
}
