package com.calcplus.calculator.feature.numpad

import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.lock.TokenRecorder

/**
 * The PIN pad's display rules, kept pure so they are unit-testable without a
 * composition (decisions §2.3). This counts *dots*, nothing else: the face has
 * no buffer, no token list and no idea what the digits were — the host's
 * [TokenRecorder] owns all of that.
 */
object NumpadEntry {
    /** Layout constants (decisions §8). */
    const val COLUMN_MAX_WIDTH_DP = 320f
    const val KEY_GAP_DP = 24f
    const val KEY_MIN_DIAMETER_DP = 64f
    const val KEY_MAX_DIAMETER_DP = 80f
    const val DOTS_TO_KEYPAD_GAP_DP = 40f
    const val DOT_DIAMETER_DP = 12f
    const val DOT_GAP_DP = 12f
    const val DOT_SHRINK_FLOOR_DP = 6f

    /** Presses beyond this still animate, buzz and emit — they just add no dot. */
    const val VISIBLE_DOT_CAP = TokenRecorder.MAX_TOKENS

    fun afterDigit(count: Int): Int = count + 1

    /** Backspace on an empty entry is a no-op, exactly like the recorder's. */
    fun afterBackspace(count: Int): Int = (count - 1).coerceAtLeast(0)

    fun afterClear(): Int = 0

    /**
     * §2.2 rule 3: the capture modes clear the entry immediately on commit (the
     * caption tells the outcome); `disguise` and `verifyCurrent` keep the dots
     * on screen and keep accepting input until the host tears the face down or
     * pulses it.
     */
    fun afterCommit(count: Int, mode: DisguiseMode): Int = when (mode) {
        DisguiseMode.CAPTURE_NEW, DisguiseMode.CONFIRM_NEW -> 0
        DisguiseMode.DISGUISE, DisguiseMode.VERIFY_CURRENT -> count
    }

    fun visibleDots(count: Int): Int = count.coerceIn(0, VISIBLE_DOT_CAP)

    /** Circular key diameter for a column of [columnWidthDp] (3 keys, 2 gaps). */
    fun keyDiameter(columnWidthDp: Float): Float =
        ((columnWidthDp - 2f * KEY_GAP_DP) / 3f)
            .coerceIn(KEY_MIN_DIAMETER_DP, KEY_MAX_DIAMETER_DP)

    /**
     * Dot diameter and gap for [dots] dots in [columnWidthDp]. Both shrink
     * uniformly when the row would overflow, with a floor of
     * [DOT_SHRINK_FLOOR_DP].
     */
    fun dotMetrics(dots: Int, columnWidthDp: Float): Pair<Float, Float> {
        if (dots <= 0) return DOT_DIAMETER_DP to DOT_GAP_DP
        val natural = dots * DOT_DIAMETER_DP + (dots - 1) * DOT_GAP_DP
        if (natural <= columnWidthDp) return DOT_DIAMETER_DP to DOT_GAP_DP
        val scale = (columnWidthDp / natural).coerceIn(0f, 1f)
        val size = (DOT_DIAMETER_DP * scale).coerceAtLeast(DOT_SHRINK_FLOOR_DP)
        val gap = (DOT_GAP_DP * scale).coerceAtLeast(DOT_SHRINK_FLOOR_DP / 2f)
        return size to gap
    }
}
