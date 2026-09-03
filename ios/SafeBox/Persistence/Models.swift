import Foundation
import SwiftData

// iOS 17-era SwiftData API only: @Attribute(.unique), @Relationship,
// #Predicate, FetchDescriptor. No #Unique/#Index (iOS 18-only).

/// The live models. `SchemaV2` adds the iteration-2 columns (decisions §0) on
/// top of the `SchemaV1` snapshot: `deletedAt` on every entity (P3 soft delete)
/// and `mediaType` / `durationMs` on photos (N3 video; stored now, read later).
/// Code refers to the models through the file-scope typealiases below.
enum SchemaV2: VersionedSchema {
    static let versionIdentifier = Schema.Version(2, 0, 0)
    static var models: [any PersistentModel.Type] {
        [Album.self, Photo.self, Note.self, Tag.self, Contact.self]
    }

    @Model
    final class Album {
        @Attribute(.unique) var id: UUID
        var name: String
        var createdAt: Date
        var sortIndex: Int
        /// Soft-delete stamp (P3). `nil` = live. Album and the live photos it
        /// held when trashed share the same instant, so a restore can tell them
        /// from photos trashed individually earlier.
        var deletedAt: Date?
        // Cascade removes rows only — file deletion is the repository's job.
        @Relationship(deleteRule: .cascade, inverse: \Photo.album) var photos: [Photo]

        init(id: UUID = UUID(), name: String, createdAt: Date = .now, sortIndex: Int) {
            self.id = id
            self.name = name
            self.createdAt = createdAt
            self.sortIndex = sortIndex
            self.deletedAt = nil
            self.photos = []
        }
    }

    @Model
    final class Photo {
        @Attribute(.unique) var id: UUID
        var fileName: String        // <uuid>.<real extension>
        var thumbFileName: String   // <uuid>.jpg
        var mimeType: String
        var width: Int
        var height: Int
        var byteCount: Int
        var importedAt: Date
        var sortIndex: Int          // import order; grid ordering key
        /// Soft-delete stamp (P3). `nil` = live.
        var deletedAt: Date?
        /// `MediaType.photo.rawValue` or `MediaType.video.rawValue` (N3). The
        /// literal default is what the lightweight migration writes into
        /// existing v1 rows.
        var mediaType: String = MediaType.photo.rawValue
        /// Videos only (N3); `nil` for stills.
        var durationMs: Int?
        // Required at the domain level (every photo belongs to exactly one album);
        // optional in the model for SwiftData relationship mechanics.
        var album: Album?

        init(id: UUID = UUID(), fileName: String, thumbFileName: String, mimeType: String,
             width: Int, height: Int, byteCount: Int, importedAt: Date = .now, sortIndex: Int,
             mediaType: String = MediaType.photo.rawValue, durationMs: Int? = nil, album: Album) {
            self.id = id
            self.fileName = fileName
            self.thumbFileName = thumbFileName
            self.mimeType = mimeType
            self.width = width
            self.height = height
            self.byteCount = byteCount
            self.importedAt = importedAt
            self.sortIndex = sortIndex
            self.deletedAt = nil
            self.mediaType = mediaType
            self.durationMs = durationMs
            self.album = album
        }
    }

    @Model
    final class Note {
        @Attribute(.unique) var id: UUID
        var title: String    // derived, denormalized: first non-empty line, markdown-stripped
        var snippet: String  // derived, denormalized: following lines, title excluded
        var body: String     // raw markdown — the single source of truth
        var createdAt: Date
        var updatedAt: Date
        /// Soft-delete stamp (P3). `nil` = live.
        var deletedAt: Date?
        var tags: [Tag]

        init(id: UUID = UUID(), body: String = "", createdAt: Date = .now, updatedAt: Date = .now) {
            self.id = id
            self.body = body
            let derived = NoteDerivation.derive(from: body)
            self.title = derived.title
            self.snippet = derived.snippet
            self.createdAt = createdAt
            self.updatedAt = updatedAt
            self.deletedAt = nil
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
        /// Soft-delete stamp (P3). `nil` = live.
        var deletedAt: Date?

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
            self.deletedAt = nil
        }
    }
}

typealias Album = SchemaV2.Album
typealias Photo = SchemaV2.Photo
typealias Note = SchemaV2.Note
typealias Tag = SchemaV2.Tag
typealias Contact = SchemaV2.Contact

/// Raw values are the shared cross-platform column values (decisions §0).
enum MediaType: String, Sendable {
    case photo
    case video
}

extension Photo {
    /// N3. An unknown/corrupt `mediaType` reads as a still — the safe default,
    /// since a still renders a video's poster frame harmlessly while the
    /// reverse would hand a JPEG to a player.
    var isVideo: Bool { mediaType == MediaType.video.rawValue }
}

/// Small codable multi-value entry ({label, value}) for phones/emails.
/// SwiftData #Predicate cannot query INTO codable attributes — contact search
/// is therefore in-memory (fine at vault scale).
struct LabeledValue: Codable, Hashable, Sendable {
    var label: String
    var value: String
}

// MARK: - Contact derivation (idea plan §3.3)

extension Contact {
    var displayName: String {
        let name = [givenName, familyName].compactMap { $0?.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }.joined(separator: " ")
        if !name.isEmpty { return name }
        return organization?.trimmingCharacters(in: .whitespaces) ?? ""
    }

    /// familyName-first sort key with fallbacks: familyName → givenName →
    /// organization. Folded through `SearchFold`, the vault's ONE folding
    /// implementation (N1).
    var sortKey: String {
        for candidate in [familyName, givenName, organization] {
            if let c = candidate?.trimmingCharacters(in: .whitespaces), !c.isEmpty {
                return SearchFold.fold(c)
            }
        }
        return ""
    }

    /// Section header letter; non-letter/empty keys bucket under "#".
    var sectionKey: String {
        guard let first = sortKey.first, first.isLetter else { return "#" }
        return String(first).uppercased()
    }
}
