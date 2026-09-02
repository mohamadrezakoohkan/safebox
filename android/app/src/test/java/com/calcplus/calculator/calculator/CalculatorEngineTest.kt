package com.calcplus.calculator.calculator

import com.calcplus.calculator.feature.calculator.CalcKey
import com.calcplus.calculator.feature.calculator.CalculatorEngine
import com.calcplus.calculator.feature.calculator.CalcOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The idea plan's shared input→display sequence table is the cross-platform
 * acceptance contract — the engine must reproduce it exactly (DoD item).
 */
class CalculatorEngineTest {
    private fun keys(spec: String): List<CalcKey> = spec.split(" ").map { token ->
        when (token) {
            "0" -> CalcKey.D0; "1" -> CalcKey.D1; "2" -> CalcKey.D2; "3" -> CalcKey.D3
            "4" -> CalcKey.D4; "5" -> CalcKey.D5; "6" -> CalcKey.D6; "7" -> CalcKey.D7
            "8" -> CalcKey.D8; "9" -> CalcKey.D9
            "." -> CalcKey.DOT
            "+" -> CalcKey.ADD
            "-", "−" -> CalcKey.SUB
            "×", "*" -> CalcKey.MUL
            "÷", "/" -> CalcKey.DIV
            "%" -> CalcKey.PCT
            "±" -> CalcKey.SIGN
            "=" -> CalcKey.EQUALS
            "C", "AC" -> CalcKey.CLEAR
            else -> error("unknown token $token")
        }
    }

    private fun run(spec: String): String {
        val engine = CalculatorEngine()
        keys(spec).forEach { engine.press(it) }
        return engine.display
    }

    // The 17-row shared table.
    @Test fun sharedTableRow1() = assertEquals("5", run("2 + 3 ="))
    @Test fun sharedTableRow2() = assertEquals("8", run("2 + 3 = ="))
    @Test fun sharedTableRow3() = assertEquals("6", run("2 + × 3 ="))
    @Test fun sharedTableRow4() = assertEquals("7.49", run("7 + 7 % ="))
    @Test fun sharedTableRow5() = assertEquals("0.07", run("7 %"))
    @Test fun sharedTableRow6() = assertEquals("4", run("8 × 5 0 % ="))
    @Test fun sharedTableRow7() = assertEquals("4", run("2 × ="))
    @Test fun sharedTableRow8() = assertEquals("1", run(". 5 + . 5 ="))
    @Test fun sharedTableRow9() = assertEquals("Error", run("8 ÷ 0 ="))
    @Test fun sharedTableRow10() = assertEquals("0.3", run("0 . 1 + 0 . 2 ="))
    @Test fun sharedTableRow11() = assertEquals("0", run("="))
    @Test fun sharedTableRow12() = assertEquals("-0", run("±"))
    @Test fun sharedTableRow13() = assertEquals("0", run("± ±"))
    @Test fun sharedTableRow14() = assertEquals("0", run("± ± + % ="))
    @Test fun sharedTableRow15() = assertEquals("0", run("+ + ="))
    @Test fun sharedTableRow16() = assertEquals("0", run("% ="))
    @Test fun sharedTableRow17() = assertEquals("46", run("1 2 + 3 4 ="))

    // Semantics beyond the table.
    @Test fun immediateExecutionNoPrecedence() = assertEquals("20", run("2 + 3 × 4 ="))
    @Test fun chainingShowsIntermediateResult() = assertEquals("5", run("2 + 3 ×"))
    @Test fun repeatedEqualsAfterNewEntry() = assertEquals("13", run("2 + 3 = 1 0 ="))
    @Test fun doubleDotIgnored() = assertEquals("1.52", run("1 . 5 . 2 ="))
    @Test fun signDuringEntry() {
        assertEquals("-5", run("5 ±"))
        assertEquals("-5", run("± 5"))
    }

    @Test fun errorRecoveryByDigit() = assertEquals("5", run("8 ÷ 0 = 5"))
    @Test fun errorRecoveryByClear() = assertEquals("0", run("8 ÷ 0 = AC"))
    @Test fun clearEntryVsAllClear() = assertEquals("9", run("5 + 3 C 4 ="))

    @Test
    fun equalsWithoutOperationCommitsEntry() {
        val engine = CalculatorEngine()
        keys("9 9 9 9 =").forEach { engine.press(it) }
        assertEquals("9,999", engine.display)
        assertTrue(engine.showsAllClear)
        engine.press(CalcKey.D5)
        assertEquals("5", engine.display)
    }

    @Test
    fun acClearLabelStateMachine() {
        val engine = CalculatorEngine()
        assertTrue(engine.showsAllClear)
        engine.press(CalcKey.D5)
        assertFalse(engine.showsAllClear)
        engine.press(CalcKey.CLEAR)
        assertTrue(engine.showsAllClear)
    }

    @Test
    fun pendingOperatorRing() {
        val engine = CalculatorEngine()
        engine.press(CalcKey.D2)
        assertNull(engine.ringOperator)
        engine.press(CalcKey.ADD)
        assertEquals(CalcOperator.ADD, engine.ringOperator)
        engine.press(CalcKey.MUL)
        assertEquals(CalcOperator.MUL, engine.ringOperator) // hops on replacement
        engine.press(CalcKey.D3)
        assertNull(engine.ringOperator) // clears on second-operand entry
    }

    // Display formatting.
    @Test fun thousandsSeparators() {
        assertEquals("1,234", run("1 2 3 4"))
        assertEquals("1,000,000", run("1 0 0 0 × 1 0 0 0 ="))
    }

    @Test fun entryCapAtNineDigits() = assertEquals("123,456,789", run("1 2 3 4 5 6 7 8 9 0"))
    @Test fun scientificOverflow() = assertEquals("8.99999999e9", run("9 9 9 9 9 9 9 9 9 × 9 ="))
    @Test fun scientificAtBillion() = assertEquals("1e9", run("9 9 9 9 9 9 9 9 9 + 1 ="))
    @Test fun tinyValuesGoScientific() = assertEquals("1e-9", run(". 0 0 0 0 1 × . 0 0 0 1 ="))
    @Test fun smallPlainDecimalStaysPlain() = assertEquals("0.00000001", run("1 ÷ 1 0 0 0 0 0 0 0 0 ="))
    @Test fun nineSignificantDigitRounding() = assertEquals("0.666666667", run("2 ÷ 3 ="))
    @Test fun trailingZerosStripped() = assertEquals("0.5", run("0 . 5 0 + 0 ="))
    @Test fun decimalEntryEcho() {
        assertEquals("0.", run("."))
        assertEquals("12.", run("1 2 ."))
    }

    @Test
    fun degenerateOperatorStreams() {
        assertEquals("0", run("+ × ÷ − ="))
        assertEquals("0", run("% % % ="))
        assertEquals("0", run("± % + ± % ="))
    }
}
