package com.calcplus.calculator.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.calcplus.calculator.app.UnlockReveal.Kind
import com.calcplus.calculator.core.lock.LockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the iteration-2 decisions §1 rule for the unlock transition: only a
 * change INTO Unlocked plays the zoom-in reveal, every other lock-state change
 * is an instant cut, and "Remove animations" turns the reveal into a cut.
 * Pure logic — no Compose runtime, no Android framework.
 */
class UnlockRevealTest {
    private val states = listOf(LockState.NeedsSetup, LockState.Locked, LockState.Unlocked)

    // Which changes animate.

    @Test
    fun lockedToUnlockedReveals() {
        assertEquals(Kind.REVEAL, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, 1f))
    }

    @Test
    fun firstRunSetupToUnlockedRevealsExactlyLikeANormalUnlock() {
        assertEquals(Kind.REVEAL, UnlockReveal.kind(LockState.NeedsSetup, LockState.Unlocked, 1f))
    }

    @Test
    fun everyLockIsACut() {
        // Manual "Lock now", background lock, and the erase-everything reset alike.
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.Unlocked, LockState.Locked, 1f))
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.Unlocked, LockState.NeedsSetup, 1f))
    }

    @Test
    fun setupAndLockedNeverAnimateBetweenEachOther() {
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.NeedsSetup, LockState.Locked, 1f))
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.Locked, LockState.NeedsSetup, 1f))
    }

    @Test
    fun sameStateChangesAreCuts() {
        // What a calculatorEpoch bump (calculator recreated pristine) or a
        // redundant emission looks like at this level: nothing to animate.
        states.forEach { state ->
            assertEquals("$state → $state", Kind.CUT, UnlockReveal.kind(state, state, 1f))
        }
    }

    @Test
    fun exactlyTwoPathsRevealAcrossTheWholeMatrix() {
        val revealing = mutableListOf<Pair<LockState, LockState>>()
        states.forEach { from ->
            states.forEach { to ->
                if (UnlockReveal.kind(from, to, 1f) == Kind.REVEAL) revealing += from to to
            }
        }
        assertEquals(
            listOf(
                LockState.NeedsSetup to LockState.Unlocked,
                LockState.Locked to LockState.Unlocked,
            ),
            revealing,
        )
    }

    // The animator scale ("Remove animations").

    @Test
    fun removeAnimationsTurnsTheRevealIntoACut() {
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, 0f))
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.NeedsSetup, LockState.Unlocked, 0f))
    }

    @Test
    fun anyPositiveAnimatorScaleKeepsTheReveal() {
        // The developer-options steps: 0.5x, 1x, 1.5x, 2x, 5x, 10x.
        listOf(0.5f, 1f, 1.5f, 2f, 5f, 10f).forEach { scale ->
            assertEquals("scale $scale", Kind.REVEAL, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, scale))
        }
    }

    @Test
    fun malformedScalesFailClosedToACut() {
        listOf(-1f, Float.NaN).forEach { scale ->
            assertEquals("scale $scale", Kind.CUT, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, scale))
        }
    }

    @Test
    fun theScaleNeverTurnsACutIntoAReveal() {
        states.forEach { from ->
            states.filter { it != LockState.Unlocked }.forEach { to ->
                assertEquals("$from → $to", Kind.CUT, UnlockReveal.kind(from, to, 10f))
            }
        }
    }

    // Constants and curve (decisions §11).

    @Test
    fun constantsMatchTheSharedDecision() {
        assertEquals(260, UnlockReveal.UNLOCK_REVEAL_DURATION_MS)
        assertEquals(0.92f, UnlockReveal.INITIAL_SCALE, 0f)
    }

    @Test
    fun easingIsEmphasizedDecelerate() {
        val easing = UnlockReveal.EASING
        assertEquals(0f, easing.transform(0f), 1e-4f)
        assertEquals(1f, easing.transform(1f), 1e-4f)
        // cubic-bezier(0.05, 0.7, 0.1, 1.0) has covered ~95 % of its travel at
        // the halfway point — the "arrive fast, settle slowly" shape.
        val halfway = easing.transform(0.5f)
        assertTrue("progress at t=0.5 was $halfway", halfway in 0.9f..0.98f)
        // Monotonic: no overshoot, no bounce.
        var previous = 0f
        for (step in 1..20) {
            val value = easing.transform(step / 20f)
            assertTrue("t=${step / 20f}: $value < $previous", value >= previous)
            previous = value
        }
    }

    // The AnimatedContent transform each kind produces.

    @Test
    fun revealFadesAndScalesTheVaultInWhileFadingTheCalculatorOut() {
        val transform = UnlockReveal.contentTransform(Kind.REVEAL)
        val spec = tween<Float>(durationMillis = UnlockReveal.UNLOCK_REVEAL_DURATION_MS, easing = UnlockReveal.EASING)
        assertEquals(
            fadeIn(spec) + scaleIn(spec, initialScale = UnlockReveal.INITIAL_SCALE),
            transform.targetContentEnter,
        )
        // Opacity only on the way out — the calculator never scales.
        assertEquals(fadeOut(spec), transform.initialContentExit)
        assertNull(transform.sizeTransform)
    }

    @Test
    fun cutBringsTheNewSurfaceInWithoutAnimationAndSnapsTheOldOneAway() {
        val transform = UnlockReveal.contentTransform(Kind.CUT)
        // Nothing animates in: the incoming surface is opaque and at full size
        // on its very first frame.
        assertEquals(EnterTransition.None, transform.targetContentEnter)
        // The outgoing surface is snapped to alpha 0 (and to scale 1, so an
        // in-flight scale-in has an explicit zero-length target) rather than
        // left to ExitTransition.None, which would let an interrupted reveal
        // spring the vault back to fully opaque before disposing it.
        assertEquals(
            fadeOut(snap()) + scaleOut(snap(), targetScale = 1f),
            transform.initialContentExit,
        )
        assertNotEquals(fadeOut(snap()), transform.initialContentExit) // the scale half is not optional
        assertNull(transform.sizeTransform)
    }

    @Test
    fun cutExitFadeAndScaleHaveZeroDuration() {
        // The transform's exit is structurally equal to one built from
        // `snap()`; pin that the spec it is built from really is zero-length
        // (no delay either). This is what pins the FAST DISPOSAL of the
        // outgoing surface on a plain lock. It is not what guarantees that no
        // vault pixel shows: a lock that interrupts a running reveal makes
        // animation-core swap this snap for its interruption spring on the
        // in-flight alpha, and the vault would ease out behind a translucent
        // calculator. That is closed by the `drawWithContent` guard in
        // SafeBoxApp, which never draws an exiting Unlocked surface regardless
        // of the alpha the animation lands on.
        val cutSpec = snap<Float>()
        val vectorized = cutSpec.vectorize(Float.VectorConverter)
        val one = AnimationVector1D(1f)
        val zero = AnimationVector1D(0f)
        assertEquals(0L, vectorized.getDurationNanos(one, zero, zero))
        assertEquals(0, vectorized.delayMillis)
        assertEquals(0, vectorized.durationMillis)
        // At play time 0 the value is already the target: alpha 1 → 0, scale → 1.
        assertEquals(0f, vectorized.getValueFromNanos(0L, one, zero, zero).value, 0f)
        assertEquals(
            fadeOut(cutSpec) + scaleOut(cutSpec, targetScale = 1f),
            UnlockReveal.contentTransform(Kind.CUT).initialContentExit,
        )
        // And a cut is genuinely different from a timed fade of any length.
        assertNotEquals(fadeOut(tween(1)), UnlockReveal.contentTransform(Kind.CUT).initialContentExit)
    }

    @Test
    fun cutSurfaceIsDrawnAboveARevealedVault() {
        // A CUT always brings in a locked surface (or a vault with animations
        // off); a REVEAL only ever brings in the vault. On a lock the calculator
        // must cover the still-composed vault on the very first frame.
        assertEquals(UnlockReveal.CUT_Z_INDEX, UnlockReveal.contentTransform(Kind.CUT).targetContentZIndex, 0f)
        assertEquals(UnlockReveal.REVEAL_Z_INDEX, UnlockReveal.contentTransform(Kind.REVEAL).targetContentZIndex, 0f)
        assertTrue(UnlockReveal.CUT_Z_INDEX > UnlockReveal.REVEAL_Z_INDEX)
    }
}
