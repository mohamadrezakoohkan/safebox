package com.calcplus.calculator.calculator

import com.calcplus.calculator.feature.calculator.CalcKey
import com.calcplus.calculator.feature.calculator.KeySequenceRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeySequenceRecorderTest {
    @Test
    fun recordsPasscodeKeysInOrder() {
        val recorder = KeySequenceRecorder()
        recorder.record(CalcKey.D1)
        recorder.record(CalcKey.ADD)
        recorder.record(CalcKey.D2)
        val commit = recorder.takeCommit()
        assertEquals(listOf(CalcKey.D1, CalcKey.ADD, CalcKey.D2), commit.keys)
        assertFalse(commit.overflowed)
    }

    @Test
    fun commitResetsBufferAndFlag() {
        val recorder = KeySequenceRecorder()
        recorder.record(CalcKey.D1)
        recorder.takeCommit()
        val second = recorder.takeCommit()
        assertTrue(second.keys.isEmpty())
        assertFalse(second.overflowed)
    }

    @Test
    fun thirtyThirdKeySetsOverflowFlag() {
        val recorder = KeySequenceRecorder()
        repeat(32) { recorder.record(CalcKey.D7) }
        assertFalse(recorder.overflowed)
        recorder.record(CalcKey.D7) // 33rd
        assertTrue(recorder.overflowed)
        val commit = recorder.takeCommit()
        assertTrue(commit.overflowed)
        assertEquals(32, commit.keys.size)
    }

    @Test
    fun clearResetsBufferAndOverflowFlag() {
        val recorder = KeySequenceRecorder()
        repeat(40) { recorder.record(CalcKey.D7) }
        assertTrue(recorder.overflowed)
        recorder.record(CalcKey.CLEAR)
        assertTrue(recorder.buffer.isEmpty())
        assertFalse(recorder.overflowed)
    }

    @Test
    fun equalsIsNotRecorded() {
        val recorder = KeySequenceRecorder()
        recorder.record(CalcKey.D1)
        recorder.record(CalcKey.EQUALS)
        assertEquals(listOf(CalcKey.D1), recorder.buffer)
    }

    @Test
    fun serializationContract() {
        assertEquals("D7|ADD|D3|DOT", CalcKey.serialize(listOf(CalcKey.D7, CalcKey.ADD, CalcKey.D3, CalcKey.DOT)))
        assertEquals(
            "D1|D2|ADD|D3|D4",
            CalcKey.serialize(listOf(CalcKey.D1, CalcKey.D2, CalcKey.ADD, CalcKey.D3, CalcKey.D4)),
        )
    }
}
