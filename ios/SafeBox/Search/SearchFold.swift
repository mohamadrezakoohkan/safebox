import Foundation

/// The vault's ONE text-folding implementation (decisions §7).
///
/// The decided fold is "Unicode NFD → strip combining marks → lowercase", and a
/// match is `fold(haystack).contains(fold(query))`. Foundation's
/// `String.folding(options: [.diacriticInsensitive, .caseInsensitive])` *is*
/// exactly that transform (`.diacriticInsensitive` decomposes canonically and
/// drops the combining marks; `.caseInsensitive` case-folds), so this enum wraps
/// it rather than hand-rolling a second, subtly different normalizer.
///
/// Everything that folds text in the vault routes through here:
/// `VaultSorting.foldedKey` (A–Z sort keys), `Contact.sortKey` (section keys),
/// the notes list search, the contacts repository search, and global search.
/// Do not call `.folding(...)` anywhere else — one implementation is the whole
/// point, so per-tab and global results can never disagree.
enum SearchFold {
    /// NFD → strip combining marks → lowercase. Locale-independent on purpose:
    /// the vault's data is user text in any language and a locale-sensitive fold
    /// would make matching depend on the device's region.
    static func fold(_ value: String) -> String {
        value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
    }

    /// A raw query reduced to what matching uses: trimmed, then folded.
    /// An empty result means "no query" — never "match everything"; every
    /// caller must decide explicitly what a blank query shows.
    static func foldedQuery(_ raw: String) -> String {
        fold(raw.trimmingCharacters(in: .whitespaces))
    }

    /// True when the query carries nothing to match on (empty or whitespace).
    static func isBlank(_ raw: String) -> Bool {
        raw.trimmingCharacters(in: .whitespaces).isEmpty
    }

    /// `fold(haystack).contains(fold(query))`. A blank query matches nothing.
    static func contains(_ haystack: String, _ query: String) -> Bool {
        foldedContains(haystack, foldedQuery: foldedQuery(query))
    }

    /// True when ANY of the haystacks contains the query.
    static func containsAny(_ haystacks: [String], _ query: String) -> Bool {
        foldedContainsAny(haystacks, foldedQuery: foldedQuery(query))
    }

    // MARK: Pre-folded query variants
    //
    // Search folds the query once per keystroke and then walks the corpus, so
    // these take an already-folded query. Passing a raw query here would
    // silently skip the fold — the parameter label says `foldedQuery` for that
    // reason.

    static func foldedContains(_ haystack: String, foldedQuery query: String) -> Bool {
        guard !query.isEmpty else { return false }
        return fold(haystack).contains(query)
    }

    static func foldedContainsAny(_ haystacks: [String], foldedQuery query: String) -> Bool {
        guard !query.isEmpty else { return false }
        return haystacks.contains { fold($0).contains(query) }
    }
}
