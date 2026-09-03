package com.calcplus.calculator.trash

import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.TrashPolicy
import com.calcplus.calculator.core.domain.repository.TrashContents
import com.calcplus.calculator.core.domain.repository.TrashItemId
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.domain.repository.TrashRepository
import com.calcplus.calculator.feature.trash.TrashViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeTrashRepository : TrashRepository {
    val state = MutableStateFlow(TrashContents())
    val restored = mutableListOf<TrashItemId>()
    val purged = mutableListOf<TrashItemId>()
    var emptyAllCalls = 0

    override fun observeTrash(): Flow<TrashContents> = state
    override suspend fun restore(items: List<TrashItemId>) { restored.addAll(items) }
    override suspend fun purge(items: List<TrashItemId>) { purged.addAll(items) }
    override suspend fun emptyAll() { emptyAllCalls += 1 }
    override suspend fun purgeExpired(now: Long) = Unit
}

class TrashViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun contentsStartNullSoTheScreenNeverFlashesAFalseEmptyState() = runTest(dispatcher) {
        val repository = FakeTrashRepository()
        val viewModel = TrashViewModel(repository, TestScope(dispatcher))
        assertNull(viewModel.contents.value)
    }

    @Test
    fun restoreAndPurgeCarryTheTypedIdAndRunOnTheWorkScope() = runTest(dispatcher) {
        val repository = FakeTrashRepository()
        // A scope that is NOT the viewModelScope: emptying the trash must
        // survive the screen popping.
        val workScope = TestScope(dispatcher)
        val viewModel = TrashViewModel(repository, workScope)

        viewModel.restore(TrashItemId(TrashItemKind.NOTE, "n1"))
        viewModel.purge(TrashItemId(TrashItemKind.ALBUM, "a1"))
        viewModel.emptyAll()
        runCurrent()

        assertEquals(listOf(TrashItemId(TrashItemKind.NOTE, "n1")), repository.restored)
        assertEquals(listOf(TrashItemId(TrashItemKind.ALBUM, "a1")), repository.purged)
        assertEquals(1, repository.emptyAllCalls)
    }

    @Test
    fun daysLeftCountsDownFromTheRetentionPeriodAndClampsAtZero() {
        val repository = FakeTrashRepository()
        val day = 86_400_000L
        var now = 0L
        val viewModel = TrashViewModel(repository, TestScope(dispatcher)) { now }

        assertEquals(30, viewModel.daysLeft(0L))
        now = day
        assertEquals(29, viewModel.daysLeft(0L))
        now = day / 2
        assertEquals(30, viewModel.daysLeft(0L)) // rounds up: still "30 days left"
        now = 30 * day
        assertEquals(0, viewModel.daysLeft(0L))
        now = 90 * day
        assertEquals(0, viewModel.daysLeft(0L)) // never negative
        assertEquals(TrashPolicy.RETENTION_DAYS, viewModel.daysLeft(null))
    }

    @Test
    fun contentsMirrorTheRepositoryFlow() = runTest(dispatcher) {
        val repository = FakeTrashRepository()
        val viewModel = TrashViewModel(repository, TestScope(dispatcher))
        val collector = backgroundScope.launch { viewModel.contents.collect { } }
        runCurrent()

        repository.state.value = TrashContents(
            notes = listOf(
                Note(
                    id = "n1",
                    body = "",
                    title = "Milk",
                    snippet = "",
                    createdAt = 0,
                    updatedAt = 0,
                    tags = emptyList(),
                    deletedAt = 5,
                )
            )
        )
        runCurrent()

        assertEquals(listOf("n1"), viewModel.contents.value?.notes?.map { it.id })
        collector.cancel()
    }
}
