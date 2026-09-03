import Foundation
import Testing
@testable import SafeBox

/// The vault's single fold (decisions §7): NFD → strip combining marks →
/// lowercase, with `fold(haystack).contains(fold(query))` as the match.
struct SearchFoldTests {
    @Test func foldLowercases() {
        #expect(SearchFold.fold("HELLO World") == "hello world")
    }

    @Test func foldStripsDiacritics() {
        #expect(SearchFold.fold("Zoë") == "zoe")
        #expect(SearchFold.fold("É") == "e")
        #expect(SearchFold.fold("Ångström") == "angstrom")
        #expect(SearchFold.fold("naïve café") == "naive cafe")
    }

    @Test func foldIsIdempotent() {
        let once = SearchFold.fold("Café ÉLAN")
        #expect(SearchFold.fold(once) == once)
    }

    @Test func matchingIsCaseInsensitiveBothWays() {
        #expect(SearchFold.contains("Meeting notes", "MEETING"))
        #expect(SearchFold.contains("MEETING NOTES", "meeting"))
    }

    @Test func matchingIsDiacriticInsensitiveBothWays() {
        // The decided examples: an unaccented query finds accented text…
        #expect(SearchFold.contains("Zoë", "zoe"))
        #expect(SearchFold.contains("É", "e"))
        // …and an accented query finds unaccented text.
        #expect(SearchFold.contains("zoe", "Zoë"))
        #expect(SearchFold.contains("e", "É"))
        #expect(SearchFold.contains("Cafe Roma", "café"))
        #expect(SearchFold.contains("Café Roma", "cafe"))
    }

    @Test func matchingIsASubstringMatch() {
        #expect(SearchFold.contains("grocery list", "cery li"))
        #expect(!SearchFold.contains("grocery list", "groceries"))
    }

    @Test func blankQueryMatchesNothing() {
        // A blank query is never "match everything" — the caller decides what
        // an empty field shows.
        #expect(!SearchFold.contains("anything at all", ""))
        #expect(!SearchFold.contains("anything at all", "   "))
        #expect(!SearchFold.containsAny(["a", "b"], " "))
        #expect(SearchFold.isBlank(""))
        #expect(SearchFold.isBlank("  \t "))
        #expect(!SearchFold.isBlank(" a "))
    }

    @Test func queryIsTrimmedAtTheEdgesOnly() {
        #expect(SearchFold.contains("grocery list", "  grocery  "))
        // Inner whitespace is part of the query, not padding.
        #expect(!SearchFold.contains("grocerylist", "grocery list"))
        #expect(SearchFold.foldedQuery("  Zoë  ") == "zoe")
    }

    @Test func containsAnyMatchesAcrossHaystacks() {
        let haystacks = ["Ada Lovelace", "Analytical Engine", "+44 7700 900123"]
        #expect(SearchFold.containsAny(haystacks, "engine"))
        #expect(SearchFold.containsAny(haystacks, "900123"))
        #expect(!SearchFold.containsAny(haystacks, "babbage"))
        #expect(!SearchFold.containsAny([], "anything"))
    }

    @Test func preFoldedVariantsAgreeWithTheRawOnes() {
        let folded = SearchFold.foldedQuery(" CAFÉ ")
        #expect(SearchFold.foldedContains("Le Cafe Central", foldedQuery: folded))
        #expect(SearchFold.foldedContainsAny(["nope", "Le Cafe"], foldedQuery: folded))
        #expect(!SearchFold.foldedContains("anything", foldedQuery: ""))
        #expect(!SearchFold.foldedContainsAny(["anything"], foldedQuery: ""))
    }

    // MARK: - One implementation for the whole vault

    @MainActor
    @Test func sortingKeysUseTheSearchFold() {
        #expect(VaultSorting.foldedKey("  Ábc ") == SearchFold.fold("Ábc"))
        #expect(VaultSorting.foldedKey("Zoë") == SearchFold.fold("Zoë"))
    }

    @MainActor
    @Test func contactSortKeyUsesTheSearchFold() {
        let contact = Contact(givenName: "Ana", familyName: "Ångström")
        #expect(contact.sortKey == SearchFold.fold("Ångström"))
        #expect(contact.sectionKey == "A")
    }
}
