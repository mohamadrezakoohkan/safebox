package com.calcplus.calculator.search

import com.calcplus.calculator.core.domain.model.SearchNormalizer
import com.calcplus.calculator.core.domain.model.VaultTextFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one fold (decisions §7): case- AND diacritic-insensitive substring
 * matching, shared by the Notes tab, the Contacts tab and global search.
 */
class SearchNormalizerTest {

    @Test
    fun theSearchFoldIsTheVaultFold() {
        // N1 must NOT introduce a second normalizer: `SearchNormalizer` is a
        // façade over P4's `VaultTextFold`, which is also what Contact.sortKey
        // and the A–Z sorts compare on.
        for (value in listOf("Zoë", " Ångström ", "CAFÉ", "", "  ", "Ünïcödé Ñ")) {
            assertEquals(VaultTextFold.fold(value), SearchNormalizer.fold(value))
        }
    }

    @Test
    fun matchingIsCaseInsensitiveBothWays() {
        assertTrue(SearchNormalizer.contains("Quarterly Report", "quarterly"))
        assertTrue(SearchNormalizer.contains("quarterly report", "QUARTERLY"))
        assertTrue(SearchNormalizer.contains("QuArTeRlY", "tErL"))
    }

    @Test
    fun anAccentedHaystackMatchesAnUnaccentedQuery() {
        // The decided example: "Zoë" matches "zoe".
        assertTrue(SearchNormalizer.contains("Zoë", "zoe"))
        assertTrue(SearchNormalizer.contains("Ångström", "angstrom"))
        assertTrue(SearchNormalizer.contains("Café Ñoño", "cafe n"))
    }

    @Test
    fun anAccentedQueryMatchesAnUnaccentedHaystack() {
        // …and the other direction: "É" matches "e".
        assertTrue(SearchNormalizer.contains("energy", "É"))
        assertTrue(SearchNormalizer.contains("Zoe", "zoë"))
        assertTrue(SearchNormalizer.contains("angstrom", "Ångström"))
    }

    @Test
    fun aPrecomposedQueryMatchesADecomposedHaystackAndViceVersa() {
        val precomposed = "caf\u00E9" // e-acute as one code point
        val decomposed = "cafe\u0301" // e + COMBINING ACUTE ACCENT
        assertTrue(SearchNormalizer.contains(precomposed, decomposed))
        assertTrue(SearchNormalizer.contains(decomposed, precomposed))
    }

    @Test
    fun aBlankQueryMatchesNothingAndIsReportedBlank() {
        // Never "match everything": each screen decides what an empty field
        // shows (the tabs show their whole list; global search shows its
        // no-query state).
        assertTrue(SearchNormalizer.isBlank(""))
        assertTrue(SearchNormalizer.isBlank("   "))
        assertFalse(SearchNormalizer.isBlank(" a "))

        assertFalse(SearchNormalizer.contains("anything", ""))
        assertFalse(SearchNormalizer.contains("anything", "   "))
        assertFalse(SearchNormalizer.containsAny(listOf("a", "b"), " "))
        assertFalse(SearchNormalizer.foldedContains("anything", ""))
        assertFalse(SearchNormalizer.foldedContainsAny(listOf("a"), ""))
    }

    @Test
    fun theQueryIsTrimmedBeforeMatching() {
        assertEquals("zoe", SearchNormalizer.foldedQuery("  Zoë  "))
        assertTrue(SearchNormalizer.contains("Zoë Baker", "  zoe "))
    }

    @Test
    fun containsAnyMatchesWhenAnySingleHaystackDoes() {
        val haystacks = listOf("Ada", "", "Lovelace", "ada@example.com")
        assertTrue(SearchNormalizer.containsAny(haystacks, "lovelace"))
        assertTrue(SearchNormalizer.containsAny(haystacks, "EXAMPLE.COM"))
        assertFalse(SearchNormalizer.containsAny(haystacks, "babbage"))
        assertFalse(SearchNormalizer.containsAny(emptyList(), "ada"))
    }

    @Test
    fun aNonMatchStaysANonMatch() {
        assertFalse(SearchNormalizer.contains("Quarterly Report", "zebra"))
        // The fold strips marks, it does not equate distinct base letters.
        assertFalse(SearchNormalizer.contains("Zoë", "zoa"))
    }
}
