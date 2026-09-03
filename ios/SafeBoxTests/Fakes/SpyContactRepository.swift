import Foundation
@testable import SafeBox

/// In-memory `ContactRepository` that records every call, so view-model tests
/// can assert "one bulk call with N ids" without SwiftData. Models are created
/// un-inserted (`Contact(givenName:)`); only their scalar columns are read.
@MainActor
final class SpyContactRepository: ContactRepository {
    var liveContacts: [Contact] = []
    var trashed: [Contact] = []

    /// One entry per `delete(contacts:)` call, each carrying the ids passed.
    private(set) var deleteCalls: [[UUID]] = []
    /// One entry per `restore(ids:)` call.
    private(set) var restoreCalls: [[UUID]] = []

    @discardableResult
    func seed(_ names: [String]) -> [Contact] {
        let contacts = names.map { Contact(givenName: $0) }
        liveContacts.append(contentsOf: contacts)
        return contacts
    }

    func contacts() -> [Contact] { liveContacts }

    /// Live rows only, like the real repository: a trashed id resolves to nil
    /// so a stale route pops instead of pushing an empty screen.
    func contact(withId id: UUID) -> Contact? {
        liveContacts.first { $0.id == id }
    }

    func insert(_ contact: Contact) throws {
        liveContacts.append(contact)
    }

    func save() throws {}

    func delete(_ contact: Contact) throws {
        try delete(contacts: [contact])
    }

    func delete(contacts: [Contact]) throws {
        deleteCalls.append(contacts.map(\.id))
        let ids = Set(contacts.map(\.id))
        let stamp = Date.now
        for contact in liveContacts where ids.contains(contact.id) {
            contact.deletedAt = stamp
            trashed.append(contact)
        }
        liveContacts.removeAll { ids.contains($0.id) }
    }

    /// Same fold as the real repository (`SearchFold`), so a view-model test
    /// that exercises search behaves like the app.
    func search(_ query: String) -> [Contact] {
        let folded = SearchFold.foldedQuery(query)
        guard !folded.isEmpty else { return liveContacts }
        return liveContacts.filter {
            SearchFold.foldedContains($0.displayName, foldedQuery: folded)
        }
    }

    func trashedContacts() -> [Contact] { trashed }

    func restore(ids: [UUID]) throws {
        restoreCalls.append(ids)
        let wanted = Set(ids)
        for contact in trashed where wanted.contains(contact.id) {
            contact.deletedAt = nil
            liveContacts.append(contact)
        }
        trashed.removeAll { wanted.contains($0.id) }
    }

    func purge(ids: [UUID]) throws {
        let wanted = Set(ids)
        trashed.removeAll { wanted.contains($0.id) }
    }

    func purgeExpired(now: Date) {}
}
