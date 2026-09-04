package com.calcplus.calculator.disguise

import com.calcplus.calculator.core.disguise.AppIconManager
import com.calcplus.calculator.core.disguise.CoverAliases
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.pattern.PatternDisguise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alias swap behind the home-screen cover identities (§9a).
 *
 * The safety property under test is an ORDERING: the incoming alias is enabled
 * before any outgoing one is disabled. A fake switcher records the calls so the
 * order is actually asserted rather than assumed — done the other way round
 * there is a window with no launcher entry at all, and the app has vanished
 * from the home screen with a vault inside it.
 */
class AppIconManagerTest {
    private val allAliases =
        listOf(CoverAliases.CALCULATOR, CoverAliases.NOTEPAD, CoverAliases.GALLERY)

    /** Records every call in order and answers reads from its own state. */
    private class FakeSwitcher(
        enabled: String,
        private val failOn: String? = null,
    ) : AppIconManager.ComponentSwitcher {
        val state = mutableMapOf(enabled to true)
        val calls = mutableListOf<Pair<String, Boolean>>()

        override fun isEnabled(alias: String): Boolean = state[alias] == true

        override fun setEnabled(alias: String, enabled: Boolean) {
            calls += alias to enabled
            if (alias == failOn) throw SecurityException("component toggling refused")
            state[alias] = enabled
        }
    }

    private fun manager(switcher: AppIconManager.ComponentSwitcher) =
        AppIconManager(allAliases, switcher)

    @Test
    fun theTargetIsEnabledBeforeAnyOtherIsDisabled() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        manager(switcher).apply(NumpadDisguise)

        assertEquals(CoverAliases.NOTEPAD to true, switcher.calls.first())
        val firstDisable = switcher.calls.indexOfFirst { !it.second }
        val enableTarget = switcher.calls.indexOfFirst { it == CoverAliases.NOTEPAD to true }
        assertTrue("the target must be enabled first", enableTarget < firstDisable)
    }

    @Test
    fun exactlyOneAliasIsEnabledAfterASwap() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        manager(switcher).apply(PatternDisguise)

        assertEquals(
            listOf(CoverAliases.GALLERY),
            allAliases.filter { switcher.isEnabled(it) },
        )
    }

    @Test
    fun theOtherAliasesAreBothDisabled() {
        // Start from a drifted state where two entries are somehow enabled.
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        switcher.state[CoverAliases.GALLERY] = true

        manager(switcher).apply(NumpadDisguise)

        assertEquals(listOf(CoverAliases.NOTEPAD), allAliases.filter { switcher.isEnabled(it) })
    }

    @Test
    fun applyingTheAlreadyEnabledIdentityTouchesNothing() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        manager(switcher).apply(CalculatorDisguise)

        assertTrue("a no-op must not call the package manager", switcher.calls.isEmpty())
    }

    /**
     * ...but only when it is the ONLY enabled one. A drifted install that also
     * has a stale alias enabled is healed rather than left alone.
     */
    @Test
    fun aDriftedStateIsHealedEvenWhenTheTargetIsAlreadyEnabled() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        switcher.state[CoverAliases.NOTEPAD] = true

        manager(switcher).apply(CalculatorDisguise)

        assertEquals(listOf(CoverAliases.CALCULATOR), allAliases.filter { switcher.isEnabled(it) })
    }

    /**
     * A failure is swallowed. By the time this runs the vault is already
     * re-enrolled; a stale icon is cosmetic and must never surface as a crash.
     */
    @Test
    fun aFailingPackageManagerIsSwallowed() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR, failOn = CoverAliases.GALLERY)
        manager(switcher).apply(PatternDisguise)

        // It threw on the very first call, so the outgoing entry is untouched —
        // which is the safe side of the failure: the app is still launchable.
        assertTrue(switcher.isEnabled(CoverAliases.CALCULATOR))
    }

    @Test
    fun anUnknownAliasIsIgnoredRatherThanDisablingEverything() {
        val switcher = FakeSwitcher(enabled = CoverAliases.CALCULATOR)
        manager(switcher).apply("com.calcplus.calculator.TipCalculatorAlias")

        assertTrue(switcher.calls.isEmpty())
        assertTrue(switcher.isEnabled(CoverAliases.CALCULATOR))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateAliasesAreRejected() {
        AppIconManager(
            listOf(CoverAliases.CALCULATOR, CoverAliases.CALCULATOR),
            FakeSwitcher(enabled = CoverAliases.CALCULATOR),
        )
    }
}
