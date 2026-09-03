import Foundation

/// One global-search hit (decisions §7).
///
/// A plain value snapshot — never a `@Model` object. Results cross a
/// presentation boundary (the search cover hands one back to `VaultNavigator`,
/// which then rebuilds a tab's navigation path from its id), and a live model
/// reference is exactly the thing that must not travel that far: the row could
/// be trashed or purged in between, and reading a deleted `@Model` traps.
struct SearchResult: Identifiable, Hashable, Sendable {
    /// Photos deliberately do not participate (decisions §7): their file names
    /// are UUIDs and mean nothing to the user; albums carry the searchable name.
    enum Kind: String, Hashable, Sendable, CaseIterable {
        case album
        case note
        case contact
    }

    let kind: Kind
    /// The entity's id — also the navigation payload.
    let id: UUID
    let title: String
    /// Secondary line; empty when the entity has nothing to show there.
    let subtitle: String
}

/// A `SearchResult` plus the strings it matches on. Built once per search
/// session (`GlobalSearchViewModel.loadCorpus`) and filtered in memory on every
/// debounced keystroke.
struct SearchCandidate: Equatable, Sendable {
    let result: SearchResult
    /// Everything the query is matched against for this entity.
    let haystacks: [String]
}

/// Hits grouped by type, in the decided section order Albums → Notes → Contacts.
struct SearchResults: Equatable, Sendable {
    var albums: [SearchResult] = []
    var notes: [SearchResult] = []
    var contacts: [SearchResult] = []

    var isEmpty: Bool { albums.isEmpty && notes.isEmpty && contacts.isEmpty }
    var count: Int { albums.count + notes.count + contacts.count }

    /// Every hit in section order — the flat view of the same grouping.
    var all: [SearchResult] { albums + notes + contacts }
}

/// The pure matching step: candidates + raw query → grouped results.
/// No SwiftData, no view state, no isolation — so it is directly testable.
enum SearchMatching {
    /// A blank query yields NO results (decisions §7: the empty-query state is
    /// its own screen state, never "everything").
    static func results(in candidates: [SearchCandidate], query: String) -> SearchResults {
        let folded = SearchFold.foldedQuery(query)
        guard !folded.isEmpty else { return SearchResults() }

        var results = SearchResults()
        for candidate in candidates
        where SearchFold.foldedContainsAny(candidate.haystacks, foldedQuery: folded) {
            switch candidate.result.kind {
            case .album: results.albums.append(candidate.result)
            case .note: results.notes.append(candidate.result)
            case .contact: results.contacts.append(candidate.result)
            }
        }
        return results
    }
}
