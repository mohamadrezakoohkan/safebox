package com.calcplus.calculator.feature.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ImportProgress
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumListViewModel(
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    /** null = first Room emission pending — render nothing, never a false empty state. */
    val albums: StateFlow<List<Album>?> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun deleteSelected() {
        val ids = _selection.value.toList()
        exitSelecting()
        viewModelScope.launch { photoRepository.deletePhotos(ids) }
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
    val photos: StateFlow<List<Photo>> = photoRepository.observePhotos(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(photoId: String) {
        viewModelScope.launch { photoRepository.deletePhotos(listOf(photoId)) }
    }

    fun move(photoId: String, toAlbumId: String) {
        viewModelScope.launch { photoRepository.movePhotos(listOf(photoId), toAlbumId) }
    }
}
