package com.calcplus.calculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.data.AlbumRepositoryImpl
import com.calcplus.calculator.core.data.ContactRepositoryImpl
import com.calcplus.calculator.core.data.NoteRepositoryImpl
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.data.PhotoRepositoryImpl
import com.calcplus.calculator.core.data.TrashRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.TrashPolicy
import com.calcplus.calculator.core.domain.repository.TrashItemId
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The P3 deletion model end to end (decisions §3): soft delete hides, files
 * survive until purge, album stamps round-trip, expiry purges, and the orphan
 * sweep never touches a trashed photo's bytes.
 *
 * Photo rows are seeded directly (row + two real files) rather than through
 * `PhotoFileStore.store`, so the tests exercise deletion rules and not image
 * decoding.
 */
@RunWith(RobolectricTestRunner::class)
class TrashRepositoryTest {
    private lateinit var db: SafeBoxDatabase
    private lateinit var fileStore: PhotoFileStore
    private lateinit var albums: AlbumRepositoryImpl
    private lateinit var photos: PhotoRepositoryImpl
    private lateinit var notes: NoteRepositoryImpl
    private lateinit var contacts: ContactRepositoryImpl
    private lateinit var trash: TrashRepositoryImpl

    /** Injected clock: every `deletedAt` in these tests is a value we chose. */
    private var clock = 1_000L
    private val now: () -> Long = { clock }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileStore = PhotoFileStore(File(context.filesDir, "trash-${UUID.randomUUID()}"))
        val scope: CoroutineScope = TestScope()
        albums = AlbumRepositoryImpl(db, fileStore, now)
        photos = PhotoRepositoryImpl(db, fileStore, context.contentResolver, scope, now)
        notes = NoteRepositoryImpl(db, now)
        contacts = ContactRepositoryImpl(db, now)
        trash = TrashRepositoryImpl(db, albums, photos, notes, contacts)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedAlbum(name: String): String {
        albums.createAlbum(name)
        return db.albumDao().allAlbums().first { it.name == name }.id
    }

    /** A photo row plus its two real files, exactly as an import would leave them. */
    private suspend fun seedPhoto(albumId: String, sortIndex: Int): PhotoEntity {
        val id = UUID.randomUUID().toString()
        val entity = PhotoEntity(
            id = id,
            albumId = albumId,
            fileName = "$id.jpg",
            thumbFileName = "$id-thumb.jpg",
            mimeType = "image/jpeg",
            width = 10,
            height = 10,
            byteCount = 3,
            importedAt = sortIndex.toLong(),
            sortIndex = sortIndex,
        )
        fileStore.photosDir.mkdirs()
        fileStore.thumbsDir.mkdirs()
        File(fileStore.photosDir, entity.fileName).writeBytes(byteArrayOf(1, 2, 3))
        File(fileStore.thumbsDir, entity.thumbFileName).writeBytes(byteArrayOf(9))
        db.photoDao().insert(entity)
        return entity
    }

    private fun filesExist(photo: PhotoEntity): Boolean =
        File(fileStore.photosDir, photo.fileName).exists() &&
            File(fileStore.thumbsDir, photo.thumbFileName).exists()

    private suspend fun seedContact(first: String): String {
        val id = UUID.randomUUID().toString()
        contacts.upsert(
            Contact(
                id = id,
                firstName = first,
                lastName = null,
                organization = null,
                phones = emptyList(),
                emails = emptyList(),
                address = null,
                notes = null,
                createdAt = 0,
                updatedAt = 0,
            )
        )
        return id
    }

    // ---------------------------------------------------------------- lists

    @Test
    fun softDeletedItemsDisappearFromEveryListAndSearch() = runTest {
        val albumId = seedAlbum("Trips")
        val photo = seedPhoto(albumId, 0)
        val noteId = notes.createNote()
        notes.saveBody(noteId, "the quick brown fox")
        val contactId = seedContact("Grace")

        assertEquals(1, albums.observeAlbums().first().size)
        assertEquals(1, photos.observePhotos(albumId).first().size)
        assertEquals(1, notes.observeNotes("fox", null).first().size)
        assertEquals(1, contacts.observeContacts("Grace").first().size)

        photos.deletePhotos(listOf(photo.id))
        notes.delete(noteId)
        contacts.delete(contactId)

        assertTrue(photos.observePhotos(albumId).first().isEmpty())
        assertTrue(notes.observeNotes("", null).first().isEmpty())
        assertTrue(notes.observeNotes("fox", null).first().isEmpty()) // search too
        assertTrue(contacts.observeContacts("").first().isEmpty())
        assertTrue(contacts.observeContacts("Grace").first().isEmpty())
        // The album is still live, now with zero live photos.
        assertEquals(0, albums.observeAlbums().first().single().photoCount)

        albums.deleteAlbum(albumId)
        assertTrue(albums.observeAlbums().first().isEmpty())
    }

    // ---------------------------------------------------------------- files

    @Test
    fun deletingAPhotoKeepsBothFilesUntilPurge() = runTest {
        val albumId = seedAlbum("Trips")
        val photo = seedPhoto(albumId, 0)

        photos.deletePhotos(listOf(photo.id))
        assertTrue("soft delete must not touch bytes", filesExist(photo))
        assertEquals(clock, db.photoDao().photosByIds(listOf(photo.id)).single().deletedAt)

        photos.purge(listOf(photo.id))
        assertFalse(filesExist(photo))
        assertTrue(db.photoDao().allPhotos().isEmpty())
    }

    @Test
    fun purgingAnAlbumRemovesEveryPhotoRowAndFileIncludingOnesTrashedEarlier() = runTest {
        val albumId = seedAlbum("Trips")
        val early = seedPhoto(albumId, 0)
        val withAlbum = seedPhoto(albumId, 1)
        photos.deletePhotos(listOf(early.id)) // trashed on its own first
        clock += 5_000
        albums.deleteAlbum(albumId)

        albums.purge(listOf(albumId))
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertTrue(db.albumDao().allAlbums().isEmpty())
        assertFalse(filesExist(early))
        assertFalse(filesExist(withAlbum))
    }

    @Test
    fun orphanSweepSparesTheFilesOfTrashedRows() = runTest {
        val albumId = seedAlbum("Trips")
        val trashedPhoto = seedPhoto(albumId, 0)
        val livePhoto = seedPhoto(albumId, 1)
        val orphan = File(fileStore.photosDir, "orphan.jpg").apply { writeBytes(byteArrayOf(7)) }
        photos.deletePhotos(listOf(trashedPhoto.id))
        albums.deleteAlbum(albumId) // …and one trashed with its album

        photos.sweepOrphans()

        assertTrue("a trashed photo keeps its bytes", filesExist(trashedPhoto))
        assertTrue(filesExist(livePhoto))
        assertFalse("a file with no row at all is still swept", orphan.exists())
    }

    // --------------------------------------------------------------- albums

    @Test
    fun deletingAnAlbumStampsItsLivePhotosWithTheSameInstant() = runTest {
        val albumId = seedAlbum("Trips")
        val early = seedPhoto(albumId, 0)
        val withAlbum = seedPhoto(albumId, 1)
        photos.deletePhotos(listOf(early.id))
        val earlyStamp = clock
        clock += 60_000
        albums.deleteAlbum(albumId)

        val albumStamp = db.albumDao().album(albumId)?.deletedAt
        assertEquals(clock, albumStamp)
        val stamps = db.photoDao().allPhotos().associate { it.id to it.deletedAt }
        assertEquals(albumStamp, stamps[withAlbum.id])
        assertEquals(earlyStamp, stamps[early.id]) // keeps its own, earlier stamp
    }

    @Test
    fun restoringAnAlbumRestoresOnlyThePhotosItTookWithIt() = runTest {
        val albumId = seedAlbum("Trips")
        val early = seedPhoto(albumId, 0)
        val withAlbum = seedPhoto(albumId, 1)
        photos.deletePhotos(listOf(early.id))
        clock += 60_000
        albums.deleteAlbum(albumId)

        albums.restore(listOf(albumId))

        assertEquals(1, albums.observeAlbums().first().size)
        val live = photos.observePhotos(albumId).first().map { it.id }
        assertEquals(listOf(withAlbum.id), live)
        assertNotNull("the earlier delete stays in the trash", db.photoDao().photosByIds(listOf(early.id)).single().deletedAt)
    }

    @Test
    fun restoringAPhotoKeepsItsSortIndexAndAlbum() = runTest {
        val albumId = seedAlbum("Trips")
        seedPhoto(albumId, 0)
        val second = seedPhoto(albumId, 1)
        seedPhoto(albumId, 2)

        photos.deletePhotos(listOf(second.id))
        photos.restore(listOf(second.id))

        val order = photos.observePhotos(albumId).first()
        assertEquals(listOf(0, 1, 2), order.map { it.sortIndex })
        assertEquals(second.id, order[1].id) // back in place, same album
        assertEquals(albumId, order[1].albumId)
    }

    @Test
    fun restoringAPhotoWhoseAlbumIsTrashedBringsTheAlbumRowBackButNotItsSiblings() = runTest {
        val albumId = seedAlbum("Trips")
        val one = seedPhoto(albumId, 0)
        val two = seedPhoto(albumId, 1)
        albums.deleteAlbum(albumId)

        photos.restore(listOf(one.id))

        assertEquals(1, albums.observeAlbums().first().size) // album is reachable again
        assertEquals(listOf(one.id), photos.observePhotos(albumId).first().map { it.id })
        assertNotNull(db.photoDao().photosByIds(listOf(two.id)).single().deletedAt)
    }

    @Test
    fun anImportLandingAfterItsAlbumWasTrashedJoinsTheAlbumInTheTrash() = runTest {
        val albumId = seedAlbum("Trips")
        albums.deleteAlbum(albumId)
        // The row insert an import would perform, via the repository's own path.
        val late = seedPhoto(albumId, 0)
        // (seedPhoto inserts directly, so mirror what `import` computes.)
        db.photoDao().softDelete(listOf(late.id), db.albumDao().album(albumId)!!.deletedAt!!)

        assertTrue(photos.observePhotos(albumId).first().isEmpty())
        albums.restore(listOf(albumId))
        assertEquals(listOf(late.id), photos.observePhotos(albumId).first().map { it.id })
    }

    // --------------------------------------------------------------- expiry

    @Test
    fun expiredItemsPurgeAndFresherOnesSurvive() = runTest {
        val albumId = seedAlbum("Trips")
        val oldPhoto = seedPhoto(albumId, 0)
        val freshPhoto = seedPhoto(albumId, 1)
        val oldNote = notes.createNote()
        val oldContact = seedContact("Old")

        clock = 0L
        photos.deletePhotos(listOf(oldPhoto.id))
        notes.delete(oldNote)
        contacts.delete(oldContact)
        clock = TrashPolicy.RETENTION_MS // 30 days later
        photos.deletePhotos(listOf(freshPhoto.id))

        // Exactly at retention the oldest items are due (inclusive rule).
        trash.purgeExpired(TrashPolicy.RETENTION_MS)

        assertEquals(listOf(freshPhoto.id), db.photoDao().allPhotos().map { it.id })
        assertFalse(filesExist(oldPhoto))
        assertTrue(filesExist(freshPhoto))
        assertTrue(db.noteDao().allNotes().isEmpty())
        assertTrue(db.contactDao().all().isEmpty())
    }

    @Test
    fun purgeExpiredTakesTheWholeAlbumWithIt() = runTest {
        val albumId = seedAlbum("Trips")
        val photo = seedPhoto(albumId, 0)
        clock = 0L
        albums.deleteAlbum(albumId)

        trash.purgeExpired(TrashPolicy.RETENTION_MS - 1)
        assertEquals(1, db.albumDao().allAlbums().size) // one millisecond short

        trash.purgeExpired(TrashPolicy.RETENTION_MS)
        assertTrue(db.albumDao().allAlbums().isEmpty())
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertFalse(filesExist(photo))
    }

    // ------------------------------------------------------------ the trash

    @Test
    fun trashContentsGroupByTypeAndHidePhotosThatWentWithTheirAlbum() = runTest {
        val liveAlbum = seedAlbum("Live")
        val doomedAlbum = seedAlbum("Doomed")
        val lonePhoto = seedPhoto(liveAlbum, 0)
        val albumPhotoA = seedPhoto(doomedAlbum, 0)
        seedPhoto(doomedAlbum, 1)
        val noteId = notes.createNote()
        val contactId = seedContact("Grace")

        photos.deletePhotos(listOf(lonePhoto.id))
        notes.delete(noteId)
        contacts.delete(contactId)
        clock += 1_000
        albums.deleteAlbum(doomedAlbum)

        val contents = trash.observeTrash().first()
        assertEquals(listOf(doomedAlbum), contents.albums.map { it.id })
        assertEquals(2, contents.albums.single().photoCount) // its trashed photos
        assertEquals(albumPhotoA.thumbFileName, contents.albums.single().coverThumbFileName)
        assertEquals(listOf(lonePhoto.id), contents.photos.map { it.id }) // NOT the album's two
        assertEquals("Live", contents.albumNames[liveAlbum])
        assertEquals(listOf(noteId), contents.notes.map { it.id })
        assertEquals(listOf(contactId), contents.contacts.map { it.id })
        assertEquals(4, contents.itemCount)
        assertFalse(contents.isEmpty)
    }

    @Test
    fun anAlbumRowCountsOnlyThePhotosItsRestoreWillBringBack() = runTest {
        val albumId = seedAlbum("Trips")
        val early = seedPhoto(albumId, 0) // trashed on its own, BEFORE the album
        val withAlbumA = seedPhoto(albumId, 1)
        val withAlbumB = seedPhoto(albumId, 2)

        photos.deletePhotos(listOf(early.id))
        clock += 1_000
        albums.deleteAlbum(albumId)

        val row = trash.observeTrash().first().albums.single()
        // Three of its photos are in the trash, but restore only clears the two
        // that carry the album's own stamp — the row must not promise the third
        // (iOS `TrashViewModel.trashedPhotoCount(in:)` counts the same set).
        assertEquals(2, row.photoCount)
        // The cover is still the first trashed photo by sortIndex whatever its
        // stamp (iOS `coverPhoto(for:)`), so the row is never a blank tile.
        assertEquals(early.thumbFileName, row.coverThumbFileName)

        trash.restore(listOf(TrashItemId(TrashItemKind.ALBUM, albumId)))

        // Exactly what the row promised came back…
        assertEquals(
            listOf(withAlbumA.id, withAlbumB.id),
            photos.observePhotos(albumId).first().map { it.id },
        )
        // …and the earlier photo stays in the trash, now on its own under a
        // live album, instead of reappearing in the album.
        val afterRestore = trash.observeTrash().first()
        assertTrue(afterRestore.albums.isEmpty())
        assertEquals(listOf(early.id), afterRestore.photos.map { it.id })
    }

    @Test
    fun trashIsEmptyWhenNothingWasDeleted() = runTest {
        val albumId = seedAlbum("Trips")
        seedPhoto(albumId, 0)
        val contents = trash.observeTrash().first()
        assertTrue(contents.isEmpty)
        assertEquals(0, contents.itemCount)
    }

    @Test
    fun restoreRoutesEachKindBackToItsRepository() = runTest {
        val albumId = seedAlbum("Trips")
        val photo = seedPhoto(albumId, 0)
        val otherAlbum = seedAlbum("Other")
        val otherPhoto = seedPhoto(otherAlbum, 0)
        val noteId = notes.createNote()
        val contactId = seedContact("Grace")

        photos.deletePhotos(listOf(otherPhoto.id))
        notes.delete(noteId)
        contacts.delete(contactId)
        albums.deleteAlbum(albumId)

        trash.restore(
            listOf(
                TrashItemId(TrashItemKind.ALBUM, albumId),
                TrashItemId(TrashItemKind.PHOTO, otherPhoto.id),
                TrashItemId(TrashItemKind.NOTE, noteId),
                TrashItemId(TrashItemKind.CONTACT, contactId),
            )
        )

        assertTrue(trash.observeTrash().first().isEmpty)
        assertEquals(2, albums.observeAlbums().first().size)
        assertEquals(listOf(photo.id), photos.observePhotos(albumId).first().map { it.id })
        assertEquals(listOf(otherPhoto.id), photos.observePhotos(otherAlbum).first().map { it.id })
        assertEquals(1, notes.observeNotes("", null).first().size)
        assertEquals(1, contacts.observeContacts("").first().size)
    }

    @Test
    fun purgeOfAnAlbumAndOneOfItsPhotosInTheSameCallDoesNotFail() = runTest {
        val albumId = seedAlbum("Trips")
        val photo = seedPhoto(albumId, 0)
        albums.deleteAlbum(albumId)

        // The album purge already removed the photo row; the photo id must be
        // re-resolved rather than purged blindly.
        trash.purge(
            listOf(
                TrashItemId(TrashItemKind.ALBUM, albumId),
                TrashItemId(TrashItemKind.PHOTO, photo.id),
            )
        )

        assertTrue(db.albumDao().allAlbums().isEmpty())
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertFalse(filesExist(photo))
    }

    @Test
    fun emptyAllRemovesEveryTrashedRowAndFileAndLeavesLiveContentAlone() = runTest {
        val liveAlbum = seedAlbum("Live")
        val livePhoto = seedPhoto(liveAlbum, 0)
        val doomedAlbum = seedAlbum("Doomed")
        val albumPhoto = seedPhoto(doomedAlbum, 0)
        val lonePhoto = seedPhoto(liveAlbum, 1)
        val noteId = notes.createNote()
        val contactId = seedContact("Grace")

        photos.deletePhotos(listOf(lonePhoto.id))
        notes.delete(noteId)
        contacts.delete(contactId)
        albums.deleteAlbum(doomedAlbum)

        trash.emptyAll()

        assertTrue(trash.observeTrash().first().isEmpty)
        assertEquals(listOf(liveAlbum), db.albumDao().allAlbums().map { it.id })
        assertEquals(listOf(livePhoto.id), db.photoDao().allPhotos().map { it.id })
        assertTrue(db.noteDao().allNotes().isEmpty())
        assertTrue(db.contactDao().all().isEmpty())
        assertFalse(filesExist(albumPhoto))
        assertFalse(filesExist(lonePhoto))
        assertTrue(filesExist(livePhoto))
    }

    @Test
    fun aBulkNoteDeleteUsesOneStampForTheWholeBatch() = runTest {
        val first = notes.createNote()
        val second = notes.createNote()
        val third = notes.createNote()

        notes.delete(listOf(first, second, third))

        val stamps = db.noteDao().allNotes().map { it.deletedAt }.toSet()
        assertEquals(setOf(clock), stamps)
        assertTrue(notes.observeNotes("", null).first().isEmpty())

        notes.restore(listOf(first, third))
        assertEquals(setOf(first, third), notes.observeNotes("", null).first().map { it.id }.toSet())
        assertNull(db.noteDao().note(first)?.deletedAt)
    }

    @Test
    fun aBulkContactDeleteUsesOneStampForTheWholeBatch() = runTest {
        val first = seedContact("Ada")
        val second = seedContact("Grace")

        contacts.delete(listOf(first, second))

        assertEquals(setOf(clock), db.contactDao().all().map { it.deletedAt }.toSet())
        contacts.restore(listOf(second))
        assertEquals(listOf(second), contacts.observeContacts("").first().map { it.id })
    }

    @Test
    fun createAlbumPicksASortIndexAboveTrashedAlbumsToo() = runTest {
        val first = seedAlbum("First")
        albums.deleteAlbum(first)
        val second = seedAlbum("Second")

        val indices = db.albumDao().allAlbums().associate { it.id to it.sortIndex }
        assertEquals(0, indices[first])
        assertEquals(1, indices[second]) // never collides with the trashed one
    }

    @Test
    fun aTrashedAlbumRowStaysReadableByIdSoRestoreCanFindIt() = runTest {
        val albumId = seedAlbum("Trips")
        albums.deleteAlbum(albumId)
        val entity = db.albumDao().album(albumId)
        assertNotNull(entity)
        assertEquals(clock, entity?.deletedAt)
    }

    @Test
    fun contactAndNoteRowsCarryTheirStampIntoTheDomainModel() = runTest {
        val contactId = seedContact("Grace")
        val noteId = notes.createNote()
        contacts.delete(contactId)
        notes.delete(noteId)

        val contents = trash.observeTrash().first()
        assertEquals(clock, contents.contacts.single().deletedAt)
        assertEquals(clock, contents.notes.single().deletedAt)
        assertEquals(TrashPolicy.RETENTION_DAYS, TrashPolicy.daysLeft(clock, clock))
    }
}
