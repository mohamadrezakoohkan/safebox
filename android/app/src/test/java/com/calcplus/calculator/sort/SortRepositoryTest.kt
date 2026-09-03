package com.calcplus.calculator.sort

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.data.AlbumRepositoryImpl
import com.calcplus.calculator.core.data.NoteRepositoryImpl
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Sorting is applied in the repository over the fetched list (decisions §4).
 * These are the assertions that need real Room: the `photo_count` mode must
 * rank albums by LIVE photos only, and a trashed row must never influence any
 * order.
 */
@RunWith(RobolectricTestRunner::class)
class SortRepositoryTest {
    private lateinit var db: SafeBoxDatabase
    private lateinit var albums: AlbumRepositoryImpl
    private lateinit var notes: NoteRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val fileStore = PhotoFileStore(File(context.filesDir, "sort-${UUID.randomUUID()}"))
        albums = AlbumRepositoryImpl(db, fileStore) { 1_000L }
        notes = NoteRepositoryImpl(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun photo(albumId: String, id: String, deletedAt: Long? = null) {
        db.photoDao().insert(
            PhotoEntity(
                id = id,
                albumId = albumId,
                fileName = "$id.jpg",
                thumbFileName = "$id-t.jpg",
                mimeType = "image/jpeg",
                width = 1, height = 1, byteCount = 1, importedAt = 0, sortIndex = 0,
                deletedAt = deletedAt,
            )
        )
    }

    @Test
    fun photoCountRanksAlbumsByLivePhotosOnly() = runTest {
        db.albumDao().insert(AlbumEntity("a", "Alpha", createdAt = 0, sortIndex = 0))
        db.albumDao().insert(AlbumEntity("b", "Bravo", createdAt = 0, sortIndex = 1))
        photo("a", "a1")
        photo("a", "a2")
        photo("b", "b1")
        photo("b", "b2")
        photo("b", "b3")

        assertEquals(
            listOf("b", "a"),
            albums.observeAlbums(AlbumSort.PHOTO_COUNT).first().map { it.id },
        )

        // Two of Bravo's three photos go to "Recently deleted": the ranking has
        // to follow the LIVE count, not the rows still sitting in the table.
        db.photoDao().softDelete(listOf("b2", "b3"), 500)
        val ranked = albums.observeAlbums(AlbumSort.PHOTO_COUNT).first()
        assertEquals(listOf("a", "b"), ranked.map { it.id })
        assertEquals(listOf(2, 1), ranked.map { it.photoCount })
    }

    @Test
    fun trashedAlbumsAndNotesNeverAppearInAnySortedList() = runTest {
        db.albumDao().insert(AlbumEntity("live", "Live", createdAt = 0, sortIndex = 0))
        db.albumDao().insert(
            AlbumEntity("gone", "Gone", createdAt = 0, sortIndex = 1, deletedAt = 500)
        )
        db.noteDao().upsert(NoteEntity("n1", "Apple", "Apple", "", 0, 0))
        db.noteDao().upsert(NoteEntity("n2", "Banana", "Banana", "", 0, 0, deletedAt = 500))

        for (mode in AlbumSort.entries) {
            assertEquals(
                "album mode $mode",
                listOf("live"),
                albums.observeAlbums(mode).first().map { it.id },
            )
        }
        for (mode in NoteSort.entries) {
            assertEquals(
                "note mode $mode",
                listOf("n1"),
                notes.observeNotes("", null, mode).first().map { it.id },
            )
        }
    }

    @Test
    fun theRepositoryReordersTheDaoResultPerMode() = runTest {
        // The DAO's base order is sortIndex (albums) / updatedAt desc (notes);
        // every other mode has to come from the repository's own sort.
        db.albumDao().insert(AlbumEntity("first", "Zulu", createdAt = 100, sortIndex = 0))
        db.albumDao().insert(AlbumEntity("second", "Alpha", createdAt = 200, sortIndex = 1))

        assertEquals(
            listOf("first", "second"),
            albums.observeAlbums(AlbumSort.MANUAL).first().map { it.id },
        )
        assertEquals(
            listOf("second", "first"),
            albums.observeAlbums(AlbumSort.NAME).first().map { it.id },
        )
        assertEquals(
            listOf("second", "first"),
            albums.observeAlbums(AlbumSort.DATE_CREATED).first().map { it.id },
        )

        db.noteDao().upsert(NoteEntity("recent", "Zulu", "Zulu", "", createdAt = 1, updatedAt = 90))
        db.noteDao().upsert(NoteEntity("older", "Apple", "Apple", "", createdAt = 50, updatedAt = 10))

        assertEquals(
            listOf("recent", "older"),
            notes.observeNotes("", null, NoteSort.DATE_MODIFIED).first().map { it.id },
        )
        assertEquals(
            listOf("older", "recent"),
            notes.observeNotes("", null, NoteSort.DATE_CREATED).first().map { it.id },
        )
        assertEquals(
            listOf("older", "recent"),
            notes.observeNotes("", null, NoteSort.TITLE).first().map { it.id },
        )
    }

    @Test
    fun theDefaultArgumentKeepsPreP4CallSitesOnTheDecidedDefaults() = runTest {
        db.albumDao().insert(AlbumEntity("second", "Alpha", createdAt = 200, sortIndex = 1))
        db.albumDao().insert(AlbumEntity("first", "Zulu", createdAt = 100, sortIndex = 0))
        db.noteDao().upsert(NoteEntity("old", "A", "A", "", createdAt = 9, updatedAt = 1))
        db.noteDao().upsert(NoteEntity("new", "B", "B", "", createdAt = 1, updatedAt = 9))

        // manual for albums, date_modified for notes.
        assertEquals(listOf("first", "second"), albums.observeAlbums().first().map { it.id })
        assertEquals(listOf("new", "old"), notes.observeNotes("", null).first().map { it.id })
    }
}
