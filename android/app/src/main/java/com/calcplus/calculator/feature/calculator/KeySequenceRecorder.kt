package com.calcplus.calculator.feature.calculator

/**
 * Records every allowed key pressed since the last AC/=. Capped at 32 keys:
 * the 33rd key sets an overflow flag, and an overflowed buffer never matches
 * on commit. CLEAR resets both the buffer and the flag.
 */
class KeySequenceRecorder {
    companion object {
        const val MAX_KEYS = 32
    }

    private val _buffer = mutableListOf<CalcKey>()
    val buffer: List<CalcKey> get() = _buffer.toList()
    var overflowed = false
        private set

    fun record(key: CalcKey) {
        if (!key.isPasscodeKey) {
            if (key == CalcKey.CLEAR) clear()
            return
        }
        if (_buffer.size >= MAX_KEYS) {
            overflowed = true
        } else {
            _buffer.add(key)
        }
    }

    data class Commit(val keys: List<CalcKey>, val overflowed: Boolean)

    /** Returns the buffered sequence (excluding the trailing =) and the flag, then resets both. */
    fun takeCommit(): Commit {
        val commit = Commit(_buffer.toList(), overflowed)
        clear()
        return commit
    }

    fun clear() {
        _buffer.clear()
        overflowed = false
    }
}
