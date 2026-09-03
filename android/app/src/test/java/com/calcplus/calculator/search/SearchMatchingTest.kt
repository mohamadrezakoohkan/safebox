package com.calcplus.calculator.search

import com.calcplus.calculator.core.database.entity.LabeledValue
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.SearchCorpus
import com.calcplus.calculator.core.domain.model.SearchMatching
import com.calcplus.calculator.core.domain.model.SearchResultKind
import com.calcplus.calculator.core.domain.model.Tag
import com.calcplus.calculator.core.markdown.NoteDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun searchAlbum(
    id: String,
    name: String,
    photoCount: Int = 0,
    cover: String? = null,
) = Album(
    id = id,
    name = name,
    createdAt = 0,
    sortIndex = 0,
    photoCount = photoCount,
    coverThumbFileName = cover,
)

internal fun searchNote(
    id: String,
    title: String,
    body: String = title,
    snippet: String = "",
    tags: List<Tag> = emptyList(),
) = Note(
    id = id,
    body = body,
    title = title,
    snippet = snippet,
    createdAt = 0,
    updatedAt = 0,
    tags = tags,
)

internal fun searchContact(
    id: String,
    first: String? = null,
    last: String? = null,
    organization: String? = null,
    phones: List<LabeledValue> = emptyList(),
    emails: List<LabeledValue> = emptyList(),
) = Contact(
    id = id,
    firstName = first,
    lastName = last,
    organization = organization,
    phones = phones,
    emails = emails,
    address = null,
    notes = null,
    createdAt = 0,
    updatedAt = 0,
)

/**
 * The matching scope of decisions §7: notes (title, body, tag names), contacts
 * (name, organization, phones, emails) and albums (name). Photos never
 * participate.
 */
class SearchMatchingTest {
    private val albums = listOf(
        searchAlbum("al-1", "Rëceipts", photoCount = 4, cover = "cover.jpg"),
        searchAlbum("al-2", "Holiday"),
    )
    private val notes = listOf(
        searchNote(
            "n-1",
            title = "Quarterly report",
            body = "revenue up",
            snippet = "revenue up",
            tags = listOf(Tag("t-1", "Finance", 0)),
        ),
        searchNote("n-2", title = "", body = "", snippet = ""),
    )
    private val contacts = listOf(
        searchContact(
            "c-1",
            first = "Ada",
            last = "Lovelace",
            organization = "Analytical Engines",
            phones = listOf(LabeledValue("mobile", "+34 600 123 456")),
            emails = listOf(LabeledValue("home", "ada@example.com")),
        ),
        searchContact("c-2", organization = "Zebra Ltd"),
    )
    private val corpus = SearchCorpus.build(albums, notes, contacts)

    private fun ids(query: String) = SearchMatching.results(corpus, query).all.map { it.id }

    @Test
    fun theCorpusIsExactlyAlbumsNotesAndContacts() {
        // Photos never participate (decisions §7): there is no photo kind, and
        // the corpus holds one candidate per live album / note / contact.
        assertEquals(listOf("ALBUM", "NOTE", "CONTACT"), SearchResultKind.entries.map { it.name })
        assertEquals(albums.size + notes.size + contacts.size, corpus.size)
        assertEquals(
            mapOf(
                SearchResultKind.ALBUM to albums.size,
                SearchResultKind.NOTE to notes.size,
                SearchResultKind.CONTACT to contacts.size,
            ),
            corpus.groupingBy { it.result.kind }.eachCount(),
        )
    }

    @Test
    fun anAlbumMatchesOnItsNameDiacriticInsensitively() {
        assertEquals(listOf("al-1"), ids("receipts"))
        assertEquals(listOf("al-1"), ids("RËCEI"))
    }

    @Test
    fun aNoteMatchesOnTitleBodyAndTagName() {
        assertEquals(listOf("n-1"), ids("quarterly")) // title
        assertEquals(listOf("n-1"), ids("revenue")) // body
        assertEquals(listOf("n-1"), ids("finance")) // TAG NAME
    }

    @Test
    fun aContactMatchesOnNameOrganizationPhoneAndEmail() {
        assertEquals(listOf("c-1"), ids("lovelace"))
        assertEquals(listOf("c-1"), ids("Ada Lovelace")) // the derived display name
        assertEquals(listOf("c-1"), ids("analytical"))
        assertEquals(listOf("c-1"), ids("600 123"))
        assertEquals(listOf("c-1"), ids("ADA@EXAMPLE"))
    }

    @Test
    fun resultsAreGroupedInSectionOrderAlbumsNotesContacts() {
        val results = SearchMatching.results(corpus, "a")
        assertTrue(results.albums.all { it.kind == SearchResultKind.ALBUM })
        assertTrue(results.notes.all { it.kind == SearchResultKind.NOTE })
        assertTrue(results.contacts.all { it.kind == SearchResultKind.CONTACT })
        assertEquals(results.albums + results.notes + results.contacts, results.all)
        assertEquals(results.all.size, results.count)
    }

    @Test
    fun aQueryCanMatchAcrossAllThreeTypesAtOnce() {
        // "e" appears in every one of these entities.
        val results = SearchMatching.results(corpus, "e")
        assertTrue(results.albums.isNotEmpty())
        assertTrue(results.notes.isNotEmpty())
        assertTrue(results.contacts.isNotEmpty())
    }

    @Test
    fun aBlankQueryYieldsNoResultsRatherThanEverything() {
        for (query in listOf("", " ", "\t  ")) {
            val results = SearchMatching.results(corpus, query)
            assertTrue(results.isEmpty)
            assertEquals(0, results.count)
        }
    }

    @Test
    fun aQueryThatMatchesNothingYieldsNothing() {
        assertTrue(SearchMatching.results(corpus, "xyzzy").isEmpty)
    }

    @Test
    fun rowsCarryTheDisplayDataEachTabsRowNeeds() {
        val album = corpus.first { it.result.id == "al-1" }.result
        assertEquals(4, album.photoCount)
        assertEquals("cover.jpg", album.thumbFileName)

        val note = corpus.first { it.result.id == "n-1" }.result
        assertEquals("Quarterly report", note.title)
        assertEquals("revenue up", note.subtitle)

        // An untitled note falls back to the same label the notes list shows.
        val untitled = corpus.first { it.result.id == "n-2" }.result
        assertEquals(NoteDerivation.EMPTY_TITLE_FALLBACK, untitled.title)

        val contact = corpus.first { it.result.id == "c-1" }.result
        assertEquals("Ada Lovelace", contact.title)
        assertEquals("Analytical Engines", contact.subtitle)

        // An org-only contact must not print the organization twice.
        val orgOnly = corpus.first { it.result.id == "c-2" }.result
        assertEquals("Zebra Ltd", orgOnly.title)
        assertEquals("", orgOnly.subtitle)
    }
}
