import Foundation
import Observation

@MainActor
@Observable
final class NoteEditorViewModel {
    let note: Note
    private let repository: any NoteRepository

    var draftBody: String
    var showPreview = false
    private(set) var allTags: [Tag] = []

    private var autosaveTask: Task<Void, Never>?

    init(note: Note, repository: any NoteRepository) {
        self.note = note
        self.repository = repository
        self.draftBody = note.body
        self.allTags = repository.tags()
    }

    /// Autosave contract: persisted within 1 s of the last keystroke, plus a
    /// synchronous flush on editor exit and on scene-inactive.
    func bodyChanged() {
        autosaveTask?.cancel()
        autosaveTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            self?.flush()
        }
    }

    func flush() {
        autosaveTask?.cancel()
        autosaveTask = nil
        try? repository.save(note: note, body: draftBody)
    }

    // MARK: - Tags

    var noteTags: [Tag] {
        note.tags.sorted { $0.name < $1.name }
    }

    func isTagged(_ tag: Tag) -> Bool {
        note.tags.contains { $0.id == tag.id }
    }

    func toggleTag(_ tag: Tag) {
        var tags = note.tags
        if let index = tags.firstIndex(where: { $0.id == tag.id }) {
            tags.remove(at: index)
        } else {
            tags.append(tag)
        }
        try? repository.setTags(tags, on: note)
    }

    func addTag(named name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        guard let tag = try? repository.findOrCreateTag(named: trimmed) else { return }
        if !isTagged(tag) {
            toggleTag(tag)
        }
        allTags = repository.tags()
    }

    func delete() {
        autosaveTask?.cancel()
        autosaveTask = nil
        try? repository.delete(note: note)
    }
}
