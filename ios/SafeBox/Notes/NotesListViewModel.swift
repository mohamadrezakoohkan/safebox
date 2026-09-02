import Foundation
import Observation

@MainActor
@Observable
final class NotesListViewModel {
    let repository: any NoteRepository

    private(set) var notes: [Note] = []
    private(set) var allTags: [Tag] = []
    var searchText = ""
    var filterTag: Tag?

    init(repository: any NoteRepository) {
        self.repository = repository
    }

    func reload() {
        notes = repository.notes()
        allTags = repository.tags()
    }

    /// Sorted by updatedAt desc (repository order), contains-match search over
    /// title + body, optional tag filter.
    var visibleNotes: [Note] {
        var result = notes
        if let filterTag {
            result = result.filter { note in note.tags.contains { $0.id == filterTag.id } }
        }
        let query = searchText.trimmingCharacters(in: .whitespaces)
        if !query.isEmpty {
            result = result.filter {
                $0.title.range(of: query, options: .caseInsensitive) != nil ||
                $0.body.range(of: query, options: .caseInsensitive) != nil
            }
        }
        return result
    }

    func createNote() -> Note? {
        let note = try? repository.createNote(body: "")
        reload()
        return note
    }

    func delete(_ note: Note) {
        try? repository.delete(note: note)
        reload()
    }
}
