package com.calcplus.calculator.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.model.SearchCandidate
import com.calcplus.calculator.core.domain.model.SearchCorpus
import com.calcplus.calculator.core.domain.model.SearchMatching
import com.calcplus.calculator.core.domain.model.SearchNormalizer
import com.calcplus.calculator.core.domain.model.SearchResults
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.core.domain.repository.NoteRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Global search across notes, contacts and albums (decisions §7). The iOS twin
 * is `GlobalSearchViewModel`.
 *
 * The corpus is the three live lists combined — the repositories already filter
 * `deletedAt IS NULL` (P3), so **a trashed album, note or contact can never
 * surface**, and photos have no candidate at all. Matching itself is the pure
 * `SearchMatching` step over `SearchNormalizer`, i.e. exactly the fold the Notes
 * and Contacts tabs now filter with.
 */
@OptIn(FlowPreview::class)
class GlobalSearchViewModel(
    albumRepository: AlbumRepository,
    noteRepository: NoteRepository,
    contactRepository: ContactRepository,
    debounceMillis: Long = SEARCH_DEBOUNCE_MS,
) : ViewModel() {
    companion object {
        /** Shared constant (decisions §11): 300 ms on both platforms. */
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * True once the field holds something matchable. Deliberately **not**
     * debounced: it picks between the no-query state and the results/no-results
     * state, and making the screen wait 300 ms to blank a list the user just
     * erased reads as lag.
     */
    val hasQuery: StateFlow<Boolean> = _query
        .map { !SearchNormalizer.isBlank(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val candidates: Flow<List<SearchCandidate>> = combine(
        albumRepository.observeAlbums(),
        // Empty query / no tag filter = the whole live list; the filtering this
        // screen does is its own.
        noteRepository.observeNotes(query = "", tagId = null),
        contactRepository.observeContacts(query = ""),
    ) { albums, notes, contacts -> SearchCorpus.build(albums, notes, contacts) }

    /** Hits grouped Albums → Notes → Contacts. Empty until the query matches something. */
    val results: StateFlow<SearchResults> =
        combine(_query.debounce(debounceMillis), candidates) { raw, corpus ->
            SearchMatching.results(corpus, raw)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    /** The only way the query changes, so the debounce cannot be bypassed. */
    fun setQuery(value: String) {
        _query.value = value
    }
}
