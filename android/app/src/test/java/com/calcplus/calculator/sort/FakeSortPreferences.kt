package com.calcplus.calculator.sort

import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.repository.SortPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SortPreferences] (hand-written, no mocking library) so the list
 * view models can be tested without a DataStore file.
 */
class FakeSortPreferences(
    album: AlbumSort = AlbumSort.DEFAULT,
    note: NoteSort = NoteSort.DEFAULT,
) : SortPreferences {
    val albumState = MutableStateFlow(album)
    val noteState = MutableStateFlow(note)
    var resetCalls = 0

    override val albumSort: Flow<AlbumSort> = albumState
    override val noteSort: Flow<NoteSort> = noteState

    override suspend fun setAlbumSort(mode: AlbumSort) {
        albumState.value = mode
    }

    override suspend fun setNoteSort(mode: NoteSort) {
        noteState.value = mode
    }

    override suspend fun reset() {
        resetCalls += 1
        albumState.value = AlbumSort.DEFAULT
        noteState.value = NoteSort.DEFAULT
    }
}
