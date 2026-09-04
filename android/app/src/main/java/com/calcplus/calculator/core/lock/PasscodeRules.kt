package com.calcplus.calculator.core.lock

/**
 * Shared validation for first-run setup, the change flow and the switch flow.
 * Face-independent by construction: it sees opaque token IDs only.
 */
object PasscodeRules {
    const val MIN_TOKENS = 4
    const val MAX_TOKENS = TokenRecorder.MAX_TOKENS

    fun isValidLength(tokens: List<String>): Boolean =
        tokens.size in MIN_TOKENS..MAX_TOKENS

    /** Soft warning only, never a block: a single repeated token (e.g. 7777). */
    fun isTrivial(tokens: List<String>): Boolean =
        tokens.isNotEmpty() && tokens.toSet().size == 1
}
