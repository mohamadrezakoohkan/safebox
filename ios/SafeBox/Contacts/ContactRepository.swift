import Foundation
import SwiftData

/// Contacts. Since P3 `delete` is a SOFT delete (`deletedAt = now`);
/// `contacts()` and `search` return live rows only.
@MainActor
protocol ContactRepository: AnyObject {
    func contacts() -> [Contact]
    /// By-id lookup for a navigation route (N1): a path carries ids, never
    /// models, so the destination re-fetches. `nil` once the row is gone.
    func contact(withId id: UUID) -> Contact?
    func insert(_ contact: Contact) throws
    func save() throws
    func delete(_ contact: Contact) throws
    /// Bulk soft delete in one call (P6 multi-select).
    func delete(contacts: [Contact]) throws
    /// In-memory search over name + organization + phone numbers + email
    /// addresses (SwiftData predicates cannot reach into codable attributes).
    func search(_ query: String) -> [Contact]

    // MARK: Trash (P3)

    func trashedContacts() -> [Contact]
    func restore(ids: [UUID]) throws
    /// Hard delete of the rows (contacts own no files).
    func purge(ids: [UUID]) throws
    func purgeExpired(now: Date)
}

@MainActor
final class SwiftDataContactRepository: ContactRepository {
    // The container must be retained: ModelContext references it weakly, and a
    // deallocated container traps on the first model operation.
    private let container: ModelContainer
    private let context: ModelContext

    init(container: ModelContainer) {
        self.container = container
        self.context = container.mainContext
    }

    func contacts() -> [Contact] {
        let descriptor = FetchDescriptor<Contact>(predicate: #Predicate { $0.deletedAt == nil })
        let all = (try? context.fetch(descriptor)) ?? []
        return all.sorted {
            let a = $0.sortKey
            let b = $1.sortKey
            if a == b { return $0.displayName < $1.displayName }
            if a.isEmpty { return false }
            if b.isEmpty { return true }
            return a < b
        }
    }

    /// LIVE rows only: a route can outlive the contact it points at (Delete now
    /// in Recently deleted, expiry, Erase everything), and a trashed contact
    /// must not render as a pushed detail.
    func contact(withId id: UUID) -> Contact? {
        var descriptor = FetchDescriptor<Contact>(
            predicate: #Predicate { $0.id == id && $0.deletedAt == nil }
        )
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first
    }

    func insert(_ contact: Contact) throws {
        context.insert(contact)
        try context.save()
    }

    func save() throws {
        try context.save()
    }

    func delete(_ contact: Contact) throws {
        try delete(contacts: [contact])
    }

    func delete(contacts: [Contact]) throws {
        let stamp = Date.now
        for contact in contacts {
            contact.deletedAt = stamp
        }
        try context.save()
    }

    /// Matching goes through `SearchFold` — the vault's single fold (N1), so
    /// this per-tab search and global search can never disagree.
    func search(_ query: String) -> [Contact] {
        let folded = SearchFold.foldedQuery(query)
        guard !folded.isEmpty else { return contacts() }
        return contacts().filter { contact in
            let haystacks: [String] = [
                contact.givenName ?? "",
                contact.familyName ?? "",
                contact.displayName,
                contact.organization ?? "",
            ]
            + contact.phones.map(\.value)
            + contact.emails.map(\.value)
            return SearchFold.foldedContainsAny(haystacks, foldedQuery: folded)
        }
    }

    // MARK: - Trash

    func trashedContacts() -> [Contact] {
        let descriptor = FetchDescriptor<Contact>(predicate: #Predicate { $0.deletedAt != nil })
        return (try? context.fetch(descriptor)) ?? []
    }

    func restore(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for contact in fetch(ids: ids) {
            contact.deletedAt = nil
        }
        try context.save()
    }

    func purge(ids: [UUID]) throws {
        guard !ids.isEmpty else { return }
        for contact in fetch(ids: ids) {
            context.delete(contact)
        }
        try context.save()
    }

    func purgeExpired(now: Date) {
        let expired = trashedContacts()
            .filter { TrashPolicy.isExpired(deletedAt: $0.deletedAt ?? now, now: now) }
            .map(\.id)
        try? purge(ids: expired)
    }

    private func fetch(ids: [UUID]) -> [Contact] {
        let descriptor = FetchDescriptor<Contact>(predicate: #Predicate { ids.contains($0.id) })
        return (try? context.fetch(descriptor)) ?? []
    }
}
