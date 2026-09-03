import Foundation
import Observation

/// Global search across notes, contacts and albums (decisions §7).
///
/// The corpus is snapshotted once when the screen appears (the vault cannot
/// change underneath a full-screen search), then every debounced keystroke is a
/// pure in-memory filter. Only LIVE rows are read — the repositories filter
/// `deletedAt` (P3), so a trashed note/contact/album/photo can never surface.
/// What the search screen renders. A separate `pending` case exists because
/// results arrive one debounce (300 ms) after the first keystroke: without it
/// the screen flips to "No results / Check the spelling" the instant a query
/// becomes non-blank and flashes it on every first character.
///
/// Stale results outrank pending on purpose — while a new search is in flight
/// the previous list stays on screen (the standard incremental-search feel)
/// rather than blanking between keystrokes.
enum GlobalSearchContent: Hashable, Sendable {
    case noQuery
    /// A search for the current query has not completed yet and there is
    /// nothing older to show: render nothing, never "No results".
    case pending
    case results
    case noResults

    static func of(hasQuery: Bool, isPending: Bool, hasResults: Bool) -> GlobalSearchContent {
        guard hasQuery else { return .noQuery }
        if hasResults { return .results }
        return isPending ? .pending : .noResults
    }
}

@MainActor
@Observable
final class GlobalSearchViewModel {
    /// Shared constant (decisions §11): 300 ms on both platforms.
    static let debounce: Duration = .milliseconds(300)

    private let photoRepository: any PhotoRepository
    private let noteRepository: any NoteRepository
    private let contactRepository: any ContactRepository
    private let debounceDelay: Duration
    private let defaults: UserDefaults

    private(set) var query = ""
    private(set) var results = SearchResults()
    /// True from the keystroke that starts a debounce until that search runs.
    /// `results` is stale (or empty) while it is set.
    private(set) var isSearchPending = false
    /// The searchable snapshot of the vault; `loadCorpus()` fills it.
    private(set) var candidates: [SearchCandidate] = []

    private var searchTask: Task<Void, Never>?

    init(photoRepository: any PhotoRepository,
         noteRepository: any NoteRepository,
         contactRepository: any ContactRepository,
         debounce: Duration = GlobalSearchViewModel.debounce,
         defaults: UserDefaults = .standard) {
        self.photoRepository = photoRepository
        self.noteRepository = noteRepository
        self.contactRepository = contactRepository
        self.debounceDelay = debounce
        self.defaults = defaults
    }

    /// True once the user has typed something matchable. Drives the choice
    /// between the no-query state and the no-results state (decisions §2).
    var hasQuery: Bool { !SearchFold.isBlank(query) }

    /// What the screen shows right now (see `GlobalSearchContent`).
    var content: GlobalSearchContent {
        GlobalSearchContent.of(hasQuery: hasQuery,
                               isPending: isSearchPending,
                               hasResults: !results.isEmpty)
    }

    // MARK: - Query

    /// The only way the query changes (the search field binds through here), so
    /// the 300 ms debounce cannot be bypassed.
    ///
    /// Same shape as `NoteEditorViewModel`'s autosave: cancel the pending task,
    /// sleep, re-check cancellation, then do the work.
    func setQuery(_ newQuery: String) {
        guard newQuery != query else { return }
        query = newQuery
        searchTask?.cancel()
        searchTask = nil

        // Clearing the field is immediate — waiting 300 ms to empty a list the
        // user just erased reads as lag, and there is nothing to compute.
        guard hasQuery else {
            results = SearchResults()
            isSearchPending = false
            return
        }

        // Nothing has been matched for this query yet: until the debounce
        // fires, the screen keeps whatever it was showing instead of claiming
        // there are no results.
        isSearchPending = true

        let delay = debounceDelay
        searchTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            self?.search()
        }
    }

    /// Runs the match immediately (the debounce's tail; tests call it directly).
    func search() {
        results = SearchMatching.results(in: candidates, query: query)
        isSearchPending = false
    }

    // MARK: - Corpus

    /// Snapshots every live album, note and contact into `SearchCandidate`s.
    /// Scope (decisions §7): notes match on title, body and tag names; contacts
    /// on name, organization, phone numbers and email addresses; albums on name.
    func loadCorpus() {
        var built: [SearchCandidate] = []

        // Albums come in the order the user chose for the gallery (decisions
        // §4), so the search section matches the tab it navigates into.
        for album in photoRepository.albums(sortedBy: SortPreferences.albumSort(defaults: defaults)) {
            let count = photoRepository.photos(in: album).count
            built.append(SearchCandidate(
                result: SearchResult(kind: .album,
                                     id: album.id,
                                     title: album.name,
                                     subtitle: VaultCopy.trashPhotoCount(count)),
                haystacks: [album.name]
            ))
        }

        for note in noteRepository.notes() {
            built.append(SearchCandidate(
                result: SearchResult(kind: .note,
                                     id: note.id,
                                     title: note.title.isEmpty
                                         ? NoteDerivation.emptyTitleFallback
                                         : note.title,
                                     subtitle: note.snippet),
                haystacks: [note.title, note.body] + note.tags.map(\.name)
            ))
        }

        for contact in contactRepository.contacts() {
            let organization = contact.organization ?? ""
            built.append(SearchCandidate(
                result: SearchResult(kind: .contact,
                                     id: contact.id,
                                     title: contact.displayName,
                                     // The org line is the display name itself
                                     // for org-only contacts — don't repeat it.
                                     subtitle: organization == contact.displayName ? "" : organization),
                haystacks: [contact.displayName,
                            contact.givenName ?? "",
                            contact.familyName ?? "",
                            organization]
                    + contact.phones.map(\.value)
                    + contact.emails.map(\.value)
            ))
        }

        candidates = built
        // Keep an in-flight query's results consistent with the new corpus.
        if hasQuery { search() }
    }

    /// Derived cover thumbnail for an album row — the same "first live photo"
    /// rule the gallery uses. Returns nil once the album is gone.
    func coverPhoto(forAlbum id: UUID) -> Photo? {
        guard let album = photoRepository.album(withId: id) else { return nil }
        return photoRepository.photos(in: album).first
    }
}
