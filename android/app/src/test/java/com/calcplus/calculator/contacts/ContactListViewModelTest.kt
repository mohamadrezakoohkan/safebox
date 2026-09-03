package com.calcplus.calculator.contacts

import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.feature.contacts.ContactListViewModel
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

/** Hand-written fake (no mocking library); one entry per `delete` CALL. */
class FakeContactRepository(seed: List<Contact> = emptyList()) : ContactRepository {
    val contacts = MutableStateFlow(seed)
    val deleteCalls = mutableListOf<List<String>>()
    val restoreCalls = mutableListOf<List<String>>()

    override fun observeContacts(query: String): Flow<List<Contact>> = contacts.map { list ->
        list.filter { query.isEmpty() || it.displayName.contains(query, ignoreCase = true) }
    }

    override fun observeContact(id: String): Flow<Contact?> =
        contacts.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(contact: Contact) = Unit
    override suspend fun delete(id: String) = delete(listOf(id))

    override suspend fun delete(ids: List<String>) {
        deleteCalls += ids
        contacts.value = contacts.value.filterNot { it.id in ids }
    }

    override suspend fun restore(ids: List<String>) {
        restoreCalls += ids
    }

    override suspend fun purge(ids: List<String>) = Unit
    override suspend fun purgeExpired(now: Long) = Unit
}

fun contact(id: String, first: String? = id, last: String? = null) = Contact(
    id = id,
    firstName = first,
    lastName = last,
    organization = null,
    phones = emptyList(),
    emails = emptyList(),
    address = null,
    notes = null,
    createdAt = 0,
    updatedAt = 0,
)

/** Multi-select on the contacts list (decisions §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aFreshViewModelIsOutsideSelectionMode() = runTest(dispatcher) {
        val viewModel = ContactListViewModel(FakeContactRepository())
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun longPressEntersSelectionModeWithThatRowSelected() = runTest(dispatcher) {
        val viewModel = ContactListViewModel(FakeContactRepository())
        viewModel.startSelecting("a")
        assertTrue(viewModel.isSelecting.value)
        assertEquals(setOf("a"), viewModel.selection.value)
    }

    @Test
    fun togglingAddsRemovesAndClearsOnExit() = runTest(dispatcher) {
        val viewModel = ContactListViewModel(FakeContactRepository())
        viewModel.startSelecting("a")
        viewModel.toggleSelection("b")
        assertEquals(setOf("a", "b"), viewModel.selection.value)

        viewModel.toggleSelection("a")
        assertEquals(setOf("b"), viewModel.selection.value)

        viewModel.exitSelecting()
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun togglingOutsideSelectionModeIsANoOp() = runTest(dispatcher) {
        val viewModel = ContactListViewModel(FakeContactRepository())
        viewModel.toggleSelection("a")
        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun bulkDeleteCallsTheRepositoryOnceWithEveryId() = runTest(dispatcher) {
        val repository = FakeContactRepository(listOf(contact("a"), contact("b"), contact("c")))
        val viewModel = ContactListViewModel(repository)
        viewModel.startSelecting("a")
        viewModel.toggleSelection("c")

        val deleted = viewModel.deleteSelected()
        runCurrent()

        assertEquals(1, repository.deleteCalls.size)
        assertEquals(setOf("a", "c"), repository.deleteCalls.single().toSet())
        assertEquals(setOf("a", "c"), deleted.toSet())
    }

    @Test
    fun bulkDeleteExitsSelectionModeAndClearsTheSelection() = runTest(dispatcher) {
        val repository = FakeContactRepository(listOf(contact("a")))
        val viewModel = ContactListViewModel(repository)
        viewModel.startSelecting("a")
        viewModel.deleteSelected()
        runCurrent()

        assertFalse(viewModel.isSelecting.value)
        assertTrue(viewModel.selection.value.isEmpty())
    }

    @Test
    fun deletingAnEmptySelectionTouchesTheRepositoryNotAtAll() = runTest(dispatcher) {
        val repository = FakeContactRepository(listOf(contact("a")))
        val viewModel = ContactListViewModel(repository)
        viewModel.startSelecting()

        assertTrue(viewModel.deleteSelected().isEmpty())
        runCurrent()
        assertTrue(repository.deleteCalls.isEmpty())
        assertFalse(viewModel.isSelecting.value)
    }

    @Test
    fun sectionsAreUnaffectedBySelectionMode() = runTest(dispatcher) {
        // Section headers are derived from the contacts alone: entering
        // selection mode must not reshuffle or drop a single one.
        val repository = FakeContactRepository(
            listOf(contact("1", first = "Ada"), contact("2", first = "Bob"))
        )
        val viewModel = ContactListViewModel(repository)
        val collector = backgroundScope.launch { viewModel.sections.collect { } }
        advanceTimeBy(400)
        runCurrent()
        val before = viewModel.sections.value?.map { it.first }

        viewModel.startSelecting("1")
        runCurrent()

        assertEquals(listOf("A", "B"), before)
        assertEquals(before, viewModel.sections.value?.map { it.first })
        collector.cancel()
    }
}
