package com.calcplus.calculator.lock

import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.core.lock.BannerText
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.core.lock.SetupPhase
import com.calcplus.calculator.feature.calculator.CalcKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake with call counting: proves sub-minimum/overflowed commits skip the KDF. */
private class FakePasscodeRepository(var stored: List<CalcKey>? = null) : PasscodeRepository {
    var matchesCallCount = 0

    override suspend fun set(sequence: List<CalcKey>) {
        stored = sequence
    }

    override suspend fun matches(sequence: List<CalcKey>): Boolean {
        matchesCallCount += 1
        return stored == sequence
    }
}

class AppLockManagerTest {
    private val code = listOf(CalcKey.D1, CalcKey.D2, CalcKey.ADD, CalcKey.D3, CalcKey.D4)

    private class Clock(var now: Long = 1_000L)

    private fun makeSetup(): Pair<AppLockManager, FakePasscodeRepository> {
        val repo = FakePasscodeRepository()
        return AppLockManager(repo, hasPasscode = false, elapsedRealtime = { 0L }) to repo
    }

    private fun makeLocked(clock: Clock = Clock()): Pair<AppLockManager, FakePasscodeRepository> {
        val repo = FakePasscodeRepository(stored = code)
        return AppLockManager(repo, hasPasscode = true, elapsedRealtime = { clock.now }) to repo
    }

    // First-run setup.

    @Test
    fun freshInstallStartsInSetup() {
        val (manager, _) = makeSetup()
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(BannerText.SETUP_ENTRY, manager.banner.value?.primary)
        assertEquals(BannerText.SETUP_HINT, manager.banner.value?.secondary)
    }

    @Test
    fun existingPasscodeBootsLockedWithNoBanner() {
        val (manager, _) = makeLocked()
        assertEquals(LockState.Locked, manager.lockState.value)
        assertNull(manager.banner.value)
    }

    @Test
    fun setupTooShortStaysInEntry() = runTest {
        val (manager, repo) = makeSetup()
        manager.commit(listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3), overflowed = false)
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(BannerText.SETUP_TOO_SHORT, manager.banner.value?.primary)
        assertNull(repo.stored)
    }

    @Test
    fun setupOverflowStaysInEntry() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(List(32) { CalcKey.D7 }, overflowed = true)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(BannerText.SETUP_TOO_LONG, manager.banner.value?.primary)
    }

    @Test
    fun setupValidGoesToConfirm() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(code, overflowed = false)
        assertEquals(SetupPhase.Confirm(code), manager.setupPhase.value)
        assertEquals(BannerText.SETUP_CONFIRM, manager.banner.value?.primary)
        assertNull(manager.banner.value?.secondary)
    }

    @Test
    fun trivialSequenceShowsSoftWarning() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(List(4) { CalcKey.D7 }, overflowed = false)
        assertEquals(BannerText.SETUP_TRIVIAL_WARNING, manager.banner.value?.secondary)
    }

    @Test
    fun confirmMismatchReturnsToEntry() = runTest {
        val (manager, repo) = makeSetup()
        manager.commit(code, overflowed = false)
        manager.commit(code.dropLast(1) + CalcKey.D9, overflowed = false)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(BannerText.SETUP_MISMATCH, manager.banner.value?.primary)
        assertNull(repo.stored)
    }

    @Test
    fun confirmMatchStoresAndUnlocks() = runTest {
        val (manager, repo) = makeSetup()
        manager.commit(code, overflowed = false)
        manager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, manager.lockState.value)
        assertTrue(manager.showNoRecoveryNotice.value)
        assertEquals(code, repo.stored)
        assertNull(manager.banner.value)
    }

    @Test
    fun backgroundingMidSetupDiscardsBuffers() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(code, overflowed = false)
        val epochBefore = manager.calculatorEpoch.value
        manager.onAppStop()
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(BannerText.SETUP_ENTRY, manager.banner.value?.primary)
        assertTrue(manager.calculatorEpoch.value > epochBefore)
    }

    // Locked.

    @Test
    fun correctSequenceUnlocks() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, manager.lockState.value)
    }

    @Test
    fun wrongSequenceStaysLockedSilently() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(List(4) { CalcKey.D9 }, overflowed = false)
        assertEquals(LockState.Locked, manager.lockState.value)
        assertNull(manager.banner.value)
    }

    @Test
    fun subMinimumCommitSkipsCompare() = runTest {
        val (manager, repo) = makeLocked()
        manager.commit(listOf(CalcKey.D1, CalcKey.D2), overflowed = false)
        assertEquals(LockState.Locked, manager.lockState.value)
        assertEquals(0, repo.matchesCallCount) // no store read, no KDF
    }

    @Test
    fun overflowedCommitNeverMatches() = runTest {
        val (manager, repo) = makeLocked()
        manager.commit(code, overflowed = true) // even the correct sequence
        assertEquals(LockState.Locked, manager.lockState.value)
        assertEquals(0, repo.matchesCallCount)
    }

    // Re-lock model.

    @Test
    fun backgroundingWhileUnlockedLocksImmediately() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        manager.onAppStop()
        assertEquals(LockState.Locked, manager.lockState.value)
    }

    @Test
    fun manualLockClearsCalculator() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        val epochBefore = manager.calculatorEpoch.value
        manager.lock()
        assertEquals(LockState.Locked, manager.lockState.value)
        assertTrue(manager.calculatorEpoch.value > epochBefore)
    }

    @Test
    fun onStopWhileLockedClearsCalculator() {
        val (manager, _) = makeLocked()
        val epochBefore = manager.calculatorEpoch.value
        manager.onAppStop()
        assertTrue(manager.calculatorEpoch.value > epochBefore)
        assertEquals(LockState.Locked, manager.lockState.value)
    }

    @Test
    fun pickerSuppressionWithinCapStaysUnlocked() = runTest {
        val clock = Clock()
        val (manager, _) = makeLocked(clock)
        manager.commit(code, overflowed = false)
        manager.beginExternalActivity()
        manager.onAppStop()
        assertEquals(LockState.Unlocked, manager.lockState.value)
        clock.now += 60_000
        manager.onAppStart()
        assertEquals(LockState.Unlocked, manager.lockState.value)
    }

    @Test
    fun pickerSuppressionBeyondCapLocks() = runTest {
        val clock = Clock()
        val (manager, _) = makeLocked(clock)
        manager.commit(code, overflowed = false)
        manager.beginExternalActivity()
        manager.onAppStop()
        clock.now += AppLockManager.SUPPRESSION_CAP_MS + 1
        manager.onAppStart()
        assertEquals(LockState.Locked, manager.lockState.value)
    }

    @Test
    fun monotonicInconsistencyFailsClosed() = runTest {
        val clock = Clock()
        val (manager, _) = makeLocked(clock)
        manager.commit(code, overflowed = false)
        manager.beginExternalActivity()
        manager.onAppStop()
        clock.now -= 100 // clock went backwards → lock
        manager.onAppStart()
        assertEquals(LockState.Locked, manager.lockState.value)
    }

    @Test
    fun clearedSuppressionLocksOnNextStop() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        manager.beginExternalActivity()
        manager.endExternalActivity()
        manager.onAppStop()
        assertEquals(LockState.Locked, manager.lockState.value)
        assertFalse(manager.systemUiInFlight)
    }

    // Onboarding gate + erase-everything reset.

    @Test
    fun freshInstallShowsOnboardingUntilCompleted() {
        val repo = FakePasscodeRepository()
        val manager = AppLockManager(repo, hasPasscode = false, elapsedRealtime = { 0L }, onboardingComplete = false)
        assertTrue(manager.showOnboarding.value)
        manager.completeOnboarding()
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun onboardingNeverShowsOncePasscodeExists() {
        // Even with the flag unset (upgrade path), an existing vault means no explainer.
        val repo = FakePasscodeRepository(stored = code)
        val manager = AppLockManager(repo, hasPasscode = true, elapsedRealtime = { 0L }, onboardingComplete = false)
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun completedOnboardingStaysHiddenDuringSetup() {
        val (manager, _) = makeSetup() // onboardingComplete defaults to true
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun resetReturnsToFirstRunState() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        val epochBefore = manager.calculatorEpoch.value
        manager.reset()
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(BannerText.SETUP_ENTRY, manager.banner.value?.primary)
        assertTrue(manager.showOnboarding.value)
        assertFalse(manager.showNoRecoveryNotice.value)
        assertTrue(manager.calculatorEpoch.value > epochBefore)
        assertFalse(manager.systemUiInFlight)
    }

    @Test
    fun resetClearsPickerSuppression() = runTest {
        val clock = Clock()
        val (manager, _) = makeLocked(clock)
        manager.commit(code, overflowed = false)
        manager.beginExternalActivity()
        manager.onAppStop() // suppressed stop pending
        manager.reset()
        // A vault set up after the reset must not inherit the old suppression
        // window: the next backgrounding locks immediately.
        manager.commit(code, overflowed = false) // setup entry
        manager.commit(code, overflowed = false) // confirm → unlocked
        manager.onAppStop()
        assertEquals(LockState.Locked, manager.lockState.value)
    }
}
