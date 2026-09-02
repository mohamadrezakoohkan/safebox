import Foundation
import Observation

@MainActor
@Observable
final class ContactsListViewModel {
    let repository: any ContactRepository

    private(set) var contacts: [Contact] = []
    var searchText = ""

    init(repository: any ContactRepository) {
        self.repository = repository
    }

    func reload() {
        contacts = repository.contacts()
    }

    var visibleContacts: [Contact] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return contacts }
        return repository.search(query)
    }

    /// Alphabetical sections, familyName-first sort keys, "#" bucket last.
    var sections: [(key: String, contacts: [Contact])] {
        let grouped = Dictionary(grouping: visibleContacts) { $0.sectionKey }
        return grouped.keys.sorted { a, b in
            if a == "#" { return false }
            if b == "#" { return true }
            return a < b
        }.map { (key: $0, contacts: grouped[$0] ?? []) }
    }

    func delete(_ contact: Contact) {
        try? repository.delete(contact)
        reload()
    }
}
