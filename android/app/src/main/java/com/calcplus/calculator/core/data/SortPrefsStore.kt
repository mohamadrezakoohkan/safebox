package com.calcplus.calculator.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.repository.SortPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persists the album and note sort modes (decisions §4) on the app's single
 * Preferences DataStore — the same file the passcode blob and the onboarding
 * flag use; the keys are namespaced so no store's reset touches another's.
 *
 * Everything here is a Flow or a suspend function: there is deliberately NO
 * blocking read. The startup path already pays for one `runBlocking` (the
 * passcode-existence check) and a sort preference is not needed before the
 * first composition — the lists render with the default and switch when the
 * first DataStore emission lands.
 *
 * The stored raw values are exactly the snake_case strings shared with iOS;
 * anything else in the file reads back as the DEFAULT mode.
 */
class SortPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : SortPreferences {
    companion object {
        val KEY_ALBUM_SORT = stringPreferencesKey("album_sort")
        val KEY_NOTE_SORT = stringPreferencesKey("note_sort")
    }

    override val albumSort: Flow<AlbumSort> = dataStore.data
        .map { AlbumSort.fromRaw(it.rawString(KEY_ALBUM_SORT)) }
        .distinctUntilChanged()

    override val noteSort: Flow<NoteSort> = dataStore.data
        .map { NoteSort.fromRaw(it.rawString(KEY_NOTE_SORT)) }
        .distinctUntilChanged()

    override suspend fun setAlbumSort(mode: AlbumSort) {
        dataStore.edit { prefs -> prefs[KEY_ALBUM_SORT] = mode.raw }
    }

    override suspend fun setNoteSort(mode: NoteSort) {
        dataStore.edit { prefs -> prefs[KEY_NOTE_SORT] = mode.raw }
    }

    override suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ALBUM_SORT)
            prefs.remove(KEY_NOTE_SORT)
        }
    }
}

/**
 * Reads a preference as a String WITHOUT the unchecked cast `Preferences.get`
 * performs. A `Preferences.Key` compares by name alone, so a corrupt file
 * holding, say, an Int under `album_sort` would otherwise throw a
 * ClassCastException on the way out; here it simply reads as absent, and the
 * mode falls back to its default.
 */
private fun Preferences.rawString(key: Preferences.Key<String>): String? =
    asMap()[key] as? String
