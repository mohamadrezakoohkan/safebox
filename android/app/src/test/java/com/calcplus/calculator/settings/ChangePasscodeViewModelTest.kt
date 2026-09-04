package com.calcplus.calculator.settings

import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.settings.ChangePasscodeViewModel
import com.calcplus.calculator.lock.FakePasscodeRepository
import com.calcplus.calculator.lock.testRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePasscodeViewModelTest {
    private val current = listOf("D1", "D2", "D3", "D4")
    private val newCode = listOf("D5", "ADD", "D7", "PCT")

    private fun make(): Pair<ChangePasscodeViewModel, FakePasscodeRepository> {
        val repo = FakePasscodeRepository(stored = current, storedFaceId = "calculator")
        return ChangePasscodeViewModel(repo, testRegistry(), CalculatorDisguise) to repo
    }

    @Test
    fun startsInVerifyCurrent() {
        val (vm, _) = make()
        assertEquals(ChangePasscodeViewModel.Phase.VerifyCurrent, vm.phase.value)
        assertEquals(CaptionKind.PROMPT_CURRENT, vm.caption.value.primary)
        assertEquals(DisguiseMode.VERIFY_CURRENT, vm.modeForPhase(vm.phase.value))
    }

    @Test
    fun wrongCurrentShowsVisibleError() = runTest {
        val (vm, _) = make()
        val pulseBefore = vm.failedAttemptToken.value
        vm.commitInternal(List(4) { "D9" }, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.VerifyCurrent, vm.phase.value) // unlimited retries
        assertEquals(CaptionKind.WRONG_CODE, vm.caption.value.primary)
        assertTrue(vm.caption.value.isError)
        assertTrue(vm.failedAttemptToken.value > pulseBefore)
    }

    @Test
    fun subMinimumAndOverflowedShowSameErrorAndSkipTheKdf() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(listOf("D1"), overflowed = false)
        assertEquals(CaptionKind.WRONG_CODE, vm.caption.value.primary)
        vm.commitInternal(current, overflowed = true)
        assertEquals(CaptionKind.WRONG_CODE, vm.caption.value.primary)
        assertEquals(2, vm.failedAttemptToken.value)
        assertEquals(0, repo.matchesCallCount)
    }

    @Test
    fun inputRevertsTheErrorCaption() = runTest {
        val (vm, _) = make()
        vm.commitInternal(List(4) { "D9" }, overflowed = false)
        vm.inputReceived()
        assertFalse(vm.caption.value.isError)
        assertEquals(CaptionKind.PROMPT_CURRENT, vm.caption.value.primary)
    }

    @Test
    fun rightCurrentAdvancesToEnterNew() = runTest {
        val (vm, _) = make()
        vm.commitInternal(current, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(CaptionKind.PROMPT_NEW_CHANGE, vm.caption.value.primary)
        assertEquals(DisguiseMode.CAPTURE_NEW, vm.modeForPhase(vm.phase.value))
    }

    @Test
    fun newCodeTooShortStaysInEnterNew() = runTest {
        val (vm, _) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(listOf("D5", "D6"), overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(CaptionKind.TOO_SHORT, vm.caption.value.primary)
    }

    @Test
    fun mismatchOnConfirmReturnsToEnterNew() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        vm.commitInternal(newCode.dropLast(1) + "D9", overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(CaptionKind.MISMATCH, vm.caption.value.primary)
        assertEquals(current, repo.stored) // old code untouched
    }

    @Test
    fun cancelKeepsOldPasscode() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        // Caller dismisses the screen: no further commits; nothing changed.
        assertEquals(current, repo.stored)
        assertEquals(0, repo.setCallCount)
    }

    @Test
    fun successfulChangeReplacesPasscodeAndPreservesTheFace() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.Done, vm.phase.value)
        assertEquals(newCode, repo.stored)
        assertEquals(1, repo.setCallCount) // ONE atomic replace
        assertEquals("calculator", repo.storedFaceId)
        assertEquals("calculator", repo.storedAlphabet?.tokenSetId)
        assertFalse(repo.matches(current))
        assertTrue(repo.matches(newCode))
    }

    // MARK: the switch flow (decisions §5)

    private fun makeSwitch(
        failWrites: Boolean = false,
    ): Triple<ChangePasscodeViewModel, FakePasscodeRepository, MutableList<String>> {
        val repo = FakePasscodeRepository(
            stored = current,
            storedFaceId = "calculator",
            failWrites = failWrites,
        )
        val notified = mutableListOf<String>()
        val vm = ChangePasscodeViewModel(
            passcodeRepository = repo,
            registry = testRegistry(),
            currentFace = CalculatorDisguise,
            switchDisguise = true,
            onDisguiseChanged = { notified.add(it) },
        )
        return Triple(vm, repo, notified)
    }

    @Test
    fun switchGoesThroughThePicker() = runTest {
        val (vm, _, _) = makeSwitch()
        vm.commitInternal(current, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.PickDisguise, vm.phase.value)
        // The old face still renders the picker step.
        assertEquals("calculator", vm.faceForPhase(vm.phase.value).id)
    }

    @Test
    fun theCurrentFaceCannotBePicked() = runTest {
        val (vm, _, _) = makeSwitch()
        vm.commitInternal(current, overflowed = false)
        vm.selectTargetDisguise("calculator")
        vm.confirmPick() // no-op: the CTA is disabled in this state
        assertEquals(ChangePasscodeViewModel.Phase.PickDisguise, vm.phase.value)

        vm.selectTargetDisguise("numpad")
        vm.confirmPick()
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals("numpad", vm.faceForPhase(vm.phase.value).id)
    }

    @Test
    fun aWrongVerifyPulsesAndNeverReachesThePicker() = runTest {
        val (vm, _, _) = makeSwitch()
        vm.commitInternal(List(4) { "D9" }, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.VerifyCurrent, vm.phase.value)
        assertEquals(1, vm.failedAttemptToken.value)
        assertEquals(CaptionKind.WRONG_CODE, vm.caption.value.primary)
    }

    @Test
    fun switchCommitsOnceWithTheNewAlphabetAndFace() = runTest {
        val (vm, repo, notified) = makeSwitch()
        val pin = listOf("D1", "D2", "D3", "D4", "D5", "D6")
        vm.commitInternal(current, overflowed = false)
        vm.selectTargetDisguise("numpad")
        vm.confirmPick()
        vm.commitInternal(pin, overflowed = false)
        assertEquals(CaptionKind.PROMPT_CONFIRM_CHANGE, vm.caption.value.primary)
        vm.commitInternal(pin, overflowed = false)

        assertEquals(ChangePasscodeViewModel.Phase.Done, vm.phase.value)
        assertEquals(1, repo.setCallCount)
        assertEquals(pin, repo.stored)
        assertEquals("numpad", repo.storedFaceId)
        assertEquals(NumpadDisguise.alphabet, repo.storedAlphabet)
        assertEquals(listOf("numpad"), notified)
        // The old code no longer matches.
        assertFalse(repo.matches(current))
    }

    @Test
    fun aWriteFailureLeavesTheOldEnvelopeAndReturnsToCaptureNew() = runTest {
        val (vm, repo, notified) = makeSwitch(failWrites = true)
        val pin = listOf("D1", "D2", "D3", "D4", "D5", "D6")
        vm.commitInternal(current, overflowed = false)
        vm.selectTargetDisguise("pattern")
        vm.confirmPick()
        vm.commitInternal(pin, overflowed = false)
        vm.commitInternal(pin, overflowed = false)

        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(CaptionKind.PROMPT_NEW_CHANGE, vm.caption.value.primary)
        assertEquals(current, repo.stored) // old envelope intact
        assertEquals("calculator", repo.storedFaceId)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun cancellingAtAnyPhaseLeavesTheOldCodeAndFace() = runTest {
        val pin = listOf("D1", "D2", "D3", "D4", "D5", "D6")
        // Abandoning is simply "stop committing": assert at each step.
        listOf(0, 1, 2, 3).forEach { stopAfter ->
            val (vm, repo, notified) = makeSwitch()
            if (stopAfter >= 1) vm.commitInternal(current, overflowed = false)
            if (stopAfter >= 2) {
                vm.selectTargetDisguise("numpad")
                vm.confirmPick()
            }
            if (stopAfter >= 3) vm.commitInternal(pin, overflowed = false)
            assertEquals(current, repo.stored)
            assertEquals("calculator", repo.storedFaceId)
            assertEquals(0, repo.setCallCount)
            assertTrue(notified.isEmpty())
        }
    }
}
