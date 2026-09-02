package com.calcplus.calculator.settings

import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.lock.BannerText
import com.calcplus.calculator.feature.calculator.CalcKey
import com.calcplus.calculator.feature.settings.ChangePasscodeViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepo(var stored: List<CalcKey>?) : PasscodeRepository {
    override suspend fun set(sequence: List<CalcKey>) {
        stored = sequence
    }

    override suspend fun matches(sequence: List<CalcKey>): Boolean = stored == sequence
}

class ChangePasscodeViewModelTest {
    private val current = listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.D4)
    private val newCode = listOf(CalcKey.D5, CalcKey.ADD, CalcKey.D7, CalcKey.PCT)

    private fun make(): Pair<ChangePasscodeViewModel, FakeRepo> {
        val repo = FakeRepo(stored = current)
        return ChangePasscodeViewModel(repo) to repo
    }

    @Test
    fun startsInVerifyCurrent() {
        val (vm, _) = make()
        assertEquals(ChangePasscodeViewModel.Phase.VerifyCurrent, vm.phase.value)
        assertEquals(BannerText.VERIFY_CURRENT, vm.banner.value.primary)
    }

    @Test
    fun wrongCurrentShowsVisibleError() = runTest {
        val (vm, _) = make()
        val shakeBefore = vm.shakeToken.value
        vm.commitInternal(List(4) { CalcKey.D9 }, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.VerifyCurrent, vm.phase.value) // unlimited retries
        assertEquals(BannerText.VERIFY_ERROR, vm.banner.value.primary)
        assertTrue(vm.bannerIsError.value)
        assertTrue(vm.shakeToken.value > shakeBefore)
    }

    @Test
    fun subMinimumAndOverflowedShowSameError() = runTest {
        val (vm, _) = make()
        vm.commitInternal(listOf(CalcKey.D1), overflowed = false)
        assertEquals(BannerText.VERIFY_ERROR, vm.banner.value.primary)
        vm.commitInternal(current, overflowed = true)
        assertEquals(BannerText.VERIFY_ERROR, vm.banner.value.primary)
    }

    @Test
    fun keyPressClearsErrorCaption() = runTest {
        val (vm, _) = make()
        vm.commitInternal(List(4) { CalcKey.D9 }, overflowed = false)
        vm.keyPressed()
        assertFalse(vm.bannerIsError.value)
        assertEquals(BannerText.VERIFY_CURRENT, vm.banner.value.primary)
    }

    @Test
    fun rightCurrentAdvancesToEnterNew() = runTest {
        val (vm, _) = make()
        vm.commitInternal(current, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(BannerText.CHANGE_ENTER_NEW, vm.banner.value.primary)
    }

    @Test
    fun newCodeTooShortStaysInEnterNew() = runTest {
        val (vm, _) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(listOf(CalcKey.D5, CalcKey.D6), overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(BannerText.SETUP_TOO_SHORT, vm.banner.value.primary)
    }

    @Test
    fun mismatchOnConfirmReturnsToEnterNew() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        vm.commitInternal(newCode.dropLast(1) + CalcKey.D9, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.EnterNew, vm.phase.value)
        assertEquals(BannerText.SETUP_MISMATCH, vm.banner.value.primary)
        assertEquals(current, repo.stored) // old code untouched
    }

    @Test
    fun cancelKeepsOldPasscode() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        // Caller dismisses the screen: no further commits; nothing changed.
        assertEquals(current, repo.stored)
    }

    @Test
    fun successfulChangeReplacesPasscode() = runTest {
        val (vm, repo) = make()
        vm.commitInternal(current, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        vm.commitInternal(newCode, overflowed = false)
        assertEquals(ChangePasscodeViewModel.Phase.Done, vm.phase.value)
        assertEquals(newCode, repo.stored)
        assertFalse(repo.matches(current))
        assertTrue(repo.matches(newCode))
    }
}
