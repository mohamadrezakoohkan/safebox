package com.calcplus.calculator.settings

import com.calcplus.calculator.core.update.UpdateChecker
import com.calcplus.calculator.core.update.UpdateEndpoints
import com.calcplus.calculator.feature.settings.UpdateCheckViewModel
import com.calcplus.calculator.feature.settings.UpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The "Check for updates" row's state machine (decisions §13), driven entirely
 * through the injected fetch — no network, and no real dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        currentVersion: String = "1.0.0",
        fetch: suspend () -> String,
    ) = UpdateCheckViewModel(
        currentVersion = currentVersion,
        checker = UpdateChecker(fetch = fetch),
        ioDispatcher = dispatcher,
    )

    @Test
    fun theRowStartsWithNoSubtitleAndMakesNoRequest() = runTest(dispatcher) {
        var calls = 0
        val model = viewModel(fetch = { calls += 1; """{"latestVersion":"9.0.0"}""" })

        advanceUntilIdle()

        // Nothing at construction: the app is silent until the row is tapped.
        assertEquals(UpdateState.Idle, model.state.value)
        assertEquals(0, calls)
    }

    @Test
    fun aTapShowsCheckingImmediatelyAndThenTheResult() = runTest(dispatcher) {
        val body = CompletableDeferred<String>()
        val model = viewModel(fetch = { body.await() })

        model.check()
        // Synchronous, before the coroutine even starts: no blank frame.
        assertEquals(UpdateState.Checking, model.state.value)

        runCurrent()
        assertEquals(UpdateState.Checking, model.state.value)

        body.complete("""{"latestVersion":"1.0.0"}""")
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, model.state.value)
    }

    @Test
    fun aNewerVersionBecomesAvailableWithTheFetchedUrl() = runTest(dispatcher) {
        val model = viewModel(
            fetch = { """{"latestVersion":"1.2.0","releasesUrl":"https://example.test/r/1.2.0"}""" },
        )

        model.check()
        advanceUntilIdle()

        assertEquals(UpdateState.Available("1.2.0", "https://example.test/r/1.2.0"), model.state.value)
    }

    @Test
    fun anAvailableUpdateWithoutAUrlFallsBackToTheReleasesPage() = runTest(dispatcher) {
        val model = viewModel(fetch = { """{"latestVersion":"1.2.0"}""" })

        model.check()
        advanceUntilIdle()

        assertEquals(UpdateState.Available("1.2.0", UpdateEndpoints.RELEASES_URL), model.state.value)
    }

    @Test
    fun anOfflineFetchFails() = runTest(dispatcher) {
        val model = viewModel(fetch = { throw IOException("no route to host") })

        model.check()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed, model.state.value)
    }

    @Test
    fun aMalformedManifestFailsRatherThanCrashing() = runTest(dispatcher) {
        val model = viewModel(fetch = { "<html>rate limited</html>" })

        model.check()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed, model.state.value)
    }

    @Test
    fun rowMashingWhileCheckingDoesNotFanOutIntoParallelRequests() = runTest(dispatcher) {
        val body = CompletableDeferred<String>()
        var calls = 0
        val model = viewModel(fetch = { calls += 1; body.await() })

        model.check()
        model.check()
        model.check()
        runCurrent()

        assertEquals(1, calls)
        assertEquals(UpdateState.Checking, model.state.value)

        body.complete("""{"latestVersion":"1.0.0"}""")
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, model.state.value)
    }

    @Test
    fun tappingAgainAfterAFailureRetries() = runTest(dispatcher) {
        var calls = 0
        val model = viewModel(
            fetch = {
                calls += 1
                if (calls == 1) throw IOException("offline") else """{"latestVersion":"3.0.0"}"""
            },
        )

        model.check()
        advanceUntilIdle()
        assertEquals(UpdateState.Failed, model.state.value)

        model.check()
        assertEquals(UpdateState.Checking, model.state.value)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(UpdateState.Available("3.0.0", UpdateEndpoints.RELEASES_URL), model.state.value)
    }

    @Test
    fun tappingAgainAfterUpToDateRunsTheCheckAgain() = runTest(dispatcher) {
        var calls = 0
        val model = viewModel(fetch = { calls += 1; """{"latestVersion":"1.0.0"}""" })

        model.check()
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, model.state.value)

        model.check()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(UpdateState.UpToDate, model.state.value)
    }
}
