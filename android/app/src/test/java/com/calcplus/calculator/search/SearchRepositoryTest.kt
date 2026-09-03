package com.calcplus.calculator.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.data.ContactRepositoryImpl
import com.calcplus.calculator.core.data.NoteRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.LabeledValue
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The per-tab searches now filter in Kotlin through the shared fold
 * (decisions §7) instead of SQLite `LIKE`, so a Notes/Contacts result and a
 * global-search result can never disagree.
 *
 * These are the assertions that need real Room: the old `LIKE` was
 * ASCII-case-only, diacritic-sensitive and passed `%` / `_` through as
 * wildcards, and it scanned the contacts' phone/email JSON rather than their
 * values.
 */
@RunWith(RobolectricTestRunner::class)
class SearchRepositoryTest {
    private lateinit var db: SafeBoxDatabase
    private lateinit var notes: NoteRepositoryImpl
    private lateinit var contacts: ContactRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notes = NoteRepositoryImpl(db) { 1_000L }
        contacts = ContactRepositoryImpl(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun note(id: String, title: String, body: String = title, deletedAt: Long? = null) {
        db.noteDao().upsert(
            NoteEntity(
                id = id,
                body = body,
                title = title,
                snippet = body,
                createdAt = 0,
                updatedAt = 0,
                deletedAt = deletedAt,
            )
        )
    }

    private suspend fun contact(
        id: String,
        first: String? = null,
        last: String? = null,
        organization: String? = null,
        phones: List<LabeledValue> = emptyList(),
        emails: List<LabeledValue> = emptyList(),
        deletedAt: Long? = null,
    ) {
        db.contactDao().upsert(
            ContactEntity(
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
                deletedAt = deletedAt,
            )
        )
    }

    private suspend fun noteIds(query: String) =
        notes.observeNotes(query, null).first().map { it.id }

    private suspend fun contactIds(query: String) =
        contacts.observeContacts(query).first().map { it.id }

    // ---- Notes ------------------------------------------------------------

    @Test
    fun aDiacriticQueryNowMatchesInTheNotesList() = runTest {
        note("n-1", title = "Zoë Baker", body = "birthday")
        note("n-2", title = "Angstrom", body = "units")

        // Both directions — SQLite LIKE could do neither.
        assertEquals(listOf("n-1"), noteIds("zoe"))
        assertEquals(listOf("n-1"), noteIds("ZOË"))
        assertEquals(listOf("n-2"), noteIds("Ångström"))
    }

    @Test
    fun theNotesListStillMatchesTitleAndBodyCaseInsensitively() = runTest {
        note("n-1", title = "Quarterly report", body = "revenue up")
        note("n-2", title = "Shopping", body = "milk")

        assertEquals(listOf("n-1"), noteIds("QUARTERLY"))
        assertEquals(listOf("n-1"), noteIds("revenue"))
        assertEquals(setOf("n-1", "n-2"), noteIds("").toSet())
        assertTrue(noteIds("zebra").isEmpty())
    }

    @Test
    fun sqlWildcardsInTheQueryAreNoLongerWildcards() = runTest {
        note("n-1", title = "100% cotton", body = "label")
        note("n-2", title = "Shopping", body = "milk")

        // `%` used to match everything; now it is an ordinary character.
        assertEquals(listOf("n-1"), noteIds("%"))
        assertEquals(listOf("n-1"), noteIds("100% co"))
        assertTrue(noteIds("_").isEmpty())
    }

    @Test
    fun aTrashedNoteNeverMatches() = runTest {
        note("n-1", title = "Zoë Baker", deletedAt = 5_000L)
        assertTrue(noteIds("zoe").isEmpty())
        assertTrue(noteIds("").isEmpty())
    }

    @Test
    fun theTagFilteredQueryUsesTheSameFold() = runTest {
        db.tagDao().insert(TagEntity("t-1", "Finance", 0))
        note("n-1", title = "Zoë Baker")
        note("n-2", title = "Zoë Carter")
        db.noteDao().setTags("n-1", listOf("t-1"))

        assertEquals(listOf("n-1"), notes.observeNotes("zoe", "t-1").first().map { it.id })
        assertEquals(listOf("n-1"), notes.observeNotes("", "t-1").first().map { it.id })
    }

    // ---- Contacts ---------------------------------------------------------

    @Test
    fun aDiacriticQueryNowMatchesInTheContactsList() = runTest {
        contact("c-1", first = "Zoë", last = "Baker")
        contact("c-2", first = "Ada", last = "Lovelace")

        assertEquals(listOf("c-1"), contactIds("zoe"))
        assertEquals(listOf("c-2"), contactIds("LOVELACE"))
        // An accented query against unaccented data.
        assertEquals(listOf("c-2"), contactIds("Àda"))
    }

    @Test
    fun contactsMatchOnTheDerivedDisplayNameAndOnPhoneAndEmailValues() = runTest {
        contact(
            "c-1",
            first = "Ada",
            last = "Lovelace",
            organization = "Analytical Engines",
            phones = listOf(LabeledValue("mobile", "+34 600 123 456")),
            emails = listOf(LabeledValue("home", "ada@example.com")),
        )

        assertEquals(listOf("c-1"), contactIds("Ada Lovelace")) // spans two columns
        assertEquals(listOf("c-1"), contactIds("analytical"))
        assertEquals(listOf("c-1"), contactIds("600 123"))
        assertEquals(listOf("c-1"), contactIds("ADA@EXAMPLE.COM"))
        // The stored JSON's own keys are not searchable text.
        assertTrue(contactIds("label").isEmpty())
    }

    @Test
    fun aTrashedContactNeverMatches() = runTest {
        contact("c-1", first = "Zoë", last = "Baker", deletedAt = 5_000L)
        assertTrue(contactIds("zoe").isEmpty())
        assertTrue(contactIds("").isEmpty())
    }

    @Test
    fun theContactsListKeepsItsFamilyNameFirstOrderUnderAQuery() = runTest {
        contact("c-1", first = "Ada", last = "Zeta")
        contact("c-2", first = "Ada", last = "Alpha")

        assertEquals(listOf("c-2", "c-1"), contactIds("ada"))
    }
}
