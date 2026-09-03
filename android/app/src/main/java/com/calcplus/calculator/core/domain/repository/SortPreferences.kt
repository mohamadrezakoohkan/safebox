package com.calcplus.calculator.core.domain.repository

import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import kotlinx.coroutines.flow.Flow

/**
 * The two persisted sort choices (decisions §4): global, not per album or tab.
 * The production implementation is `SortPrefsStore` on the app's one
 * Preferences DataStore; the interface exists so view models and `VaultNuker`
 * can be tested without a DataStore file.
 *
 * Both flows emit the DEFAULT mode for an absent, unknown or corrupt stored
 * value — reading a preference can never fail.
 */
interface SortPreferences {
    val albumSort: Flow<AlbumSort>
    val noteSort: Flow<NoteSort>
    suspend fun setAlbumSort(mode: AlbumSort)
    suspend fun setNoteSort(mode: NoteSort)
    /** Erase everything returns both preferences to their just-installed default. */
    suspend fun reset()
}
