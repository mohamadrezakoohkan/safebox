package com.calcplus.calculator.feature.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ImportProgress
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.core.domain.repository.SortPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumListViewModel(
    private val albumRepository: AlbumRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {
    /** The active sort mode (decisions §4); DEFAULT until the store's first emission. */
    val sort: StateFlow<AlbumSort> = sortPreferences.albumSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumSort.DEFAULT)

    /**
     * null = first Room emission pending — render nothing, never a false empty
     * state. Ordering happens in the repository, never in a composable body.
     */
    val albums: StateFlow<List<Album>?> = sortPreferences.albumSort
        .flatMapLatest { albumRepository.observeAlbums(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(mode: AlbumSort) {
        viewModelScope.launch { sortPreferences.setAlbumSort(mode) }
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { albumRepository.createAlbum(trimmed) }
    }

    fun renameAlbum(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { albumRepository.renameAlbum(id, trimmed) }
    }

    fun deleteAlbum(id: String) {
        viewModelScope.launch { albumRepository.deleteAlbum(id) }
    }
}

class PhotoGridViewModel(
    private val albumId: String,
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    /** null = first Room emission pending — render nothing, never a false empty state. */
    val photos: StateFlow<List<Photo>?> = photoRepository.observePhotos(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importProgress: StateFlow<ImportProgress> = photoRepository.importProgress

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _isSelecting = MutableStateFlow(false)
    val isSelecting: StateFlow<Boolean> = _isSelecting.asStateFlow()

    fun startSelecting() {
        _isSelecting.value = true
    }

    fun exitSelecting() {
        _isSelecting.value = false
        _selection.value = emptySet()
    }

    fun toggleSelection(photoId: String) {
        _selection.value = _selection.value.let {
            if (photoId in it) it - photoId else it + photoId
        }
    }

    /** Import completes at the repository level even if the vault locks (§2.3). */
    fun import(uris: List<Uri>) {
        photoRepository.import(albumId, uris)
    }

    /**
     * Soft-deletes the selection in ONE repository call (one shared stamp) and
     * returns the ids it deleted, so the caller can offer Undo for exactly
     * those photos.
     */
    fun deleteSelected(): List<String> {
        val ids = _selection.value.toList()
        exitSelecting()
        viewModelScope.launch { photoRepository.deletePhotos(ids) }
        return ids
    }

    fun moveSelected(toAlbumId: String) {
        val ids = _selection.value.toList()
        exitSelecting()
        viewModelScope.launch { photoRepository.movePhotos(ids, toAlbumId) }
    }
}

class PhotoPagerViewModel(
    albumId: String,
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    /**
     * `null` means "the first Room emission has not arrived yet" — the same
     * convention every other list in the vault uses. It must NOT be
     * `emptyList()`: the pager treats an empty album as "nothing to page" and
     * pops itself, so a pending-but-empty initial value closed the pager the
     * instant it opened (no photo or video could be viewed at all).
     */
    val photos: StateFlow<List<Photo>?> = photoRepository.observePhotos(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(photoId: String) {
        viewModelScope.launch { photoRepository.deletePhotos(listOf(photoId)) }
    }

    fun move(photoId: String, toAlbumId: String) {
        viewModelScope.launch { photoRepository.movePhotos(listOf(photoId), toAlbumId) }
    }
}
