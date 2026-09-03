package com.calcplus.calculator.sort

import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.VaultSorting
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.feature.gallery.AlbumListViewModel
import com.calcplus.calculator.feature.notes.NoteListViewModel
import com.calcplus.calculator.notes.FakeNoteRepository
import com.calcplus.calculator.notes.note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeAlbumRepository(seed: List<Album> = emptyList()) : AlbumRepository {
    val albums = MutableStateFlow(seed)
    val sortsRequested = mutableListOf<AlbumSort>()

    override fun observeAlbums(sort: AlbumSort): Flow<List<Album>> {
        sortsRequested += sort
        return albums.map { VaultSorting.sortAlbums(it, sort) }
    }

    override suspend fun createAlbum(name: String) = Unit
    override suspend fun renameAlbum(id: String, name: String) = Unit
    override suspend fun deleteAlbum(id: String) = Unit
    override suspend fun restore(ids: List<String>) = Unit
    override suspend fun purge(ids: List<String>) = Unit
    override suspend fun purgeExpired(now: Long) = Unit
}

private fun album(id: String, name: String, sortIndex: Int, photoCount: Int = 0) = Album(
    id = id,
    name = name,
    createdAt = 0,
    sortIndex = sortIndex,
    photoCount = photoCount,
    coverThumbFileName = null,
)

/**
 * The view models hold the mode and hand it to the repository (decisions §4) —
 * they never sort a list themselves, and nothing sorts in a composable body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SortViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theAlbumListStartsOnTheStoredModeAndReordersWhenItChanges() = runTest(dispatcher) {
        val repository = FakeAlbumRepository(
            listOf(
                album("z", name = "Alpha", sortIndex = 2),
                album("a", name = "Zulu", sortIndex = 0),
            )
        )
        val prefs = FakeSortPreferences(album = AlbumSort.MANUAL)
        val viewModel = AlbumListViewModel(repository, prefs)
        // Both flows are WhileSubscribed, exactly as the screen collects them.
        val collector = backgroundScope.launch { viewModel.albums.collect { } }
        val sortCollector = backgroundScope.launch { viewModel.sort.collect { } }
        runCurrent()

        assertEquals(listOf("a", "z"), viewModel.albums.value?.map { it.id })
        assertEquals(AlbumSort.MANUAL, viewModel.sort.value)

        viewModel.setSort(AlbumSort.NAME)
        runCurrent()

        assertEquals(AlbumSort.NAME, viewModel.sort.value)
        assertEquals(AlbumSort.NAME, prefs.albumState.value) // persisted, not just local
        assertEquals(listOf("z", "a"), viewModel.albums.value?.map { it.id })
        // The mode reached the repository; the view model never re-sorted.
        assertEquals(listOf(AlbumSort.MANUAL, AlbumSort.NAME), repository.sortsRequested)
        collector.cancel()
        sortCollector.cancel()
    }

    @Test
    fun theNoteListStartsOnTheStoredModeAndReordersWhenItChanges() = runTest(dispatcher) {
        val repository = FakeNoteRepository(
            listOf(
                note("old", title = "Apple", updatedAt = 1),
                note("new", title = "Zulu", updatedAt = 9),
            )
        )
        val prefs = FakeSortPreferences(note = NoteSort.DATE_MODIFIED)
        val viewModel = NoteListViewModel(repository, prefs)
        val collector = backgroundScope.launch { viewModel.notes.collect { } }
        val sortCollector = backgroundScope.launch { viewModel.sort.collect { } }
        advanceTimeBy(400) // past the 300 ms query debounce
        runCurrent()

        assertEquals(listOf("new", "old"), viewModel.notes.value?.map { it.id })
        assertEquals(NoteSort.DATE_MODIFIED, viewModel.sort.value)

        viewModel.setSort(NoteSort.TITLE)
        advanceTimeBy(400)
        runCurrent()

        assertEquals(NoteSort.TITLE, viewModel.sort.value)
        assertEquals(NoteSort.TITLE, prefs.noteState.value)
        assertEquals(listOf("old", "new"), viewModel.notes.value?.map { it.id })
        assertEquals(
            listOf(NoteSort.DATE_MODIFIED, NoteSort.TITLE),
            repository.sortsRequested.distinct(),
        )
        collector.cancel()
        sortCollector.cancel()
    }

    @Test
    fun anEmptyStoreLandsOnTheDefaultsWithoutBlocking() = runTest(dispatcher) {
        val albumViewModel = AlbumListViewModel(FakeAlbumRepository(), FakeSortPreferences())
        val noteViewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        assertEquals(AlbumSort.DEFAULT, albumViewModel.sort.value)
        assertEquals(NoteSort.DEFAULT, noteViewModel.sort.value)
    }
}
