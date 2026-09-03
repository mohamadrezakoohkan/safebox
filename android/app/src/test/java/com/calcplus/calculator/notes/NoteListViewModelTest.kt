package com.calcplus.calculator.notes

import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.Tag
import com.calcplus.calculator.core.domain.model.VaultSorting
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.feature.notes.NoteListViewModel
import com.calcplus.calculator.sort.FakeSortPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Hand-written fake (no mocking library). Records every `delete` call as ONE
 * entry, which is what makes "bulk delete is one call with N ids, never N
 * calls" assertable.
 */
class FakeNoteRepository(seed: List<Note> = emptyList()) : NoteRepository {
    val notes = MutableStateFlow(seed)
    val deleteCalls = mutableListOf<List<String>>()
    val restoreCalls = mutableListOf<List<String>>()
    /** Every sort mode the view model asked for, in order. */
    val sortsRequested = mutableListOf<NoteSort>()

    override fun observeNotes(query: String, tagId: String?, sort: NoteSort): Flow<List<Note>> {
        sortsRequested += sort
        return notes.map { list ->
            VaultSorting.sortNotes(
                list.filter { note ->
                    (query.isEmpty() || note.title.contains(query, ignoreCase = true)) &&
                        (tagId == null || note.tags.any { it.id == tagId })
                },
                sort,
            )
        }
    }

    override fun observeNote(id: String): Flow<Note?> = notes.map { list -> list.firstOrNull { it.id == id } }
    override fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun createNote(): String = "created"
    override suspend fun saveBody(id: String, body: String) = Unit
    override suspend fun delete(id: String) = delete(listOf(id))

    override suspend fun delete(ids: List<String>) {
        deleteCalls += ids
        notes.value = notes.value.filterNot { it.id in ids }
    }

    override suspend fun restore(ids: List<String>) {
        restoreCalls += ids
    }

    override suspend fun purge(ids: List<String>) = Unit
    override suspend fun purgeExpired(now: Long) = Unit
    override suspend fun getOrCreateTag(name: String): Tag = Tag("t", name, 0)
    override suspend fun setTags(noteId: String, tagIds: List<String>) = Unit
}

fun note(id: String, title: String = id, createdAt: Long = 0, updatedAt: Long = 0) = Note(
    id = id,
    body = title,
    title = title,
    snippet = "",
    createdAt = createdAt,
    updatedAt = updatedAt,
    tags = emptyList(),
)

/** Multi-select on the notes list (decisions §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aFreshViewModelIsOutsideSelectionMode() = runTest(dispatcher) {
        // Pins the post-lock state: VaultScaffold builds a new view model on
        // every unlock, so "reset on lock" is this assertion.
        val viewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun longPressEntersSelectionModeWithThatRowSelected() = runTest(dispatcher) {
        val viewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        viewModel.startSelecting("a")
        assertTrue(viewModel.isSelecting.value)
        assertEquals(setOf("a"), viewModel.selection.value)
    }

    @Test
    fun togglingAddsRemovesAndClearsOnExit() = runTest(dispatcher) {
        val viewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        viewModel.startSelecting("a")
        viewModel.toggleSelection("b")
        viewModel.toggleSelection("c")
        assertEquals(setOf("a", "b", "c"), viewModel.selection.value)

        viewModel.toggleSelection("b")
        assertEquals(setOf("a", "c"), viewModel.selection.value)

        viewModel.exitSelecting()
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun togglingOutsideSelectionModeIsANoOp() = runTest(dispatcher) {
        // A tap while browsing opens the note; it must never quietly build a
        // selection behind the user's back.
        val viewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        viewModel.toggleSelection("a")
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun startSelectingWithoutARowEntersWithAnEmptySelection() = runTest(dispatcher) {
        val viewModel = NoteListViewModel(FakeNoteRepository(), FakeSortPreferences())
        viewModel.startSelecting()
        assertTrue(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun bulkDeleteCallsTheRepositoryOnceWithEveryId() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("a"), note("b"), note("c")))
        val viewModel = NoteListViewModel(repository, FakeSortPreferences())
        viewModel.startSelecting("a")
        viewModel.toggleSelection("b")
        viewModel.toggleSelection("c")

        val deleted = viewModel.deleteSelected()
        runCurrent()

        // ONE call, three ids — never three calls (one shared deletedAt stamp).
        assertEquals(1, repository.deleteCalls.size)
        assertEquals(setOf("a", "b", "c"), repository.deleteCalls.single().toSet())
        assertEquals(setOf("a", "b", "c"), deleted.toSet())
    }

    @Test
    fun bulkDeleteExitsSelectionModeAndClearsTheSelection() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("a")))
        val viewModel = NoteListViewModel(repository, FakeSortPreferences())
        viewModel.startSelecting("a")
        viewModel.deleteSelected()
        runCurrent()

        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun deletingAnEmptySelectionTouchesTheRepositoryNotAtAll() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("a")))
        val viewModel = NoteListViewModel(repository, FakeSortPreferences())
        viewModel.startSelecting()

        val deleted = viewModel.deleteSelected()
        runCurrent()

        assertTrue(deleted.isEmpty())
        assertTrue(repository.deleteCalls.isEmpty())
        // …but the mode still exits, so the bar cannot get stuck.
        assertFalse(viewModel.isSelecting.value)
    }

    @Test
    fun singleDeleteStillGoesThroughTheOneIdBatchApi() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("a")))
        val viewModel = NoteListViewModel(repository, FakeSortPreferences())
        viewModel.delete("a")
        runCurrent()
        assertEquals(listOf(listOf("a")), repository.deleteCalls)
    }
}
