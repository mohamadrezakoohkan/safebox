package com.calcplus.calculator.sort

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.data.SortPrefsStore
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The persisted sort preference (decisions §4): raw values shared with iOS, a
 * default for anything unreadable, and survival across a relaunch (a fresh
 * store instance over the same file).
 */
@RunWith(RobolectricTestRunner::class)
class SortPrefsStoreTest {
    private lateinit var file: File

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { file })

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.filesDir, "sort-${UUID.randomUUID()}.preferences_pb")
    }

    @Test
    fun rawValuesAreTheSharedCrossPlatformStrings() {
        assertEquals("manual", AlbumSort.MANUAL.raw)
        assertEquals("name", AlbumSort.NAME.raw)
        assertEquals("date_created", AlbumSort.DATE_CREATED.raw)
        assertEquals("photo_count", AlbumSort.PHOTO_COUNT.raw)
        assertEquals("date_modified", NoteSort.DATE_MODIFIED.raw)
        assertEquals("date_created", NoteSort.DATE_CREATED.raw)
        assertEquals("title", NoteSort.TITLE.raw)
        assertEquals("album_sort", SortPrefsStore.KEY_ALBUM_SORT.name)
        assertEquals("note_sort", SortPrefsStore.KEY_NOTE_SORT.name)
    }

    @Test
    fun defaultsAreTheDecidedOnes() = runTest {
        val store = SortPrefsStore(newDataStore())
        assertEquals(AlbumSort.MANUAL, AlbumSort.DEFAULT)
        assertEquals(NoteSort.DATE_MODIFIED, NoteSort.DEFAULT)
        assertEquals(AlbumSort.DEFAULT, store.albumSort.first())
        assertEquals(NoteSort.DEFAULT, store.noteSort.first())
    }

    @Test
    fun thePreferenceSurvivesARelaunch() = runTest {
        // A fresh store over the same file is what a process restart looks like.
        val dataStore = newDataStore()
        SortPrefsStore(dataStore).setAlbumSort(AlbumSort.PHOTO_COUNT)
        SortPrefsStore(dataStore).setNoteSort(NoteSort.TITLE)

        val reopened = SortPrefsStore(dataStore)
        assertEquals(AlbumSort.PHOTO_COUNT, reopened.albumSort.first())
        assertEquals(NoteSort.TITLE, reopened.noteSort.first())
    }

    @Test
    fun everyModeRoundTrips() = runTest {
        val store = SortPrefsStore(newDataStore())
        for (mode in AlbumSort.entries) {
            store.setAlbumSort(mode)
            assertEquals(mode, store.albumSort.first())
        }
        for (mode in NoteSort.entries) {
            store.setNoteSort(mode)
            assertEquals(mode, store.noteSort.first())
        }
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { prefs ->
            prefs[SortPrefsStore.KEY_ALBUM_SORT] = "by_vibes"
            prefs[SortPrefsStore.KEY_NOTE_SORT] = ""
        }
        val store = SortPrefsStore(dataStore)
        assertEquals(AlbumSort.DEFAULT, store.albumSort.first())
        assertEquals(NoteSort.DEFAULT, store.noteSort.first())
    }

    @Test
    fun aValueOfTheWrongTypeAlsoFallsBackToTheDefault() = runTest {
        // Nothing writes an Int under these names, but a corrupt file could.
        val dataStore = newDataStore()
        dataStore.edit { prefs -> prefs[intPreferencesKey("album_sort")] = 2 }
        val store = SortPrefsStore(dataStore)
        assertEquals(AlbumSort.DEFAULT, store.albumSort.first())
    }

    @Test
    fun fromRawIsTotal() {
        assertEquals(AlbumSort.DEFAULT, AlbumSort.fromRaw(null))
        assertEquals(AlbumSort.DEFAULT, AlbumSort.fromRaw("Manual")) // case-sensitive on purpose
        assertEquals(NoteSort.DEFAULT, NoteSort.fromRaw(null))
        assertEquals(NoteSort.DEFAULT, NoteSort.fromRaw("photo_count"))
        // …but a note and an album really do share the `date_created` value.
        assertEquals(NoteSort.DATE_CREATED, NoteSort.fromRaw("date_created"))
        assertEquals(AlbumSort.DATE_CREATED, AlbumSort.fromRaw("date_created"))
    }

    @Test
    fun resetRemovesBothKeysAndNothingElse() = runTest {
        val dataStore = newDataStore()
        val store = SortPrefsStore(dataStore)
        val onboarding = OnboardingStore(dataStore)
        val unrelated = stringPreferencesKey("unrelated")
        dataStore.edit { it[unrelated] = "keep me" }
        onboarding.setComplete()
        store.setAlbumSort(AlbumSort.NAME)
        store.setNoteSort(NoteSort.TITLE)

        store.reset()

        assertEquals(AlbumSort.DEFAULT, store.albumSort.first())
        assertEquals(NoteSort.DEFAULT, store.noteSort.first())
        // The shared prefs file keeps every other namespace intact.
        assertEquals("keep me", dataStore.data.first()[unrelated])
        assertTrue(onboarding.isCompleteBlocking())
    }
}
