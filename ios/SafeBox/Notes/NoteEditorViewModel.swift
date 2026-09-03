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
    /// Set by `delete()`. Once the note is in the trash the editor must not
    /// touch it again — the `onDisappear` flush that follows the dismiss would
    /// otherwise write the draft into a trashed row.
    private(set) var isDeleted = false

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
        guard !isDeleted else { return }
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

    /// Soft delete. Flushes the pending draft FIRST, so an Undo from the toast
    /// restores exactly the text that was on screen (not the last autosave),
    /// then freezes the editor so no later flush can reach the trashed note.
    func delete() {
        flush()
        isDeleted = true
        try? repository.delete(note: note)
    }
}
