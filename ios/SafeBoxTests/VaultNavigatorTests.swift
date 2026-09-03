import Foundation
import Testing
@testable import SafeBox

/// The cross-tab navigation contract (decisions §7): tapping a result dismisses
/// search, selects the target tab, resets that tab's back stack to its root
/// list, and pushes the detail.
@MainActor
struct VaultNavigatorTests {
    private func result(_ kind: SearchResult.Kind, _ id: UUID = UUID()) -> SearchResult {
        SearchResult(kind: kind, id: id, title: "title", subtitle: "subtitle")
    }

    // MARK: - The pure mapping

    @Test func albumResultsMapToTheGalleryPhotoGrid() {
        let id = UUID()
        let destination = VaultRouting.destination(for: result(.album, id))
        #expect(destination == .gallery(.album(id)))
        #expect(destination.tab == .gallery)
    }

    @Test func noteResultsMapToTheNoteEditor() {
        let id = UUID()
        let destination = VaultRouting.destination(for: result(.note, id))
        #expect(destination == .notes(.note(id)))
        #expect(destination.tab == .notes)
    }

    @Test func contactResultsMapToTheContactDetail() {
        let id = UUID()
        let destination = VaultRouting.destination(for: result(.contact, id))
        #expect(destination == .contacts(.contact(id)))
        #expect(destination.tab == .contacts)
    }

    @Test func everyResultKindHasADestination() {
        // Guards against a new `SearchResult.Kind` arriving without a route.
        for kind in SearchResult.Kind.allCases {
            let destination = VaultRouting.destination(for: result(kind))
            #expect(destination.tab != .settings)
        }
    }

    // MARK: - Applying it

    @Test func openingAnAlbumSelectsGalleryAndResetsItsStack() {
        let navigator = VaultNavigator()
        navigator.selectedTab = .notes
        // A stale, deeper stack from earlier browsing.
        navigator.galleryPath = [.album(UUID()), .album(UUID())]

        let id = UUID()
        navigator.open(result(.album, id))

        #expect(navigator.selectedTab == .gallery)
        #expect(navigator.galleryPath == [.album(id)])
    }

    @Test func openingANoteSelectsNotesAndPushesTheEditor() {
        let navigator = VaultNavigator()
        let id = UUID()
        navigator.open(result(.note, id))
        #expect(navigator.selectedTab == .notes)
        #expect(navigator.notesPath == [.note(id)])
    }

    @Test func openingAContactSelectsContactsAndPushesTheDetail() {
        let navigator = VaultNavigator()
        let id = UUID()
        navigator.open(result(.contact, id))
        #expect(navigator.selectedTab == .contacts)
        #expect(navigator.contactsPath == [.contact(id)])
    }

    @Test func backFromTheDetailLandsOnTheTabsList() {
        let navigator = VaultNavigator()
        navigator.open(result(.note))
        // Exactly one route above the root, so one Back reaches the list.
        #expect(navigator.notesPath.count == 1)
        navigator.notesPath.removeLast()
        #expect(navigator.notesPath.isEmpty)
    }

    @Test func openingOneTabLeavesTheOtherStacksAlone() {
        let navigator = VaultNavigator()
        let albumId = UUID()
        let contactId = UUID()
        navigator.galleryPath = [.album(albumId)]
        navigator.contactsPath = [.contact(contactId)]

        navigator.open(result(.note))

        #expect(navigator.galleryPath == [.album(albumId)])
        #expect(navigator.contactsPath == [.contact(contactId)])
    }

    @Test func openingAResultDismissesSearch() {
        let navigator = VaultNavigator()
        navigator.presentSearch()
        #expect(navigator.isSearchPresented)
        navigator.open(result(.album))
        #expect(!navigator.isSearchPresented)
    }

    @Test func aFreshNavigatorStartsOnGalleryWithEmptyStacks() {
        // The post-unlock state: `MainTabView` builds a new navigator every
        // unlock, so nothing survives a lock.
        let navigator = VaultNavigator()
        #expect(navigator.selectedTab == .gallery)
        #expect(navigator.galleryPath.isEmpty)
        #expect(navigator.notesPath.isEmpty)
        #expect(navigator.contactsPath.isEmpty)
        #expect(!navigator.isSearchPresented)
    }

    // MARK: - Dropping a route whose row is gone (N1 review polish)

    @Test func dismissingARoutePopsBackToTheTabsList() {
        // A route outlives its row: purge from Recently deleted, expiry, or
        // Erase everything while the detail is pushed. The destination resolves
        // to nil and asks the navigator to pop rather than render an empty
        // screen.
        let navigator = VaultNavigator()
        let noteId = UUID()
        navigator.open(.notes(.note(noteId)))
        #expect(navigator.notesPath == [.note(noteId)])

        navigator.dismiss(.notes(.note(noteId)))
        #expect(navigator.notesPath.isEmpty)
        #expect(navigator.selectedTab == .notes) // the tab stays selected
    }

    @Test func dismissingLeavesOtherRoutesAndOtherTabsAlone() {
        let navigator = VaultNavigator()
        let contactId = UUID()
        let albumId = UUID()
        navigator.open(.gallery(.album(albumId)))
        navigator.open(.contacts(.contact(contactId)))

        // A route that is not on its path is a no-op…
        navigator.dismiss(.contacts(.contact(UUID())))
        #expect(navigator.contactsPath == [.contact(contactId)])

        // …and popping one tab never touches another's stack.
        navigator.dismiss(.contacts(.contact(contactId)))
        #expect(navigator.contactsPath.isEmpty)
        #expect(navigator.galleryPath == [.album(albumId)])
    }

    @Test func tabsCarryTheSharedCopyIds() {
        #expect(VaultTab.gallery.title == VaultCopy.vaultTabGallery)
        #expect(VaultTab.notes.title == VaultCopy.vaultTabNotes)
        #expect(VaultTab.contacts.title == VaultCopy.vaultTabContacts)
        #expect(VaultTab.settings.title == VaultCopy.vaultTabSettings)
        #expect(VaultTab.allCases.count == 4)
    }
}
