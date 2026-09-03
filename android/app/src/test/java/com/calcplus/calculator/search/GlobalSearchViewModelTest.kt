package com.calcplus.calculator.search

import com.calcplus.calculator.core.database.entity.LabeledValue
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Tag
import com.calcplus.calculator.core.domain.model.VaultSorting
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.contacts.FakeContactRepository
import com.calcplus.calculator.feature.search.GlobalSearchViewModel
import com.calcplus.calculator.notes.FakeNoteRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Hand-written fake. Like the real repository it only ever emits LIVE albums —
 * a trashed row is filtered by the DAO and never reaches the domain list.
 */
private class FakeSearchAlbumRepository(seed: List<Album> = emptyList()) : AlbumRepository {
    val albums = MutableStateFlow(seed)

    override fun observeAlbums(sort: AlbumSort): Flow<List<Album>> =
        albums.map { list -> VaultSorting.sortAlbums(list.filter { it.deletedAt == null }, sort) }

    override suspend fun createAlbum(name: String) = Unit
    override suspend fun renameAlbum(id: String, name: String) = Unit
    override suspend fun deleteAlbum(id: String) {
        albums.value = albums.value.map { if (it.id == id) it.copy(deletedAt = 1L) else it }
    }
    override suspend fun restore(ids: List<String>) = Unit
    override suspend fun purge(ids: List<String>) = Unit
    override suspend fun purgeExpired(now: Long) = Unit
}

/** Global search (decisions §7): scope, grouping, the 300 ms debounce, trash. */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        albums: FakeSearchAlbumRepository = FakeSearchAlbumRepository(
            listOf(searchAlbum("al-1", "Rëceipts", photoCount = 2))
        ),
        notes: FakeNoteRepository = FakeNoteRepository(
            listOf(
                searchNote(
                    "n-1",
                    title = "Quarterly report",
                    body = "receipts filed",
                    tags = listOf(Tag("t-1", "Finance", 0)),
                )
            )
        ),
        contacts: FakeContactRepository = FakeContactRepository(
            listOf(
                searchContact(
                    "c-1",
                    first = "Ada",
                    last = "Lovelace",
                    emails = listOf(LabeledValue("home", "ada@receipts.example")),
                )
            )
        ),
    ) = GlobalSearchViewModel(albums, notes, contacts)

    @Test
    fun theDebounceIsTheSharedThreeHundredMilliseconds() {
        assertEquals(300L, GlobalSearchViewModel.SEARCH_DEBOUNCE_MS)
    }

    @Test
    fun anEmptyQueryShowsTheNoQueryStateAndNeverAllResults() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }
        val queryCollector = backgroundScope.launch { model.hasQuery.collect { } }
        advanceTimeBy(400)
        runCurrent()

        assertFalse(model.hasQuery.value)
        assertTrue(model.results.value.isEmpty)

        // Whitespace is still "no query".
        model.setQuery("   ")
        advanceTimeBy(400)
        runCurrent()
        assertFalse(model.hasQuery.value)
        assertTrue(model.results.value.isEmpty)

        collector.cancel()
        queryCollector.cancel()
    }

    @Test
    fun hasQueryFlipsImmediatelyWhileResultsWaitForTheDebounce() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }
        val queryCollector = backgroundScope.launch { model.hasQuery.collect { } }
        advanceTimeBy(400)
        runCurrent()

        model.setQuery("receipts")
        advanceTimeBy(100)
        runCurrent()
        // The state selector is not debounced (so the no-query screen leaves at
        // once) but the match itself has not run yet.
        assertTrue(model.hasQuery.value)
        assertTrue(model.results.value.isEmpty)

        advanceTimeBy(300)
        runCurrent()
        assertFalse(model.results.value.isEmpty)

        collector.cancel()
        queryCollector.cancel()
    }

    @Test
    fun oneQueryMatchesAcrossAllThreeTypesGroupedInSectionOrder() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }
        model.setQuery("receipts")
        advanceTimeBy(400)
        runCurrent()

        val results = model.results.value
        assertEquals(listOf("al-1"), results.albums.map { it.id }) // album name
        assertEquals(listOf("n-1"), results.notes.map { it.id }) // note body
        assertEquals(listOf("c-1"), results.contacts.map { it.id }) // contact email
        assertEquals(listOf("al-1", "n-1", "c-1"), results.all.map { it.id })
        collector.cancel()
    }

    @Test
    fun matchingIsDiacriticInsensitiveAndReachesTagNames() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }

        model.setQuery("RECEIPTS") // "Rëceipts", case and diacritics folded
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("al-1"), model.results.value.albums.map { it.id })

        model.setQuery("finance") // a TAG name, not the note's text
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("n-1"), model.results.value.notes.map { it.id })
        assertTrue(model.results.value.albums.isEmpty())

        collector.cancel()
    }

    @Test
    fun trashedRowsNeverAppearInResults() = runTest(dispatcher) {
        val albums = FakeSearchAlbumRepository(listOf(searchAlbum("al-1", "Receipts")))
        val notes = FakeNoteRepository(listOf(searchNote("n-1", title = "Receipts")))
        val contacts = FakeContactRepository(listOf(searchContact("c-1", first = "Receipts")))
        val model = GlobalSearchViewModel(albums, notes, contacts)
        val collector = backgroundScope.launch { model.results.collect { } }
        model.setQuery("receipts")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(3, model.results.value.count)

        // Soft-delete all three: the repositories stop emitting them (P3), so
        // search cannot surface a row that sits in "Recently deleted".
        albums.deleteAlbum("al-1")
        notes.delete("n-1")
        contacts.delete("c-1")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(model.results.value.isEmpty)
        collector.cancel()
    }

    @Test
    fun clearingTheFieldReturnsToTheNoQueryState() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }
        val queryCollector = backgroundScope.launch { model.hasQuery.collect { } }
        model.setQuery("receipts")
        advanceTimeBy(400)
        runCurrent()
        assertFalse(model.results.value.isEmpty)

        model.setQuery("")
        runCurrent()
        assertFalse(model.hasQuery.value) // immediately, without waiting 300 ms
        advanceTimeBy(400)
        runCurrent()
        assertTrue(model.results.value.isEmpty)

        collector.cancel()
        queryCollector.cancel()
    }

    @Test
    fun aQueryThatMatchesNothingLeavesTheResultsEmptyWithAQueryPresent() = runTest(dispatcher) {
        val model = viewModel()
        val collector = backgroundScope.launch { model.results.collect { } }
        val queryCollector = backgroundScope.launch { model.hasQuery.collect { } }
        model.setQuery("xyzzy")
        advanceTimeBy(400)
        runCurrent()

        // hasQuery + empty results is exactly the "No results" state.
        assertTrue(model.hasQuery.value)
        assertTrue(model.results.value.isEmpty)
        collector.cancel()
        queryCollector.cancel()
    }
}
