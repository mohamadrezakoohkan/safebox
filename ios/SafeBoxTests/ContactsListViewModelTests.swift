import Foundation
import Testing
@testable import SafeBox

/// P6 multi-select on the contacts list (decisions §6).
@MainActor
struct ContactsListViewModelTests {
    private func makeViewModel(seeding names: [String]) -> (ContactsListViewModel, SpyContactRepository, [Contact]) {
        let repo = SpyContactRepository()
        let contacts = repo.seed(names)
        let viewModel = ContactsListViewModel(repository: repo)
        viewModel.reload()
        return (viewModel, repo, contacts)
    }

    // MARK: - Selection add / remove / clear

    @Test func aFreshViewModelStartsOutsideSelectionMode() {
        // `MainTabView` builds a new view model on every unlock, so this is
        // also the state after a lock: no mode, no selection, by construction.
        let (viewModel, _, _) = makeViewModel(seeding: ["Ada", "Bob"])
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
        #expect(viewModel.selectedContacts.isEmpty)
    }

    @Test func longPressEntersSelectionModeWithThePressedRowSelected() {
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada", "Bob", "Cy"])
        viewModel.enterSelectMode(selecting: contacts[2])
        #expect(viewModel.isSelecting)
        #expect(viewModel.selection == [contacts[2].id])
    }

    @Test func toggleAddsAndRemovesWhileSelecting() {
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada", "Bob", "Cy"])
        viewModel.enterSelectMode()
        viewModel.toggleSelection(contacts[0])
        viewModel.toggleSelection(contacts[1])
        #expect(viewModel.selection == [contacts[0].id, contacts[1].id])
        viewModel.toggleSelection(contacts[0])
        #expect(viewModel.selection == [contacts[1].id])
        #expect(viewModel.isSelecting)
    }

    @Test func toggleIsIgnoredOutsideSelectionMode() {
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada"])
        viewModel.toggleSelection(contacts[0])
        #expect(viewModel.selection.isEmpty)
        #expect(!viewModel.isSelecting)
    }

    @Test func exitClearsTheSelectionAndLeavesTheMode() {
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada", "Bob"])
        viewModel.enterSelectMode(selecting: contacts[0])
        viewModel.toggleSelection(contacts[1])
        viewModel.exitSelectMode()
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
    }

    @Test func sectionsAreUnaffectedBySelectionMode() {
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada", "Bob", "Bea"])
        let before = viewModel.sections.map { ($0.key, $0.contacts.map(\.id)) }
        viewModel.enterSelectMode(selecting: contacts[1])
        let after = viewModel.sections.map { ($0.key, $0.contacts.map(\.id)) }
        #expect(before.map(\.0) == ["A", "B"])
        #expect(before.map(\.0) == after.map(\.0))
        #expect(before.map(\.1) == after.map(\.1))
    }

    @Test func reloadDropsSelectedIdsThatAreNoLongerLive() throws {
        let (viewModel, repo, contacts) = makeViewModel(seeding: ["Ada", "Bob"])
        viewModel.enterSelectMode(selecting: contacts[0])
        viewModel.toggleSelection(contacts[1])
        try repo.delete(contacts[0]) // deleted elsewhere (e.g. the detail screen)
        viewModel.reload()
        #expect(viewModel.selection == [contacts[1].id])
    }

    // MARK: - Bulk delete: one repository call with N ids

    @Test func deleteSelectedCallsTheRepositoryOnceWithEveryId() {
        let (viewModel, repo, contacts) = makeViewModel(seeding: ["Ada", "Bob", "Cy", "Dee"])
        viewModel.enterSelectMode(selecting: contacts[1])
        viewModel.toggleSelection(contacts[3])
        let expected: Set<UUID> = [contacts[1].id, contacts[3].id]

        let ids = viewModel.deleteSelected()

        #expect(repo.deleteCalls.count == 1)
        #expect(Set(repo.deleteCalls[0]) == expected)
        #expect(Set(ids) == expected)
        #expect(!viewModel.isSelecting)
        #expect(viewModel.selection.isEmpty)
        #expect(Set(viewModel.contacts.map(\.id)) == [contacts[0].id, contacts[2].id])
        #expect(Set(repo.trashedContacts().map(\.id)) == expected)
    }

    @Test func deleteSelectedWithNothingSelectedIsANoOpThatStillExits() {
        let (viewModel, repo, _) = makeViewModel(seeding: ["Ada"])
        viewModel.enterSelectMode()
        let ids = viewModel.deleteSelected()
        #expect(ids.isEmpty)
        #expect(repo.deleteCalls.isEmpty)
        #expect(!viewModel.isSelecting)
        #expect(viewModel.contacts.count == 1)
    }

    @Test func undoRestoresTheWholeBatchInOneCall() {
        let (viewModel, repo, contacts) = makeViewModel(seeding: ["Ada", "Bob", "Cy"])
        viewModel.enterSelectMode(selecting: contacts[0])
        viewModel.toggleSelection(contacts[2])
        let ids = viewModel.deleteSelected()
        #expect(viewModel.contacts.count == 1)

        viewModel.restore(ids: ids)

        #expect(repo.restoreCalls == [ids])
        #expect(Set(viewModel.contacts.map(\.id)) == Set(contacts.map(\.id)))
        #expect(repo.trashedContacts().isEmpty)
    }

    @Test func bulkDeleteAgainstSwiftDataMovesEveryContactToTheTrashWithOneStamp() throws {
        let repo = SwiftDataContactRepository(container: ModelContainerFactory.inMemory())
        let ada = Contact(givenName: "Ada")
        let bob = Contact(givenName: "Bob")
        let cy = Contact(givenName: "Cy")
        for contact in [ada, bob, cy] { try repo.insert(contact) }
        let viewModel = ContactsListViewModel(repository: repo)
        viewModel.reload()
        viewModel.enterSelectMode(selecting: ada)
        viewModel.toggleSelection(bob)

        let ids = viewModel.deleteSelected()

        #expect(Set(ids) == [ada.id, bob.id])
        #expect(viewModel.contacts.map(\.id) == [cy.id])
        let trashed = repo.trashedContacts()
        #expect(Set(trashed.map(\.id)) == [ada.id, bob.id])
        #expect(Set(trashed.compactMap(\.deletedAt)).count == 1) // one call, one stamp
    }

    // MARK: - Search field vs. selection mode (P6 review polish)

    @Test func enteringAndLeavingSelectionModeKeepsTheTypedQuery() {
        // The `.searchable` field is detached while selecting (§6), but the
        // query itself lives in the view model and survives the round trip.
        let (viewModel, _, contacts) = makeViewModel(seeding: ["Ada", "Bob"])
        viewModel.searchText = "ada"
        #expect(viewModel.visibleContacts.map(\.id) == [contacts[0].id])

        viewModel.enterSelectMode(selecting: contacts[0])
        #expect(viewModel.searchText == "ada")
        #expect(viewModel.visibleContacts.map(\.id) == [contacts[0].id])

        viewModel.exitSelectMode()
        #expect(viewModel.searchText == "ada")
        #expect(viewModel.visibleContacts.map(\.id) == [contacts[0].id])
    }
}
