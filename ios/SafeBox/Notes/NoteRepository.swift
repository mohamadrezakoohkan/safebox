import Foundation
import SwiftData

@MainActor
protocol NoteRepository: AnyObject {
    func notes() -> [Note]
    @discardableResult
    func createNote(body: String) throws -> Note
    func save(note: Note, body: String) throws
    func delete(note: Note) throws
    func tags() -> [Tag]
    @discardableResult
    func findOrCreateTag(named name: String) throws -> Tag
    func setTags(_ tags: [Tag], on note: Note) throws
}

@MainActor
final class SwiftDataNoteRepository: NoteRepository {
    // The container must be retained: ModelContext references it weakly, and a
    // deallocated container traps on the first model operation.
    private let container: ModelContainer
    private let context: ModelContext

    init(container: ModelContainer) {
        self.container = container
        self.context = container.mainContext
    }

    func notes() -> [Note] {
        let descriptor = FetchDescriptor<Note>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }

    @discardableResult
    func createNote(body: String = "") throws -> Note {
        let note = Note(body: body)
        context.insert(note)
        try context.save()
        return note
    }

    func save(note: Note, body: String) throws {
        guard note.body != body else { return }
        note.body = body
        let derived = NoteDerivation.derive(from: body)
        note.title = derived.title
        note.snippet = derived.snippet
        note.updatedAt = .now // bumps only on real change (guarded above)
        try context.save()
    }

    func delete(note: Note) throws {
        context.delete(note)
        try context.save()
    }

    func tags() -> [Tag] {
        let descriptor = FetchDescriptor<Tag>(sortBy: [SortDescriptor(\.name)])
        return (try? context.fetch(descriptor)) ?? []
    }

    @discardableResult
    func findOrCreateTag(named name: String) throws -> Tag {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        // Dedupe by case-insensitive name.
        if let existing = tags().first(where: { $0.name.caseInsensitiveCompare(trimmed) == .orderedSame }) {
            return existing
        }
        let colorIndex = tags().count % 6
        let tag = Tag(name: trimmed, colorIndex: colorIndex)
        context.insert(tag)
        try context.save()
        return tag
    }

    func setTags(_ tags: [Tag], on note: Note) throws {
        note.tags = tags
        try context.save()
    }
}
