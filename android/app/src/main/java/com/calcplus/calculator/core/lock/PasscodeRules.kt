package com.calcplus.calculator.core.lock

import com.calcplus.calculator.feature.calculator.CalcKey

/** Shared validation for first-run setup and the Settings change flow. */
object PasscodeRules {
    const val MIN_KEYS = 4
    const val MAX_KEYS = 32

    fun isValidLength(sequence: List<CalcKey>): Boolean =
        sequence.size in MIN_KEYS..MAX_KEYS

    /** Soft warning only, never a block: a single repeated key (e.g. 7777). */
    fun isTrivial(sequence: List<CalcKey>): Boolean =
        sequence.isNotEmpty() && sequence.toSet().size == 1
}
