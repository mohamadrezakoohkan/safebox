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

    @Test func diacriticInsensitiveSortKey() throws {
        let contact = Contact(familyName: "Álvarez")
        #expect(contact.sortKey == "alvarez")
        #expect(contact.sectionKey == "A")
    }
}
