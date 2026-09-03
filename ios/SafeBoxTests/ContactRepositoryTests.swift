import Foundation
import Testing
@testable import SafeBox

@MainActor
struct ContactRepositoryTests {
    private func makeRepository() -> SwiftDataContactRepository {
        SwiftDataContactRepository(container: ModelContainerFactory.inMemory())
    }

    @Test func crud() throws {
        let repo = makeRepository()
        let contact = Contact(givenName: "Ada", familyName: "Lovelace",
                              phones: [LabeledValue(label: "mobile", value: "+34600111222")],
                              emails: [LabeledValue(label: "work", value: "ada@analytical.engine")])
        try repo.insert(contact)
        #expect(repo.contacts().count == 1)

        contact.familyName = "Byron"
        try repo.save()
        #expect(repo.contacts().first?.familyName == "Byron")

        try repo.delete(contact)
        #expect(repo.contacts().isEmpty)
    }

    @Test func organizationOnlyContactIsLegal() throws {
        let repo = makeRepository()
        let contact = Contact(organization: "Acme Corp")
        try repo.insert(contact)
        #expect(repo.contacts().first?.displayName == "Acme Corp")
        #expect(repo.contacts().first?.sectionKey == "A")
    }

    @Test func familyNameFirstSortWithFallbacks() throws {
        let repo = makeRepository()
        try repo.insert(Contact(givenName: "Zoe", familyName: "Adams"))
        try repo.insert(Contact(givenName: "Bob"))                 // falls back to givenName
        try repo.insert(Contact(organization: "Citrus"))           // falls back to org
        let sorted = repo.contacts().map(\.sortKey)
        #expect(sorted == ["adams", "bob", "citrus"])
    }

    @Test func nonLetterBucketsUnderHash() throws {
        let repo = makeRepository()
        let numeric = Contact(organization: "123 Industries")
        try repo.insert(numeric)
        #expect(numeric.sectionKey == "#")
    }

    @Test func searchMatchesNameOrgPhoneEmail() throws {
        let repo = makeRepository()
        try repo.insert(Contact(givenName: "Grace", familyName: "Hopper",
                                organization: "Navy",
                                phones: [LabeledValue(label: "work", value: "555-0199")],
                                emails: [LabeledValue(label: "work", value: "grace@usn.mil")]))
        try repo.insert(Contact(givenName: "Alan", familyName: "Turing"))

        #expect(repo.search("grace").count == 1)     // given name
        #expect(repo.search("hopper").count == 1)    // family name
        #expect(repo.search("navy").count == 1)      // organization
        #expect(repo.search("0199").count == 1)      // phone
        #expect(repo.search("usn.mil").count == 1)   // email
        #expect(repo.search("nonexistent").isEmpty)
        #expect(repo.search("").count == 2)          // empty query → all
    }

    /// The contacts search folds through `SearchFold` (N1), the vault's single
    /// fold — so it agrees with global search on every field, not just names.
    @Test func searchUsesTheSharedFoldOnEveryField() throws {
        let repo = makeRepository()
        try repo.insert(Contact(givenName: "Zoë", familyName: "Meyer",
                                organization: "Café Éclair",
                                emails: [LabeledValue(label: "work", value: "ZOE@Example.com")]))
        try repo.insert(Contact(givenName: "Alan", familyName: "Turing"))

        #expect(repo.search("zoe").count == 1)         // unaccented query, accented name
        #expect(repo.search("cafe eclair").count == 1) // unaccented query, accented org
        #expect(repo.search("zoe@example").count == 1) // case-insensitive email
        #expect(repo.search("Álan").count == 1)        // accented query, plain name
        #expect(repo.search("  ").count == 2)          // whitespace-only → all
    }

    @Test func diacriticInsensitiveSortKey() throws {
        let contact = Contact(familyName: "Álvarez")
        #expect(contact.sortKey == "alvarez")
        #expect(contact.sectionKey == "A")
    }

    // MARK: - Soft delete (P3)

    @Test func deleteIsSoftAndHidesTheContactFromListAndSearch() throws {
        let repo = makeRepository()
        let contact = Contact(givenName: "Grace", familyName: "Hopper")
        try repo.insert(contact)
        try repo.insert(Contact(givenName: "Alan", familyName: "Turing"))

        try repo.delete(contact)
        #expect(repo.contacts().map(\.displayName) == ["Alan Turing"])
        #expect(repo.search("grace").isEmpty)                  // search excludes trashed rows
        #expect(repo.trashedContacts().map(\.id) == [contact.id])
        #expect(contact.deletedAt != nil)
    }

    @Test func restoreBringsTheContactBackAndPurgeRemovesIt() throws {
        let repo = makeRepository()
        let contact = Contact(givenName: "Ada", familyName: "Lovelace",
                              phones: [LabeledValue(label: "mobile", value: "+34600111222")])
        try repo.insert(contact)
        try repo.delete(contact)

        try repo.restore(ids: [contact.id])
        #expect(repo.contacts().map(\.id) == [contact.id])
        #expect(repo.trashedContacts().isEmpty)
        #expect(repo.contacts().first?.phones.first?.value == "+34600111222")

        try repo.delete(contact)
        try repo.purge(ids: [contact.id])
        #expect(repo.contacts().isEmpty)
        #expect(repo.trashedContacts().isEmpty)
    }

    @Test func bulkDeleteTrashesEveryContactWithOneStamp() throws {
        let repo = makeRepository()
        let a = Contact(givenName: "A")
        let b = Contact(givenName: "B")
        let c = Contact(givenName: "C")
        for contact in [a, b, c] { try repo.insert(contact) }
        try repo.delete(contacts: [a, b])
        #expect(repo.contacts().map(\.id) == [c.id])
        #expect(Set(repo.trashedContacts().map(\.id)) == [a.id, b.id])
        #expect(a.deletedAt == b.deletedAt)
    }

    @Test func purgeExpiredRemovesOnlyExpiredContacts() throws {
        let repo = makeRepository()
        let expired = Contact(givenName: "Old")
        let fresh = Contact(givenName: "New")
        try repo.insert(expired)
        try repo.insert(fresh)
        try repo.delete(contacts: [expired, fresh])
        expired.deletedAt = Date.now.addingTimeInterval(-31 * 86_400)

        repo.purgeExpired(now: .now)

        #expect(repo.trashedContacts().map(\.id) == [fresh.id])
        #expect(repo.contacts().isEmpty)
    }

    /// A `ContactsRoute` can outlive the contact it points at (Delete now in
    /// Recently deleted, expiry, Erase everything). The by-id lookup is
    /// LIVE-only so the destination resolves to nil and the stack pops.
    @Test func lookupByIdSkipsTrashedContactsAndReturnsThemAfterRestore() throws {
        let repo = makeRepository()
        let contact = Contact(givenName: "Routed")
        try repo.insert(contact)
        #expect(repo.contact(withId: contact.id)?.id == contact.id)

        try repo.delete(contact)
        #expect(repo.contact(withId: contact.id) == nil)

        try repo.restore(ids: [contact.id])
        #expect(repo.contact(withId: contact.id)?.id == contact.id)

        try repo.delete(contact)
        try repo.purge(ids: [contact.id])
        #expect(repo.contact(withId: contact.id) == nil)
        #expect(repo.contact(withId: UUID()) == nil)
    }
}
