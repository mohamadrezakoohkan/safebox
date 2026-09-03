package com.calcplus.calculator.core.domain.model

/**
 * The vault's matching helper (decisions §7): case- **and** diacritic-insensitive
 * substring matching, `fold(haystack).contains(fold(query))`.
 *
 * It deliberately owns no fold of its own — [VaultTextFold.fold] (P4) already
 * implements the decided transformation (trim → Unicode NFD → strip combining
 * marks → lowercase) and is what [Contact.sortKey] and the A–Z sorts compare on.
 * A second normalizer is exactly the divergence §7 forbids, so everything that
 * matches text routes through here and everything that folds text routes through
 * [VaultTextFold]. The iOS twin is `SearchFold`.
 *
 * **A blank query matches NOTHING** in every function below — never "everything".
 * Each caller decides for itself what an empty field shows: the notes and
 * contacts lists return their whole list, global search shows its no-query state.
 */
object SearchNormalizer {
    /** The one fold. Delegates to [VaultTextFold] — do not reimplement it here. */
    fun fold(value: String): String = VaultTextFold.fold(value)

    /**
     * A raw query reduced to what matching uses. [VaultTextFold.fold] trims, so a
     * whitespace-only query folds to `""` — i.e. to "no query".
     */
    fun foldedQuery(raw: String): String = fold(raw)

    /** True when the query carries nothing to match on (empty or whitespace). */
    fun isBlank(raw: String): Boolean = raw.isBlank()

    /** `fold(haystack).contains(fold(query))`. A blank query matches nothing. */
    fun contains(haystack: String, query: String): Boolean =
        foldedContains(haystack, foldedQuery(query))

    /** True when ANY haystack contains the query. A blank query matches nothing. */
    fun containsAny(haystacks: List<String>, query: String): Boolean =
        foldedContainsAny(haystacks, foldedQuery(query))

    // Pre-folded variants: search folds the query once per keystroke and then
    // walks the corpus. The parameter is named `foldedQuery` so passing a raw
    // query here reads as the mistake it would be.

    fun foldedContains(haystack: String, foldedQuery: String): Boolean =
        foldedQuery.isNotEmpty() && fold(haystack).contains(foldedQuery)

    fun foldedContainsAny(haystacks: List<String>, foldedQuery: String): Boolean =
        foldedQuery.isNotEmpty() && haystacks.any { fold(it).contains(foldedQuery) }
}
