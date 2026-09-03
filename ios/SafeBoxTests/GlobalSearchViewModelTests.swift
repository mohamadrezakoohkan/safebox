import Foundation
import SwiftData
import Testing
@testable import SafeBox

/// Global search across notes, contacts and albums (decisions §7).
@MainActor
struct GlobalSearchViewModelTests {
    private struct Stack {
        let photos: SwiftDataPhotoRepository
        let notes: SwiftDataNoteRepository
        let contacts: SwiftDataContactRepository
        let root: URL
    }

    /// All three repositories over ONE in-memory store, the way `AppContainer`
    /// wires them.
    private func makeStack() -> Stack {
        let container = ModelContainerFactory.inMemory()
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxTests-\(UUID().uuidString)", isDirectory: true)
        return Stack(photos: SwiftDataPhotoRepository(container: container,
                                                      fileStore: PhotoFileStore(rootURL: root)),
                     notes: SwiftDataNoteRepository(container: container),
                     contacts: SwiftDataContactRepository(container: container),
                     root: root)
    }

    private func makeViewModel(_ stack: Stack,
                               debounce: Duration = .milliseconds(0)) -> GlobalSearchViewModel {
        GlobalSearchViewModel(photoRepository: stack.photos,
                              noteRepository: stack.notes,
                              contactRepository: stack.contacts,
                              debounce: debounce)
    }

    /// Seeds one of each type, all matching "zoe" in a different way.
    @discardableResult
    private func seed(_ stack: Stack) throws -> (album: Album, note: Note, contact: Contact) {
        let album = try stack.photos.createAlbum(name: "Zoë holiday")
        let note = try stack.notes.createNote(body: "# Dinner with Zoe\nbook a table")
        let contact = Contact(givenName: "Zoë", familyName: "Meyer",
                              organization: "Meyer & Co",
                              phones: [LabeledValue(label: "mobile", value: "+44 7700 900123")],
                              emails: [LabeledValue(label: "work", value: "zm@example.com")])
        try stack.contacts.insert(contact)
        return (album, note, contact)
    }

    // MARK: - Matching across types

    @Test func aQueryMatchesEveryTypeItShould() throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        viewModel.setQuery("zoe")
        viewModel.search()

        #expect(viewModel.results.albums.map(\.id) == [seeded.album.id])
        #expect(viewModel.results.notes.map(\.id) == [seeded.note.id])
        #expect(viewModel.results.contacts.map(\.id) == [seeded.contact.id])
        #expect(viewModel.results.count == 3)
    }

    @Test func resultsLandInTheirOwnSection() throws {
        let stack = makeStack()
        try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()
        viewModel.setQuery("zoe")
        viewModel.search()

        #expect(viewModel.results.albums.allSatisfy { $0.kind == .album })
        #expect(viewModel.results.notes.allSatisfy { $0.kind == .note })
        #expect(viewModel.results.contacts.allSatisfy { $0.kind == .contact })
        // Section order Albums → Notes → Contacts (decisions §7).
        #expect(viewModel.results.all.map(\.kind) == [.album, .note, .contact])
    }

    @Test func noteBodyAndTitleAreSearchable() throws {
        let stack = makeStack()
        let note = try stack.notes.createNote(body: "# Roadmap\nship the vault in June")
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        viewModel.setQuery("roadmap")
        viewModel.search()
        #expect(viewModel.results.notes.map(\.id) == [note.id])

        viewModel.setQuery("ship the vault")
        viewModel.search()
        #expect(viewModel.results.notes.map(\.id) == [note.id])
    }

    @Test func noteTagNamesAreSearchable() throws {
        let stack = makeStack()
        let note = try stack.notes.createNote(body: "nothing quotable here")
        let tag = try stack.notes.findOrCreateTag(named: "Voyages")
        try stack.notes.setTags([tag], on: note)

        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()
        viewModel.setQuery("voyage")
        viewModel.search()

        #expect(viewModel.results.notes.map(\.id) == [note.id])
    }

    @Test func contactOrganizationPhoneAndEmailAreSearchable() throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        for query in ["meyer & co", "900123", "zm@example.com"] {
            viewModel.setQuery(query)
            viewModel.search()
            #expect(viewModel.results.contacts.map(\.id) == [seeded.contact.id],
                    "query \(query) should find the contact")
        }
    }

    @Test func albumsMatchOnNameOnlyAndPhotosNeverAppear() throws {
        let stack = makeStack()
        let album = try stack.photos.createAlbum(name: "Reykjavik")
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        viewModel.setQuery("reykjavik")
        viewModel.search()
        #expect(viewModel.results.albums.map(\.id) == [album.id])
        // Photos do not participate: there is no photo section and no photo
        // kind, so the corpus can only ever hold the three searchable types.
        #expect(Set(viewModel.candidates.map(\.result.kind)) == [.album])
    }

    // MARK: - Fold

    @Test func matchingIsCaseAndDiacriticInsensitiveBothWays() throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        // Unaccented query finds the accented album name "Zoë holiday"…
        viewModel.setQuery("ZOE HOL")
        viewModel.search()
        #expect(viewModel.results.albums.map(\.id) == [seeded.album.id])

        // …and an accented query finds the unaccented note body "Zoe".
        viewModel.setQuery("Zoë")
        viewModel.search()
        #expect(viewModel.results.notes.map(\.id) == [seeded.note.id])
    }

    // MARK: - Empty query

    @Test func anEmptyQueryYieldsTheNoQueryStateAndNeverAllResults() throws {
        let stack = makeStack()
        try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        #expect(!viewModel.hasQuery)
        #expect(viewModel.results.isEmpty)
        viewModel.search() // even if something runs the match, nothing matches
        #expect(viewModel.results.isEmpty)
    }

    @Test func aWhitespaceOnlyQueryIsNotASearch() throws {
        let stack = makeStack()
        try seed(stack)
        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()

        viewModel.setQuery("   ")
        viewModel.search()
        #expect(!viewModel.hasQuery)
        #expect(viewModel.results.isEmpty)
    }

    @Test func clearingTheQueryClearsResultsWithoutWaiting() throws {
        let stack = makeStack()
        try seed(stack)
        // A debounce long enough that a timer-based clear could not have fired.
        let viewModel = makeViewModel(stack, debounce: .seconds(60))
        viewModel.loadCorpus()
        viewModel.setQuery("zoe")
        viewModel.search()
        #expect(!viewModel.results.isEmpty)

        viewModel.setQuery("")
        #expect(viewModel.results.isEmpty)
        #expect(!viewModel.hasQuery)
    }

    // MARK: - Soft delete (P3)

    @Test func trashedRowsNeverAppearInResults() throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        try stack.notes.delete(note: seeded.note)
        try stack.contacts.delete(seeded.contact)
        try stack.photos.deleteAlbum(seeded.album)

        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()
        viewModel.setQuery("zoe")
        viewModel.search()

        #expect(viewModel.results.isEmpty)
        #expect(viewModel.candidates.isEmpty)
    }

    @Test func restoredRowsComeBackIntoResults() throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        try stack.notes.delete(note: seeded.note)
        try stack.notes.restore(ids: [seeded.note.id])

        let viewModel = makeViewModel(stack)
        viewModel.loadCorpus()
        viewModel.setQuery("zoe")
        viewModel.search()
        #expect(viewModel.results.notes.map(\.id) == [seeded.note.id])
    }

    // MARK: - Debounce

    @Test func theDebounceIsThreeHundredMilliseconds() {
        #expect(GlobalSearchViewModel.debounce == .milliseconds(300))
    }

    @Test func typingRunsTheSearchAfterTheDebounce() async throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        let viewModel = makeViewModel(stack, debounce: .milliseconds(20))
        viewModel.loadCorpus()

        viewModel.setQuery("zoe")
        // Poll to a generous deadline rather than sleeping a fixed interval:
        // only a debounce that never fires can fail this.
        let deadline = Date.now.addingTimeInterval(5)
        while viewModel.results.isEmpty && Date.now < deadline {
            try? await Task.sleep(for: .milliseconds(5))
        }
        #expect(viewModel.results.notes.map(\.id) == [seeded.note.id])
    }

    @Test func onlyTheLastKeystrokeSearches() async throws {
        let stack = makeStack()
        let seeded = try seed(stack)
        let viewModel = makeViewModel(stack, debounce: .milliseconds(20))
        viewModel.loadCorpus()

        // Each edit cancels the pending task; only "reykjavik"'s survives — and
        // it matches nothing, so an un-cancelled "zoe" would show up here.
        viewModel.setQuery("z")
        viewModel.setQuery("zo")
        viewModel.setQuery("zoe")
        viewModel.setQuery("reykjavik")

        try? await Task.sleep(for: .milliseconds(200))
        #expect(viewModel.results.isEmpty)
        #expect(viewModel.query == "reykjavik")
        _ = seeded
    }

    // MARK: - No first-keystroke "No results" flash (N1 review polish)

    @Test func theContentDecisionCoversEveryCombination() {
        // No query always wins; stale results outrank a pending search; only a
        // completed search with nothing to show says "No results".
        #expect(GlobalSearchContent.of(hasQuery: false, isPending: false, hasResults: false) == .noQuery)
        #expect(GlobalSearchContent.of(hasQuery: false, isPending: true, hasResults: true) == .noQuery)
        #expect(GlobalSearchContent.of(hasQuery: true, isPending: true, hasResults: false) == .pending)
        #expect(GlobalSearchContent.of(hasQuery: true, isPending: true, hasResults: true) == .results)
        #expect(GlobalSearchContent.of(hasQuery: true, isPending: false, hasResults: true) == .results)
        #expect(GlobalSearchContent.of(hasQuery: true, isPending: false, hasResults: false) == .noResults)
    }

    @Test func noResultsIsNeverShownWhileTheFirstSearchIsStillDebouncing() throws {
        // The bug: results arrive 300 ms after the keystroke, so a state keyed
        // on "has a query" alone flashed "No results" on every first character.
        let stack = makeStack()
        try seed(stack)
        // Long enough that the pending task cannot possibly have fired.
        let viewModel = makeViewModel(stack, debounce: .seconds(60))
        viewModel.loadCorpus()
        #expect(viewModel.content == .noQuery)

        viewModel.setQuery("q")
        #expect(viewModel.isSearchPending)
        #expect(viewModel.content == .pending)

        viewModel.search()
        #expect(!viewModel.isSearchPending)
        #expect(viewModel.content == .noResults)
    }

    @Test func thePreviousResultsStayOnScreenWhileTheNextSearchIsPending() throws {
        let stack = makeStack()
        try seed(stack)
        let viewModel = makeViewModel(stack, debounce: .seconds(60))
        viewModel.loadCorpus()

        viewModel.setQuery("zoe")
        viewModel.search()
        #expect(viewModel.content == .results)

        // Typing on: the list must not blank or flip to "No results" between
        // keystrokes.
        viewModel.setQuery("zoeq")
        #expect(viewModel.isSearchPending)
        #expect(viewModel.content == .results)

        viewModel.search()
        #expect(viewModel.content == .noResults)
    }

    @Test func clearingTheQueryEndsThePendingState() throws {
        let stack = makeStack()
        try seed(stack)
        let viewModel = makeViewModel(stack, debounce: .seconds(60))
        viewModel.loadCorpus()

        viewModel.setQuery("zoe")
        #expect(viewModel.isSearchPending)
        viewModel.setQuery("")
        #expect(!viewModel.isSearchPending)
        #expect(viewModel.content == .noQuery)
    }

    // MARK: - Album order (P4 review polish)

    @Test func theAlbumsSectionUsesTheChosenAlbumSort() throws {
        // `loadCorpus` used to call the `.manual` convenience, so the search
        // section ignored the user's gallery order (decisions §4).
        let suiteName = "test.search.sort.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let stack = makeStack()
        _ = try stack.photos.createAlbum(name: "zulu trip")
        _ = try stack.photos.createAlbum(name: "alpha trip")

        let manual = GlobalSearchViewModel(photoRepository: stack.photos,
                                           noteRepository: stack.notes,
                                           contactRepository: stack.contacts,
                                           debounce: .milliseconds(0),
                                           defaults: defaults)
        manual.loadCorpus()
        manual.setQuery("trip")
        manual.search()
        #expect(manual.results.albums.map(\.title) == ["zulu trip", "alpha trip"])

        SortPreferences.setAlbumSort(.name, defaults: defaults)
        let byName = GlobalSearchViewModel(photoRepository: stack.photos,
                                           noteRepository: stack.notes,
                                           contactRepository: stack.contacts,
                                           debounce: .milliseconds(0),
                                           defaults: defaults)
        byName.loadCorpus()
        byName.setQuery("trip")
        byName.search()
        #expect(byName.results.albums.map(\.title) == ["alpha trip", "zulu trip"])
    }
}
