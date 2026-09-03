package com.calcplus.calculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.LabeledValue
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTests {
    private lateinit var db: SafeBoxDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun album(name: String, sortIndex: Int = 0) =
        AlbumEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis(), sortIndex)

    private fun photo(albumId: String, sortIndex: Int) = PhotoEntity(
        id = UUID.randomUUID().toString(),
        albumId = albumId,
        fileName = "$sortIndex.jpg",
        thumbFileName = "$sortIndex-thumb.jpg",
        mimeType = "image/jpeg",
        width = 10,
        height = 10,
        byteCount = 100,
        importedAt = sortIndex.toLong(),
        sortIndex = sortIndex,
    )

    private fun note(body: String, title: String = "", snippet: String = "", updatedAt: Long = 0) = NoteEntity(
        id = UUID.randomUUID().toString(),
        body = body,
        title = title,
        snippet = snippet,
        createdAt = 0,
        updatedAt = updatedAt,
    )

    @Test
    fun albumWithCountAndDerivedCover() = runTest {
        val a = album("Trips", sortIndex = 0)
        db.albumDao().insert(a)
        db.photoDao().insert(photo(a.id, sortIndex = 1))
        db.photoDao().insert(photo(a.id, sortIndex = 0)) // first by sortIndex = cover

        val rows = db.albumDao().observeAlbumsWithCounts().first()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].photoCount)
        assertEquals("0-thumb.jpg", rows[0].coverThumbFileName) // derived cover
    }

    /**
     * Behavior intentionally changed in iteration 2 (decisions §3): this test
     * used to assert that `delete` cascaded the photo ROWS away. Deleting is
     * now a soft delete, and only `purge` cascades.
     */
    @Test
    fun albumSoftDeleteHidesItAndPurgeCascadesTheRows() = runTest {
        val a = album("Gone")
        db.albumDao().insert(a)
        db.photoDao().insert(photo(a.id, 0))
        db.photoDao().insert(photo(a.id, 1))

        db.albumDao().softDelete(a.id, now = 1_000)
        db.photoDao().softDeleteLiveInAlbum(a.id, now = 1_000)
        // Hidden from every live query, but every row (and every file) is still there.
        assertTrue(db.albumDao().albums().isEmpty())
        assertTrue(db.photoDao().photos(a.id).isEmpty())
        assertEquals(1, db.albumDao().allAlbums().size)
        assertEquals(2, db.photoDao().allPhotos().size)
        assertEquals(listOf(1_000L, 1_000L), db.photoDao().allPhotos().map { it.deletedAt })

        db.albumDao().purge(listOf(a.id))
        assertTrue(db.photoDao().allPhotos().isEmpty()) // FK CASCADE
        assertTrue(db.albumDao().allAlbums().isEmpty())
    }

    @Test
    fun softDeleteIsIdempotentAndKeepsTheFirstStamp() = runTest {
        val a = album("Keep the stamp")
        db.albumDao().insert(a)
        db.albumDao().softDelete(a.id, now = 500)
        db.albumDao().softDelete(a.id, now = 900)
        assertEquals(500L, db.albumDao().album(a.id)?.deletedAt)
    }

    @Test
    fun albumCountsAndCoverIgnoreTrashedPhotos() = runTest {
        val a = album("Trips")
        db.albumDao().insert(a)
        val first = photo(a.id, sortIndex = 0)
        db.photoDao().insert(first)
        db.photoDao().insert(photo(a.id, sortIndex = 1))
        db.photoDao().softDelete(listOf(first.id), now = 10)

        val rows = db.albumDao().observeAlbumsWithCounts().first()
        assertEquals(1, rows[0].photoCount)                    // the trashed one does not count
        assertEquals("1-thumb.jpg", rows[0].coverThumbFileName) // …and cannot be the cover
    }

    @Test
    fun expiryQueriesSelectOnlyStampsAtOrBeforeTheCutoff() = runTest {
        val old = album("old")
        val fresh = album("fresh")
        db.albumDao().insert(old)
        db.albumDao().insert(fresh)
        db.albumDao().softDelete(old.id, now = 100)
        db.albumDao().softDelete(fresh.id, now = 300)
        assertEquals(listOf(old.id), db.albumDao().expiredIds(cutoff = 200))
        assertEquals(2, db.albumDao().expiredIds(cutoff = 300).size) // inclusive
    }

    @Test
    fun photoMoveAndOrdering() = runTest {
        val a = album("A", 0)
        val b = album("B", 1)
        db.albumDao().insert(a)
        db.albumDao().insert(b)
        val p = photo(a.id, 0)
        db.photoDao().insert(p)
        db.photoDao().move(p.id, b.id, db.photoDao().nextSortIndex(b.id))
        assertTrue(db.photoDao().photos(a.id).isEmpty())
        assertEquals(1, db.photoDao().photos(b.id).size)
    }

    @Test
    fun noteSearchOverTitleAndBody() = runTest {
        db.noteDao().upsert(note(body = "the quick brown fox", title = "Animals"))
        db.noteDao().upsert(note(body = "unrelated", title = "Other"))
        assertEquals(1, db.noteDao().observeNotes("fox").first().size)   // body match
        assertEquals(1, db.noteDao().observeNotes("Animals").first().size) // title match
        assertEquals(2, db.noteDao().observeNotes("").first().size)      // empty = all
        assertEquals(0, db.noteDao().observeNotes("zebra").first().size)
    }

    @Test
    fun tagCrossRefsAndFilterQuery() = runTest {
        val n1 = note("tagged note")
        val n2 = note("plain note")
        db.noteDao().upsert(n1)
        db.noteDao().upsert(n2)
        val tag = TagEntity(UUID.randomUUID().toString(), "work", 0)
        db.tagDao().insert(tag)
        db.noteDao().setTags(n1.id, listOf(tag.id))

        val tagged = db.noteDao().observeNotesWithTag("", tag.id).first()
        assertEquals(listOf(n1.id), tagged.map { it.note.id })

        val withTags = db.noteDao().observeNoteWithTags(n1.id).first()
        assertEquals(listOf("work"), withTags?.tags?.map { it.name })

        // Soft delete KEEPS the cross-ref, so a restored note keeps its tags…
        db.noteDao().softDelete(listOf(n1.id), now = 5)
        assertEquals(listOf("work"), db.noteDao().observeNoteWithTags(n1.id).first()?.tags?.map { it.name })
        assertTrue(db.noteDao().observeNotes("").first().none { it.note.id == n1.id })
        db.noteDao().restore(listOf(n1.id))
        assertEquals(listOf("work"), db.noteDao().observeNoteWithTags(n1.id).first()?.tags?.map { it.name })

        // …and only the purge removes cross-refs. The tag itself always survives.
        db.noteDao().purge(listOf(n1.id))
        assertEquals(1, db.tagDao().all().size)
        assertTrue(db.noteDao().observeNotesWithTag("", tag.id).first().isEmpty())
    }

    @Test
    fun tagNameIsUniqueAndCaseInsensitiveLookupWorks() = runTest {
        db.tagDao().insert(TagEntity(UUID.randomUUID().toString(), "Work", 0))
        db.tagDao().insert(TagEntity(UUID.randomUUID().toString(), "Work", 1)) // IGNOREd
        assertEquals(1, db.tagDao().count())
        assertEquals("Work", db.tagDao().byName("work")?.name) // COLLATE NOCASE
    }

    @Test
    fun contactSearchOverNameOrgPhonesEmails() = runTest {
        val contact = ContactEntity(
            id = UUID.randomUUID().toString(),
            firstName = "Grace",
            lastName = "Hopper",
            organization = "Navy",
            phones = listOf(LabeledValue("work", "555-0199")),
            emails = listOf(LabeledValue("work", "grace@usn.mil")),
            address = null,
            notes = null,
            createdAt = 0,
            updatedAt = 0,
        )
        db.contactDao().upsert(contact)
        db.contactDao().upsert(
            ContactEntity(
                id = UUID.randomUUID().toString(),
                firstName = "Alan", lastName = "Turing", organization = null,
                phones = emptyList(), emails = emptyList(),
                address = null, notes = null, createdAt = 0, updatedAt = 0,
            )
        )

        suspend fun count(q: String) = db.contactDao().observeContacts(q).first().size
        assertEquals(1, count("Grace"))
        assertEquals(1, count("Hopper"))
        assertEquals(1, count("Navy"))
        assertEquals(1, count("0199"))    // phone (JSON column LIKE)
        assertEquals(1, count("usn.mil")) // email (JSON column LIKE)
        assertEquals(2, count(""))
        assertEquals(0, count("nonexistent"))
    }

    @Test
    fun contactJsonColumnsRoundTrip() = runTest {
        val contact = ContactEntity(
            id = UUID.randomUUID().toString(),
            firstName = null, lastName = null, organization = "Acme Corp",
            phones = listOf(LabeledValue("mobile", "+34600111222"), LabeledValue("home", "123")),
            emails = listOf(LabeledValue("work", "a@b.c")),
            address = "Somewhere 1", notes = "n", createdAt = 0, updatedAt = 0,
        )
        db.contactDao().upsert(contact)
        val loaded = db.contactDao().observeContact(contact.id).first()
        assertEquals(contact.phones, loaded?.phones)
        assertEquals(contact.emails, loaded?.emails)
        // Soft delete hides it from the list but keeps the row readable by id
        // (the detail screen navigates away on its own); purge removes it.
        db.contactDao().softDelete(listOf(contact.id), now = 7)
        assertTrue(db.contactDao().observeContacts("").first().isEmpty())
        assertEquals(7L, db.contactDao().observeContact(contact.id).first()?.deletedAt)
        db.contactDao().purge(listOf(contact.id))
        assertNull(db.contactDao().observeContact(contact.id).first())
    }
}
