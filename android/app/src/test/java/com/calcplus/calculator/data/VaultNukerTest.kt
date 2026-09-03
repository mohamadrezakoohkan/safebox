package com.calcplus.calculator.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.data.PasscodeRepositoryImpl
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.data.SortPrefsStore
import com.calcplus.calculator.core.data.VaultNuker
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.feature.calculator.CalcKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Deterministic XOR stand-in for the Keystore wrap. */
private class XorWrapper : BlobWrapper {
    override fun wrap(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray = wrapped.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

@RunWith(RobolectricTestRunner::class)
class VaultNukerTest {
    private val code = listOf(CalcKey.D7, CalcKey.ADD, CalcKey.D7, CalcKey.PCT)

    private lateinit var db: SafeBoxDatabase
    private lateinit var fileStore: PhotoFileStore
    private lateinit var passcodeStore: PasscodeStore
    private lateinit var onboardingStore: OnboardingStore
    private lateinit var sortPrefsStore: SortPrefsStore
    private lateinit var lockManager: AppLockManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileStore = PhotoFileStore(File(context.filesDir, "nuke-${UUID.randomUUID()}"))
        // Mirror production: passcode blob and onboarding flag share ONE
        // DataStore file (a single startup read serves both).
        val sharedDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "nuke-prefs-${UUID.randomUUID()}.preferences_pb") }
        )
        passcodeStore = PasscodeStore(sharedDataStore, XorWrapper(), iterations = 1_000)
        onboardingStore = OnboardingStore(sharedDataStore)
        sortPrefsStore = SortPrefsStore(sharedDataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun nukeErasesRowsFilesPasscodeAndOnboardingFlag() = runTest {
        // Seed a populated, set-up, unlocked vault — including TRASHED rows and
        // a TAGGED note, the two shapes that a batch table clear can silently
        // skip (found on iOS, where a mandatory inverse relationship made
        // erase-everything leave tagged notes behind).
        val albumId = UUID.randomUUID().toString()
        db.albumDao().insert(AlbumEntity(albumId, "Trips", 0, 0))
        db.photoDao().insert(
            PhotoEntity(
                id = UUID.randomUUID().toString(),
                albumId = albumId,
                fileName = "p.jpg",
                thumbFileName = "t.jpg",
                mimeType = "image/jpeg",
                width = 10, height = 10, byteCount = 3, importedAt = 0, sortIndex = 0,
            )
        )
        db.noteDao().upsert(NoteEntity(UUID.randomUUID().toString(), "milk", "milk", "", 0, 0))
        db.contactDao().upsert(
            ContactEntity(
                id = UUID.randomUUID().toString(),
                firstName = "Ada", lastName = null, organization = null,
                phones = emptyList(), emails = emptyList(),
                address = null, notes = null, createdAt = 0, updatedAt = 0,
            )
        )

        // A trashed album with a trashed photo, a trashed tagged note, and a
        // trashed contact — all still on disk, all due to go.
        val trashedAlbumId = UUID.randomUUID().toString()
        db.albumDao().insert(AlbumEntity(trashedAlbumId, "Deleted", 0, 1, deletedAt = 50))
        val trashedPhotoId = UUID.randomUUID().toString()
        db.photoDao().insert(
            PhotoEntity(
                id = trashedPhotoId,
                albumId = trashedAlbumId,
                fileName = "trashed.jpg",
                thumbFileName = "trashed-thumb.jpg",
                mimeType = "image/jpeg",
                width = 10, height = 10, byteCount = 3, importedAt = 0, sortIndex = 0,
                deletedAt = 50,
            )
        )
        val taggedNoteId = UUID.randomUUID().toString()
        db.noteDao().upsert(NoteEntity(taggedNoteId, "secret", "secret", "", 0, 0, deletedAt = 50))
        val tagId = UUID.randomUUID().toString()
        db.tagDao().insert(TagEntity(tagId, "private", 0))
        db.noteDao().setTags(taggedNoteId, listOf(tagId))
        db.contactDao().upsert(
            ContactEntity(
                id = UUID.randomUUID().toString(),
                firstName = "Grace", lastName = null, organization = null,
                phones = emptyList(), emails = emptyList(),
                address = null, notes = null, createdAt = 0, updatedAt = 0,
                deletedAt = 50,
            )
        )

        fileStore.photosDir.mkdirs()
        fileStore.thumbsDir.mkdirs()
        File(fileStore.photosDir, "p.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(fileStore.thumbsDir, "t.jpg").writeBytes(byteArrayOf(1))
        File(fileStore.photosDir, "trashed.jpg").writeBytes(byteArrayOf(4, 5, 6))
        File(fileStore.thumbsDir, "trashed-thumb.jpg").writeBytes(byteArrayOf(2))
        passcodeStore.set(code)
        onboardingStore.setComplete()
        // Non-default sort choices (decisions §4): erase must return both to
        // the just-installed default.
        sortPrefsStore.setAlbumSort(AlbumSort.PHOTO_COUNT)
        sortPrefsStore.setNoteSort(NoteSort.TITLE)
        lockManager = AppLockManager(
            PasscodeRepositoryImpl(passcodeStore),
            hasPasscode = true,
            elapsedRealtime = { 0L },
            onboardingComplete = true,
        )
        lockManager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, lockManager.lockState.value)

        VaultNuker(db, fileStore, passcodeStore, onboardingStore, sortPrefsStore, lockManager).nuke()

        // Rows: every table empty, asserted through the UNFILTERED queries —
        // the live queries hide trashed rows, so they could never prove this.
        assertTrue(db.albumDao().allAlbums().isEmpty())
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertTrue(db.noteDao().allNotes().isEmpty())
        assertTrue(db.contactDao().all().isEmpty())
        assertTrue(db.noteDao().trashedNotes().isEmpty())
        assertTrue(db.photoDao().trashedPhotos().isEmpty())
        assertTrue(db.contactDao().trashedContacts().isEmpty())
        assertTrue(db.albumDao().trashedAlbums().isEmpty())
        // …tags and their join rows included (a tagged note is the shape that
        // survived a batch clear on iOS).
        assertTrue(db.tagDao().all().isEmpty())
        assertEquals(0, db.tagDao().count())
        // Bytes: nothing left on disk, trashed media included.
        assertTrue(fileStore.photosDir.listFiles().isNullOrEmpty())
        assertTrue(fileStore.thumbsDir.listFiles().isNullOrEmpty())
        // Passcode + onboarding flag gone.
        assertFalse(passcodeStore.hasPasscodeBlocking())
        assertFalse(passcodeStore.matches(code))
        assertFalse(onboardingStore.isCompleteBlocking())
        // Sort preferences back to their defaults (decisions §4).
        assertEquals(AlbumSort.DEFAULT, sortPrefsStore.albumSort.first())
        assertEquals(NoteSort.DEFAULT, sortPrefsStore.noteSort.first())
        // State machine back to first-run: setup mode with the guide showing.
        assertEquals(LockState.NeedsSetup, lockManager.lockState.value)
        assertTrue(lockManager.showOnboarding.value)
    }

    @Test
    fun onboardingStoreRoundTrip() = runTest {
        assertFalse(onboardingStore.isCompleteBlocking())
        onboardingStore.setComplete()
        assertTrue(onboardingStore.isCompleteBlocking())
        onboardingStore.reset()
        assertFalse(onboardingStore.isCompleteBlocking())
    }

    @Test
    fun sharedPrefsFileKeysAreIndependent() = runTest {
        // Both stores write to the same file: clearing one namespace must
        // never disturb the other.
        passcodeStore.set(code)
        onboardingStore.setComplete()
        passcodeStore.clear()
        assertTrue(onboardingStore.isCompleteBlocking())
        assertFalse(passcodeStore.hasPasscodeBlocking())
        passcodeStore.set(code)
        onboardingStore.reset()
        assertTrue(passcodeStore.hasPasscodeBlocking())
        assertTrue(passcodeStore.matches(code))
    }
}
