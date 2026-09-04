package com.calcplus.calculator.core.lock

import com.calcplus.calculator.core.disguise.DisguiseEvent

/**
 * The host-owned entry buffer, generalized from `KeySequenceRecorder` over
 * opaque token IDs so every lock face shares one implementation of the length
 * and overflow rules (decisions §1.2).
 *
 * | Event                        | Buffer   | Overflow flag       |
 * |------------------------------|----------|---------------------|
 * | `Token`, buffer < 32         | append   | unchanged           |
 * | `Token`, buffer == 32        | unchanged| **set**             |
 * | `RemoveLast`, buffer empty   | no-op    | unchanged           |
 * | `RemoveLast`, buffer non-empty| pop last | **unchanged (sticky)** |
 * | `Clear`                      | empty    | reset               |
 * | `Commit`                     | taken    | taken, then reset   |
 *
 * The overflow flag is sticky through [removeLast] on purpose: once a 33rd
 * token has been seen the entry is unrecoverable by backspacing, and the only
 * recovery is [clear] (PIN pad: long-press ⌫) or a commit that fails as too
 * long. Nothing here is ever logged.
 */
class TokenRecorder {
    companion object {
        const val MAX_TOKENS = 32
    }

    private val _buffer = mutableListOf<String>()
    val buffer: List<String> get() = _buffer.toList()

    var overflowed = false
        private set

    fun record(token: String) {
        if (_buffer.size >= MAX_TOKENS) {
            overflowed = true
        } else {
            _buffer.add(token)
        }
    }

    /** Pops the last token. The overflow flag deliberately survives. */
    fun removeLast() {
        if (_buffer.isNotEmpty()) _buffer.removeAt(_buffer.size - 1)
    }

    fun clear() {
        _buffer.clear()
        overflowed = false
    }

    data class Commit(val tokens: List<String>, val overflowed: Boolean)

    /** Snapshots the buffer and the flag, then resets both. */
    fun takeCommit(): Commit {
        val commit = Commit(_buffer.toList(), overflowed)
        clear()
        return commit
    }

    /**
     * Applies one face event. Returns a [Commit] for — and only for —
     * [DisguiseEvent.Commit]; every other event returns null.
     */
    fun handle(event: DisguiseEvent): Commit? = when (event) {
        is DisguiseEvent.Token -> { record(event.id); null }
        DisguiseEvent.RemoveLast -> { removeLast(); null }
        DisguiseEvent.Clear -> { clear(); null }
        DisguiseEvent.Commit -> takeCommit()
    }
}
