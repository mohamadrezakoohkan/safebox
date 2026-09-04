package com.calcplus.calculator.lock

import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.lock.TokenRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole §1.2 event table, including sticky overflow through removeLast. */
class TokenRecorderTest {
    @Test
    fun recordsTokensInOrder() {
        val recorder = TokenRecorder()
        recorder.handle(DisguiseEvent.Token("D1"))
        recorder.handle(DisguiseEvent.Token("ADD"))
        recorder.handle(DisguiseEvent.Token("D2"))
        val commit = recorder.handle(DisguiseEvent.Commit)!!
        assertEquals(listOf("D1", "ADD", "D2"), commit.tokens)
        assertFalse(commit.overflowed)
    }

    @Test
    fun onlyCommitReturnsACommit() {
        val recorder = TokenRecorder()
        assertNull(recorder.handle(DisguiseEvent.Token("D1")))
        assertNull(recorder.handle(DisguiseEvent.RemoveLast))
        assertNull(recorder.handle(DisguiseEvent.Clear))
        assertEquals(emptyList<String>(), recorder.handle(DisguiseEvent.Commit)!!.tokens)
    }

    @Test
    fun commitResetsBufferAndFlag() {
        val recorder = TokenRecorder()
        recorder.record("D1")
        recorder.takeCommit()
        val second = recorder.takeCommit()
        assertTrue(second.tokens.isEmpty())
        assertFalse(second.overflowed)
    }

    @Test
    fun thirtyThirdTokenSetsOverflowFlag() {
        val recorder = TokenRecorder()
        repeat(32) { recorder.record("D7") }
        assertFalse(recorder.overflowed)
        recorder.record("D7") // 33rd
        assertTrue(recorder.overflowed)
        val commit = recorder.takeCommit()
        assertTrue(commit.overflowed)
        assertEquals(32, commit.tokens.size)
    }

    @Test
    fun clearResetsBufferAndOverflowFlag() {
        val recorder = TokenRecorder()
        repeat(40) { recorder.record("D7") }
        assertTrue(recorder.overflowed)
        recorder.handle(DisguiseEvent.Clear)
        assertTrue(recorder.buffer.isEmpty())
        assertFalse(recorder.overflowed)
    }

    @Test
    fun removeLastOnAnEmptyBufferIsANoOp() {
        val recorder = TokenRecorder()
        recorder.handle(DisguiseEvent.RemoveLast)
        assertTrue(recorder.buffer.isEmpty())
        assertFalse(recorder.overflowed)
    }

    @Test
    fun removeLastPopsTheMostRecentToken() {
        val recorder = TokenRecorder()
        recorder.record("D1")
        recorder.record("D2")
        recorder.record("D3")
        recorder.handle(DisguiseEvent.RemoveLast)
        assertEquals(listOf("D1", "D2"), recorder.buffer)
    }

    @Test
    fun overflowIsStickyThroughRemoveLast() {
        // Once a 33rd token has been seen the entry is unrecoverable by
        // backspacing: only clear (or a failing commit) gets out of it.
        val recorder = TokenRecorder()
        repeat(33) { recorder.record("D7") }
        assertTrue(recorder.overflowed)
        repeat(10) { recorder.handle(DisguiseEvent.RemoveLast) }
        assertEquals(22, recorder.buffer.size)
        assertTrue(recorder.overflowed)
        val commit = recorder.takeCommit()
        assertTrue(commit.overflowed)
        // …and the commit itself is what finally resets it.
        assertFalse(recorder.overflowed)
    }

    @Test
    fun serializationContract() {
        assertEquals(
            "D7|ADD|D3|DOT",
            AlphabetDescriptor.serialize(listOf("D7", "ADD", "D3", "DOT")),
        )
        assertEquals(
            "D1|D2|ADD|D3|D4",
            AlphabetDescriptor.serialize(listOf("D1", "D2", "ADD", "D3", "D4")),
        )
    }
}
