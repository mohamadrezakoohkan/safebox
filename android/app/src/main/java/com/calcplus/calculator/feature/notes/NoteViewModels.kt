package com.calcplus.calculator.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.Tag
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.core.domain.repository.SortPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NoteListViewModel(
    private val repository: NoteRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filterTagId = MutableStateFlow<String?>(null)
    val filterTagId: StateFlow<String?> = _filterTagId.asStateFlow()

    val tags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The active sort mode (decisions §4); DEFAULT until the store's first emission. */
    val sort: StateFlow<NoteSort> = sortPreferences.noteSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteSort.DEFAULT)

    /**
     * null = first Room emission pending — render nothing, never a false empty
     * state. Ordering happens in the repository, never in a composable body.
     */
    val notes: StateFlow<List<Note>?> =
        combine(_query.debounce(300), _filterTagId, sortPreferences.noteSort) { query, tagId, sort ->
            Triple(query, tagId, sort)
        }
            .flatMapLatest { (query, tagId, sort) ->
                repository.observeNotes(query.trim(), tagId, sort)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilterTag(tagId: String?) {
        _filterTagId.value = tagId
    }

    fun setSort(mode: NoteSort) {
        viewModelScope.launch { sortPreferences.setNoteSort(mode) }
    }

    fun createNote(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createNote()) }
    }

    fun delete(noteId: String) {
        viewModelScope.launch { repository.delete(noteId) }
    }

    // ---- Multi-select (decisions §6) --------------------------------------
    // Same shape as PhotoGridViewModel: the state lives here, so it is torn
    // down with the vault on lock (VaultScaffold is only composed while
    // Unlocked) and there is no lock hook to write.

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _isSelecting = MutableStateFlow(false)
    val isSelecting: StateFlow<Boolean> = _isSelecting.asStateFlow()

    /** Long-press entry: the pressed row is selected straight away. */
    fun startSelecting(noteId: String? = null) {
        _isSelecting.value = true
        if (noteId != null) _selection.value = _selection.value + noteId
    }

    fun exitSelecting() {
        _isSelecting.value = false
        _selection.value = emptySet()
    }

    /** No-op while browsing: a plain tap there opens the note. */
    fun toggleSelection(noteId: String) {
        if (!_isSelecting.value) return
        _selection.value = _selection.value.let {
            if (noteId in it) it - noteId else it + noteId
        }
    }

    /**
     * Soft-deletes the whole selection in ONE repository call (one shared
     * `deletedAt` stamp) and returns the ids, so the caller can offer Undo for
     * exactly that batch. Selection mode always exits, even at zero.
     */
    fun deleteSelected(): List<String> {
        val ids = _selection.value.toList()
        exitSelecting()
        if (ids.isEmpty()) return emptyList()
        viewModelScope.launch { repository.delete(ids) }
        return ids
    }
}

class NoteEditorViewModel(
    private val noteId: String,
    private val repository: NoteRepository,
) : ViewModel() {
    val note: StateFlow<Note?> = repository.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allTags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draftBody = MutableStateFlow<String?>(null)
    val draftBody: StateFlow<String?> = _draftBody.asStateFlow()

    private var autosaveJob: Job? = null

    fun initialiseDraft(body: String) {
        if (_draftBody.value == null) _draftBody.value = body
    }

    /** Autosave contract: 1 s debounce + synchronous flush on exit/backgrounding. */
    fun bodyChanged(body: String) {
        _draftBody.value = body
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1_000)
            flush()
        }
    }

    fun flush() {
        autosaveJob?.cancel()
        autosaveJob = null
        val body = _draftBody.value ?: return
        viewModelScope.launch { repository.saveBody(noteId, body) }
    }

    fun toggleTag(tag: Tag) {
        val current = note.value ?: return
        val currentIds = current.tags.map { it.id }
        val newIds = if (tag.id in currentIds) currentIds - tag.id else currentIds + tag.id
        viewModelScope.launch { repository.setTags(noteId, newIds) }
    }

    fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val tag = repository.getOrCreateTag(trimmed)
            val currentIds = note.value?.tags?.map { it.id } ?: emptyList()
            if (tag.id !in currentIds) {
                repository.setTags(noteId, currentIds + tag.id)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        autosaveJob?.cancel()
        autosaveJob = null
        _draftBody.value = null
        viewModelScope.launch {
            repository.delete(noteId)
            onDeleted()
        }
    }
}
