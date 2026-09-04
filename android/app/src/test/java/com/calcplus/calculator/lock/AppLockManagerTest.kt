package com.calcplus.calculator.lock

import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.core.lock.SetupPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {
    private val code = listOf("D1", "D2", "ADD", "D3", "D4")

    private class Clock(var now: Long = 1_000L)

    private fun makeSetup(): Pair<AppLockManager, FakePasscodeRepository> {
        val repo = FakePasscodeRepository()
        return AppLockManager(repo, testRegistry(), hasPasscode = false, elapsedRealtime = { 0L }) to repo
    }

    private fun makeLocked(
        clock: Clock = Clock(),
        faceId: String = "calculator",
    ): Pair<AppLockManager, FakePasscodeRepository> {
        val repo = FakePasscodeRepository(stored = code, storedFaceId = faceId)
        return AppLockManager(
            repo,
            testRegistry(),
            hasPasscode = true,
            elapsedRealtime = { clock.now },
            initialActiveDisguiseId = faceId,
        ) to repo
    }

    // First-run setup.

    @Test
    fun freshInstallStartsInSetup() {
        val (manager, _) = makeSetup()
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(CaptionKind.PROMPT_NEW_SETUP, manager.caption.value?.primary)
        assertEquals(CaptionKind.STRENGTH_HINT, manager.caption.value?.secondary)
    }

    @Test
    fun existingPasscodeBootsLockedWithNoCaption() {
        val (manager, _) = makeLocked()
        assertEquals(LockState.Locked, manager.lockState.value)
        assertNull(manager.caption.value)
    }

    @Test
    fun setupTooShortStaysInEntry() = runTest {
        val (manager, repo) = makeSetup()
        manager.commit(listOf("D1", "D2", "D3"), overflowed = false)
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(CaptionKind.TOO_SHORT, manager.caption.value?.primary)
        assertNull(repo.stored)
    }

    @Test
    fun setupOverflowStaysInEntry() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(List(32) { "D7" }, overflowed = true)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(CaptionKind.TOO_LONG, manager.caption.value?.primary)
    }

    @Test
    fun setupValidGoesToConfirm() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(code, overflowed = false)
        assertEquals(SetupPhase.Confirm(code), manager.setupPhase.value)
        assertEquals(CaptionKind.PROMPT_CONFIRM_SETUP, manager.caption.value?.primary)
        assertNull(manager.caption.value?.secondary)
    }

    @Test
    fun trivialSequenceShowsSoftWarning() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(List(4) { "D7" }, overflowed = false)
        assertEquals(CaptionKind.TRIVIAL_WARNING, manager.caption.value?.secondary)
    }

    @Test
    fun confirmMismatchReturnsToEntry() = runTest {
        val (manager, repo) = makeSetup()
        manager.commit(code, overflowed = false)
        manager.commit(code.dropLast(1) + "D9", overflowed = false)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(CaptionKind.MISMATCH, manager.caption.value?.primary)
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
        assertNull(manager.caption.value)
    }

    @Test
    fun backgroundingMidSetupDiscardsBuffers() = runTest {
        val (manager, _) = makeSetup()
        manager.commit(code, overflowed = false)
        val epochBefore = manager.surfaceEpoch.value
        manager.onAppStop()
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(CaptionKind.PROMPT_NEW_SETUP, manager.caption.value?.primary)
        assertTrue(manager.surfaceEpoch.value > epochBefore)
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
        manager.commit(List(4) { "D9" }, overflowed = false)
        assertEquals(LockState.Locked, manager.lockState.value)
        assertNull(manager.caption.value)
    }

    @Test
    fun subMinimumCommitSkipsCompare() = runTest {
        val (manager, repo) = makeLocked()
        manager.commit(listOf("D1", "D2"), overflowed = false)
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

    // The §1.1 failed-attempt pulse matrix, in the two modes this manager owns.

    @Test
    fun covertFaceNeverPulsesInDisguiseMode() = runTest {
        val (manager, _) = makeLocked(faceId = "calculator")
        manager.commit(List(4) { "D9" }, overflowed = false) // wrong
        manager.commit(listOf("D1"), overflowed = false) // too short
        manager.commit(code, overflowed = true) // overflowed
        assertEquals(0, manager.failedAttemptToken.value)
    }

    @Test
    fun overtFacePulsesOnEveryNonAcceptedCommit() = runTest {
        val (manager, repo) = makeLocked(faceId = "numpad")
        manager.commit(List(4) { "D9" }, overflowed = false) // wrong: KDF runs
        assertEquals(1, manager.failedAttemptToken.value)
        assertEquals(1, repo.matchesCallCount)

        manager.commit(listOf("D1"), overflowed = false) // too short: KDF skipped
        assertEquals(2, manager.failedAttemptToken.value)
        assertEquals(1, repo.matchesCallCount)

        manager.commit(code, overflowed = true) // overflowed: KDF skipped
        assertEquals(3, manager.failedAttemptToken.value)
        assertEquals(1, repo.matchesCallCount)
    }

    @Test
    fun overtFaceDoesNotPulseOnASuccessfulUnlock() = runTest {
        val (manager, _) = makeLocked(faceId = "numpad")
        manager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, manager.lockState.value)
        assertEquals(0, manager.failedAttemptToken.value)
    }

    @Test
    fun setupModesNeverPulse() = runTest {
        val (manager, _) = makeSetup()
        manager.selectPendingDisguise("numpad")
        manager.commit(listOf("D1"), overflowed = false) // too short
        manager.commit(List(32) { "D7" }, overflowed = true) // too long
        manager.commit(listOf("D1", "D2", "D3", "D4"), overflowed = false) // → confirm
        manager.commit(listOf("D9", "D9", "D9", "D9"), overflowed = false) // mismatch
        assertEquals(0, manager.failedAttemptToken.value)
    }

    @Test
    fun aFreshSurfaceStartsWithoutAStalePulse() = runTest {
        val (manager, _) = makeLocked(faceId = "pattern")
        manager.commit(List(4) { "N9" }, overflowed = false)
        assertTrue(manager.failedAttemptToken.value > 0)
        manager.onAppStop() // new surface
        assertEquals(0, manager.failedAttemptToken.value)
    }

    // Faces.

    @Test
    fun launchMirrorPicksTheFaceAndAnUnknownIdFailsClosed() {
        val (numpadManager, _) = makeLocked(faceId = "numpad")
        assertEquals("numpad", numpadManager.activeDisguise.value.id)

        val repo = FakePasscodeRepository(stored = code, storedFaceId = "tip-calculator")
        val manager = AppLockManager(
            repo,
            testRegistry(),
            hasPasscode = true,
            elapsedRealtime = { 0L },
            initialActiveDisguiseId = "tip-calculator",
        )
        assertEquals("calculator", manager.activeDisguise.value.id)
    }

    @Test
    fun unlockAdoptsTheEnvelopeFaceOverATamperedMirror() = runTest {
        // Mirror says calculator, envelope says pattern: the envelope wins.
        val repo = FakePasscodeRepository(stored = code, storedFaceId = "pattern")
        val manager = AppLockManager(
            repo,
            testRegistry(),
            hasPasscode = true,
            elapsedRealtime = { 0L },
            initialActiveDisguiseId = "calculator",
        )
        assertEquals("calculator", manager.activeDisguise.value.id)
        manager.commit(code, overflowed = false)
        assertEquals("pattern", manager.activeDisguise.value.id)
    }

    @Test
    fun setupEnrollsTheChosenFace() = runTest {
        val (manager, repo) = makeSetup()
        manager.completeOnboarding("pattern")
        assertEquals("pattern", manager.pendingDisguiseId.value)
        assertFalse(manager.showOnboarding.value)
        val pattern = listOf("N0", "N3", "N6", "N7")
        manager.commit(pattern, overflowed = false)
        manager.commit(pattern, overflowed = false)
        assertEquals("pattern", repo.storedFaceId)
        assertEquals("pattern", repo.storedAlphabet?.tokenSetId)
        assertEquals("pattern", manager.activeDisguise.value.id)
    }

    @Test
    fun changingTheGuideSelectionRecreatesTheSetupSurface() {
        val (manager, _) = makeSetup()
        val epochBefore = manager.surfaceEpoch.value
        manager.selectPendingDisguise("numpad")
        assertTrue(manager.surfaceEpoch.value > epochBefore)
        val epochAfter = manager.surfaceEpoch.value
        manager.selectPendingDisguise("numpad") // no change
        assertEquals(epochAfter, manager.surfaceEpoch.value)
    }

    @Test
    fun setActiveDisguiseRecreatesTheSurface() {
        val (manager, _) = makeLocked()
        val epochBefore = manager.surfaceEpoch.value
        manager.setActiveDisguise("numpad")
        assertEquals("numpad", manager.activeDisguise.value.id)
        assertTrue(manager.surfaceEpoch.value > epochBefore)
    }

    // Cover identities (§9a). Disabling an activity-alias tears down the task
    // rooted at it even with DONT_KILL_APP, so the swap can only happen while
    // the user is already leaving: it is reconciled on background, never at
    // the moment of a commit.

    private fun coverIdentityManager(
        worn: MutableList<String>,
        hasPasscode: Boolean,
        faceId: String? = null,
    ) = AppLockManager(
        FakePasscodeRepository(
            stored = if (hasPasscode) code else null,
            storedFaceId = faceId,
        ),
        testRegistry(),
        hasPasscode = hasPasscode,
        elapsedRealtime = { 0L },
        initialActiveDisguiseId = faceId,
        reconcileCoverIdentity = { worn += it.id },
    )

    /** The regression: setup used to eject the user to the launcher. */
    @Test
    fun firstRunSetupDoesNotTouchTheHomeScreen() = runTest {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = false)
        manager.selectPendingDisguise("pattern")
        val pattern = listOf("N0", "N1", "N2", "N5")

        manager.commit(pattern, overflowed = false)
        manager.commit(pattern, overflowed = false)

        assertEquals(LockState.Unlocked, manager.lockState.value)
        assertEquals("pattern", manager.activeDisguise.value.id)
        assertTrue("committing must never tear down the task", worn.isEmpty())
    }

    @Test
    fun theChosenIdentityIsWornOnTheNextBackground() = runTest {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = false)
        manager.selectPendingDisguise("pattern")
        val pattern = listOf("N0", "N1", "N2", "N5")
        manager.commit(pattern, overflowed = false)
        manager.commit(pattern, overflowed = false)

        manager.onAppStop()

        assertEquals(listOf("pattern"), worn)
    }

    @Test
    fun switchingWearsTheNewIdentityOnTheNextBackgroundOnly() {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = true, faceId = "calculator")

        manager.setActiveDisguise("numpad")
        assertTrue("the switch itself must not tear down the task", worn.isEmpty())

        manager.onAppStop()
        assertEquals(listOf("numpad"), worn)
    }

    /**
     * A face that never got enrolled must never reach the home screen: the
     * reconcile reads the ACTIVE face, so an abandoned switch leaves the icon
     * exactly where the envelope left it.
     */
    @Test
    fun anAbandonedSwitchNeverReachesTheHomeScreen() {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = true, faceId = "calculator")

        // The picker got as far as CAPTURE_NEW on the pattern and was cancelled,
        // so setActiveDisguise was never called. Then the user backgrounds.
        manager.onAppStop()

        assertEquals(listOf("calculator"), worn)
    }

    @Test
    fun eraseRestoresTheCalculatorIdentityOnTheNextBackground() {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = true, faceId = "pattern")

        manager.reset()
        assertEquals("calculator", manager.activeDisguise.value.id)
        // Erase leaves the user inside the app on first-run setup — ejecting
        // them to the launcher there would be the same defect again.
        assertTrue(worn.isEmpty())

        manager.onAppStop()
        assertEquals(listOf("calculator"), worn)
    }

    /**
     * The photo picker is a foreground moment wearing a background's clothes:
     * the user comes straight back into this task with the vault still
     * unlocked, and a teardown would lose the import in progress.
     */
    @Test
    fun theSuppressedPickerRoundTripDoesNotTouchTheHomeScreen() = runTest {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = true, faceId = "calculator")
        manager.commit(code, overflowed = false)
        manager.setActiveDisguise("numpad")

        manager.beginExternalActivity()
        manager.onAppStop()
        assertTrue("a picker round trip must not tear down the task", worn.isEmpty())

        // ...and a real background right after still reconciles.
        manager.onAppStart()
        manager.endExternalActivity()
        manager.onAppStop()
        assertEquals(listOf("numpad"), worn)
    }

    /** Reconciling is idempotent, so a background with nothing to do is free. */
    @Test
    fun repeatedBackgroundsJustRepeatTheSameTarget() {
        val worn = mutableListOf<String>()
        val manager = coverIdentityManager(worn, hasPasscode = true, faceId = "numpad")

        manager.onAppStop()
        manager.onAppStop()

        // The manager asks for the same identity every time; AppIconManager is
        // what turns the second ask into a no-op.
        assertEquals(listOf("numpad", "numpad"), worn)
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
    fun manualLockClearsTheSurface() = runTest {
        val (manager, _) = makeLocked()
        manager.commit(code, overflowed = false)
        val epochBefore = manager.surfaceEpoch.value
        manager.lock()
        assertEquals(LockState.Locked, manager.lockState.value)
        assertTrue(manager.surfaceEpoch.value > epochBefore)
    }

    @Test
    fun onStopWhileLockedClearsTheSurface() {
        val (manager, _) = makeLocked()
        val epochBefore = manager.surfaceEpoch.value
        manager.onAppStop()
        assertTrue(manager.surfaceEpoch.value > epochBefore)
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
        val manager = AppLockManager(
            repo,
            testRegistry(),
            hasPasscode = false,
            elapsedRealtime = { 0L },
            onboardingComplete = false,
        )
        assertTrue(manager.showOnboarding.value)
        manager.completeOnboarding()
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun onboardingNeverShowsOncePasscodeExists() {
        // Even with the flag unset (upgrade path), an existing vault means no explainer.
        val repo = FakePasscodeRepository(stored = code)
        val manager = AppLockManager(
            repo,
            testRegistry(),
            hasPasscode = true,
            elapsedRealtime = { 0L },
            onboardingComplete = false,
        )
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun completedOnboardingStaysHiddenDuringSetup() {
        val (manager, _) = makeSetup() // onboardingComplete defaults to true
        assertFalse(manager.showOnboarding.value)
    }

    @Test
    fun resetReturnsToFirstRunState() = runTest {
        val (manager, _) = makeLocked(faceId = "numpad")
        manager.commit(code, overflowed = false)
        val epochBefore = manager.surfaceEpoch.value
        manager.reset()
        assertEquals(LockState.NeedsSetup, manager.lockState.value)
        assertEquals(SetupPhase.Entry, manager.setupPhase.value)
        assertEquals(CaptionKind.PROMPT_NEW_SETUP, manager.caption.value?.primary)
        assertTrue(manager.showOnboarding.value)
        assertFalse(manager.showNoRecoveryNotice.value)
        assertTrue(manager.surfaceEpoch.value > epochBefore)
        assertFalse(manager.systemUiInFlight)
        // The face goes back to the default too.
        assertEquals("calculator", manager.activeDisguise.value.id)
        assertEquals("calculator", manager.pendingDisguiseId.value)
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
