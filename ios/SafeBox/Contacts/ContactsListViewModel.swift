import Foundation
import Observation

@MainActor
@Observable
final class ContactsListViewModel {
    let repository: any ContactRepository

    private(set) var contacts: [Contact] = []
    var searchText = ""

    // MARK: Selection (P6) — mirrors `AlbumGridViewModel`.
    //
    // Owned by this object, which `MainTabView` creates and which is torn down
    // with the vault on lock, so selection resets on lock by construction; the
    // only in-life reset is `exitSelectMode()`.

    private(set) var isSelecting = false
    private(set) var selection: Set<UUID> = []

    init(repository: any ContactRepository) {
        self.repository = repository
    }

    func reload() {
        contacts = repository.contacts()
        if !selection.isEmpty {
            selection.formIntersection(contacts.map(\.id))
        }
    }

    var visibleContacts: [Contact] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return contacts }
        return repository.search(query)
    }

    /// Alphabetical sections, familyName-first sort keys, "#" bucket last.
    /// Section headers are never selectable — only the rows inside them.
    var sections: [(key: String, contacts: [Contact])] {
        let grouped = Dictionary(grouping: visibleContacts) { $0.sectionKey }
        return grouped.keys.sorted { a, b in
            if a == "#" { return false }
            if b == "#" { return true }
            return a < b
        }.map { (key: $0, contacts: grouped[$0] ?? []) }
    }

    /// Soft delete (the contact goes to Recently deleted).
    func delete(_ contact: Contact) {
        try? repository.delete(contact)
        reload()
    }

    /// Undo path — restores a whole batch in one call.
    func restore(ids: [UUID]) {
        try? repository.restore(ids: ids)
        reload()
    }

    // MARK: - Selection

    /// The selected LIVE contacts (a selected contact hidden by the current
    /// search is still selected — the count and the delete agree).
    var selectedContacts: [Contact] {
        contacts.filter { selection.contains($0.id) }
    }

    /// Long-press entry: enters selection mode with the pressed row selected.
    func enterSelectMode(selecting contact: Contact? = nil) {
        isSelecting = true
        if let contact {
            selection.insert(contact.id)
        }
    }

    func toggleSelection(_ contact: Contact) {
        guard isSelecting else { return }
        if selection.contains(contact.id) {
            selection.remove(contact.id)
        } else {
            selection.insert(contact.id)
        }
    }

    /// Cancel: leaves selection mode and clears the selection.
    func exitSelectMode() {
        isSelecting = false
        selection = []
    }

    /// Bulk soft delete: ONE repository call carrying every selected id
    /// (decisions §6), then selection mode ends. Returns the trashed ids for
    /// the undo toast; empty when nothing was selected.
    @discardableResult
    func deleteSelected() -> [UUID] {
        let targets = selectedContacts
        exitSelectMode()
        guard !targets.isEmpty else { return [] }
        let ids = targets.map(\.id)
        try? repository.delete(contacts: targets)
        reload()
        return ids
    }
}
