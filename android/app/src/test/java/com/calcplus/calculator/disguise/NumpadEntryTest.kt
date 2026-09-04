package com.calcplus.calculator.disguise

import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.lock.TokenRecorder
import com.calcplus.calculator.feature.numpad.NumpadEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PIN pad's display rules (decisions §2.3, §8). */
class NumpadEntryTest {
    @Test
    fun digitsAddDotsAndBackspaceRemovesThem() {
        var count = 0
        repeat(6) { count = NumpadEntry.afterDigit(count) }
        assertEquals(6, count)
        count = NumpadEntry.afterBackspace(count)
        assertEquals(5, count)
    }

    @Test
    fun backspaceOnAnEmptyEntryIsANoOp() {
        assertEquals(0, NumpadEntry.afterBackspace(0))
    }

    @Test
    fun longPressClearEmptiesTheRow() {
        assertEquals(0, NumpadEntry.afterClear())
    }

    @Test
    fun captureModesClearOnCommitAndTheOthersDoNot() {
        assertEquals(0, NumpadEntry.afterCommit(6, DisguiseMode.CAPTURE_NEW))
        assertEquals(0, NumpadEntry.afterCommit(6, DisguiseMode.CONFIRM_NEW))
        // In disguise / verifyCurrent the dots stay until the host tears the
        // face down or pulses it (§2.2 rule 3).
        assertEquals(6, NumpadEntry.afterCommit(6, DisguiseMode.DISGUISE))
        assertEquals(6, NumpadEntry.afterCommit(6, DisguiseMode.VERIFY_CURRENT))
    }

    @Test
    fun visibleDotsAreCappedAtTheRecorderMaximum() {
        assertEquals(TokenRecorder.MAX_TOKENS, NumpadEntry.VISIBLE_DOT_CAP)
        assertEquals(32, NumpadEntry.visibleDots(32))
        // A 33rd press still animates, buzzes and emits — it just adds no dot.
        assertEquals(32, NumpadEntry.visibleDots(33))
        assertEquals(32, NumpadEntry.visibleDots(120))
        assertEquals(0, NumpadEntry.visibleDots(0))
    }

    @Test
    fun keyDiameterIsClampedToTheSpecBand() {
        assertEquals(64f, NumpadEntry.keyDiameter(120f), 0.001f)
        assertEquals(80f, NumpadEntry.keyDiameter(1000f), 0.001f)
        // A 320-wide column: (320 − 48) / 3 = 90.67 → clamped to 80.
        assertEquals(80f, NumpadEntry.keyDiameter(NumpadEntry.COLUMN_MAX_WIDTH_DP), 0.001f)
        // And a middling column lands inside the band.
        val mid = NumpadEntry.keyDiameter(250f)
        assertTrue(mid in NumpadEntry.KEY_MIN_DIAMETER_DP..NumpadEntry.KEY_MAX_DIAMETER_DP)
    }

    @Test
    fun dotsKeepTheirNaturalSizeUntilTheRowWouldOverflow() {
        val (size, gap) = NumpadEntry.dotMetrics(6, 320f)
        assertEquals(NumpadEntry.DOT_DIAMETER_DP, size, 0.001f)
        assertEquals(NumpadEntry.DOT_GAP_DP, gap, 0.001f)
    }

    @Test
    fun dotsShrinkUniformlyOnceTheRowWouldOverflow() {
        val (size, gap) = NumpadEntry.dotMetrics(20, 320f)
        assertTrue(size < NumpadEntry.DOT_DIAMETER_DP)
        assertTrue(size > NumpadEntry.DOT_SHRINK_FLOOR_DP)
        // Uniform: size and gap shrink by the same factor while above the floor.
        assertEquals(size / NumpadEntry.DOT_DIAMETER_DP, gap / NumpadEntry.DOT_GAP_DP, 0.001f)
    }

    @Test
    fun theShrinkStopsAtTheFloor() {
        // A full 32-dot row on a 320-wide column would need ~5 dp dots; the
        // floor keeps them visible instead.
        val (size, _) = NumpadEntry.dotMetrics(NumpadEntry.VISIBLE_DOT_CAP, 320f)
        assertEquals(NumpadEntry.DOT_SHRINK_FLOOR_DP, size, 0.001f)
    }

    @Test
    fun anEmptyRowHasTheRestingMetrics() {
        val (size, gap) = NumpadEntry.dotMetrics(0, 320f)
        assertEquals(NumpadEntry.DOT_DIAMETER_DP, size, 0.001f)
        assertEquals(NumpadEntry.DOT_GAP_DP, gap, 0.001f)
    }
}
