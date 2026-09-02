import Foundation
import SwiftData

@MainActor
protocol ContactRepository: AnyObject {
    func contacts() -> [Contact]
    func insert(_ contact: Contact) throws
    func save() throws
    func delete(_ contact: Contact) throws
    /// In-memory search over name + organization + phone numbers + email
    /// addresses (SwiftData predicates cannot reach into codable attributes).
    func search(_ query: String) -> [Contact]
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
        let descriptor = FetchDescriptor<Contact>()
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

    func insert(_ contact: Contact) throws {
        context.insert(contact)
        try context.save()
    }

    func save() throws {
        try context.save()
    }

    func delete(_ contact: Contact) throws {
        context.delete(contact)
        try context.save()
    }

    func search(_ query: String) -> [Contact] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return contacts() }
        return contacts().filter { contact in
            let haystacks: [String] = [
                contact.givenName ?? "",
                contact.familyName ?? "",
                contact.displayName,
                contact.organization ?? "",
            ]
            + contact.phones.map(\.value)
            + contact.emails.map(\.value)
            return haystacks.contains { $0.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil }
        }
    }
}
