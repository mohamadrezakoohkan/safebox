package com.calcplus.calculator.feature.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcOperator { ADD, SUB, MUL, DIV }

/**
 * Pure basic-calculator state machine matching iOS Calculator basic-mode
 * semantics as pinned by the idea plan §2.1 (immediate execution, operator
 * replacement, unary/binary %, repeated =, ± edge cases, Error on ÷0) and the
 * display-formatting rules of the disguise design spec §4 (9 significant
 * digits, 9-digit entry cap, grouping separators, scientific presentation).
 *
 * Behavior-identical port of the validated iOS engine.
 */
class CalculatorEngine {
    private data class Entry(
        var digits: String = "", // typed characters: digits and at most one "."
        var negative: Boolean = false,
    ) {
        val hasDot: Boolean get() = digits.contains(".")
        val digitCount: Int get() = digits.count { it.isDigit() }

        val value: BigDecimal
            get() {
                var s = digits
                if (s.startsWith(".")) s = "0$s"
                if (s.isEmpty() || s == ".") s = "0"
                val d = BigDecimal(s)
                return if (negative) d.negate() else d
            }

        val displayString: String
            get() {
                var s = digits
                if (s.startsWith(".")) s = "0$s"
                if (s.isEmpty()) s = "0"
                return (if (negative) "-" else "") + groupDigits(s)
            }
    }

    private var entry: Entry? = null           // in-progress literal operand entry
    private var value: BigDecimal = BigDecimal.ZERO // displayed value when no entry
    private var negativeZero = false           // "±" on a zero display shows "-0"
    private var accumulator: BigDecimal = BigDecimal.ZERO
    private var pendingOperator: CalcOperator? = null
    private var secondOperandStarted = false
    private var lastOperator: CalcOperator? = null
    private var lastOperand: BigDecimal? = null
    var isError = false
        private set

    val display: String
        get() {
            if (isError) return "Error"
            entry?.let { return it.displayString }
            if (value.signum() == 0 && negativeZero) return "-0"
            return format(value)
        }

    /** Pending-operator ring (design spec §4.9). */
    val ringOperator: CalcOperator?
        get() {
            if (isError || secondOperandStarted) return null
            return pendingOperator
        }

    /** AC/C label rule (design spec §4.8): C during operand entry, AC otherwise. */
    val showsAllClear: Boolean get() = entry == null

    fun press(key: CalcKey) {
        when (key) {
            CalcKey.CLEAR -> pressClear()
            CalcKey.EQUALS -> pressEquals()
            CalcKey.DOT -> pressDot()
            CalcKey.ADD -> pressOperator(CalcOperator.ADD)
            CalcKey.SUB -> pressOperator(CalcOperator.SUB)
            CalcKey.MUL -> pressOperator(CalcOperator.MUL)
            CalcKey.DIV -> pressOperator(CalcOperator.DIV)
            CalcKey.PCT -> pressPercent()
            CalcKey.SIGN -> pressSign()
            else -> key.digitValue?.let { pressDigit(it) }
        }
    }

    private fun pressDigit(digit: Int) {
        if (isError) reset()
        if (entry == null) {
            entry = Entry(negative = negativeZero)
            negativeZero = false
            if (pendingOperator != null) secondOperandStarted = true
        }
        val e = entry ?: return
        if (e.digits == "0") {
            if (digit == 0) return          // "0" stays "0"
            e.digits = digit.toString()     // leading zero collapses
        } else {
            if (e.digitCount >= ENTRY_DIGIT_CAP) return // entry cap: display ignores the key
            e.digits += digit.toString()
        }
        value = e.value
    }

    private fun pressDot() {
        if (isError) return
        if (entry == null) {
            val e = Entry(digits = ".", negative = negativeZero)
            negativeZero = false
            entry = e
            if (pendingOperator != null) secondOperandStarted = true
            value = e.value
            return
        }
        val e = entry ?: return
        if (e.hasDot || e.digitCount >= ENTRY_DIGIT_CAP) return
        e.digits += "."
    }

    private fun pressSign() {
        if (isError) return
        val e = entry
        if (e != null) {
            e.negative = !e.negative
            value = e.value
        } else if (value.signum() == 0) {
            negativeZero = !negativeZero
        } else {
            value = value.negate()
        }
    }

    private fun pressOperator(op: CalcOperator) {
        if (isError) return
        negativeZero = false
        if (pendingOperator != null && secondOperandStarted) {
            // Chaining: evaluate the pending operation, carry the result.
            if (!evaluatePending()) return
            accumulator = value
        } else if (pendingOperator == null) {
            accumulator = value
        }
        // Operator replacement: a second operator before second-operand entry
        // simply replaces the pending one.
        pendingOperator = op
        entry = null
        secondOperandStarted = false
    }

    private fun pressPercent() {
        if (isError) return
        negativeZero = false
        when (pendingOperator) {
            CalcOperator.ADD, CalcOperator.SUB -> {
                // Binary % in an additive context: percentage OF the first operand.
                value = accumulator.multiply(value.movePointLeft(2), MATH)
                entry = null
                secondOperandStarted = true
            }
            CalcOperator.MUL, CalcOperator.DIV -> {
                value = value.movePointLeft(2)
                entry = null
                secondOperandStarted = true
            }
            null -> {
                value = value.movePointLeft(2)
                entry = null
            }
        }
    }

    private fun pressEquals() {
        if (isError) return
        negativeZero = false
        val pending = pendingOperator
        if (pending != null) {
            val rhs = value // second operand if started; else the shown accumulator (2 × = → 4)
            lastOperator = pending
            lastOperand = rhs
            if (!evaluatePending()) return
            pendingOperator = null
            secondOperandStarted = false
        } else {
            val op = lastOperator
            val operand = lastOperand
            if (op != null && operand != null) {
                // Repeated =: repeat the last binary operation with the last RHS.
                apply(op, value, operand)
                entry = null
            } else {
                // "=" with no pending operation: no-op on the display, but the
                // in-progress entry commits as a value (AC label returns; the
                // next digit starts a fresh entry).
                entry = null
            }
        }
    }

    private fun pressClear() {
        if (entry != null) {
            // C: clear the current entry only.
            entry = null
            value = BigDecimal.ZERO
            negativeZero = false
            if (pendingOperator != null) secondOperandStarted = false
        } else {
            reset()
        }
    }

    private fun reset() {
        entry = null
        value = BigDecimal.ZERO
        negativeZero = false
        accumulator = BigDecimal.ZERO
        pendingOperator = null
        secondOperandStarted = false
        lastOperator = null
        lastOperand = null
        isError = false
    }

    private fun evaluatePending(): Boolean {
        val op = pendingOperator ?: return true
        return apply(op, accumulator, value)
    }

    private fun apply(op: CalcOperator, lhs: BigDecimal, rhs: BigDecimal): Boolean {
        if (op == CalcOperator.DIV && rhs.signum() == 0) {
            enterErrorState()
            return false
        }
        value = when (op) {
            CalcOperator.ADD -> lhs.add(rhs, MATH)
            CalcOperator.SUB -> lhs.subtract(rhs, MATH)
            CalcOperator.MUL -> lhs.multiply(rhs, MATH)
            CalcOperator.DIV -> lhs.divide(rhs, MATH)
        }
        entry = null
        return true
    }

    private fun enterErrorState() {
        isError = true
        entry = null
        value = BigDecimal.ZERO
        accumulator = BigDecimal.ZERO
        pendingOperator = null
        secondOperandStarted = false
        lastOperator = null
        lastOperand = null
    }

    companion object {
        const val ENTRY_DIGIT_CAP = 9
        const val SIGNIFICANT_DIGITS = 9
        private val MATH = MathContext(34, RoundingMode.HALF_UP) // Decimal128-class precision

        /** floor(log10(|v|)) for v != 0. */
        fun magnitudeOrder(v: BigDecimal): Int {
            val stripped = v.stripTrailingZeros()
            return stripped.precision() - stripped.scale() - 1
        }

        fun roundToSignificant(v: BigDecimal, digits: Int = SIGNIFICANT_DIGITS): BigDecimal {
            if (v.signum() == 0) return BigDecimal.ZERO
            return v.round(MathContext(digits, RoundingMode.HALF_UP))
        }

        fun format(v: BigDecimal): String {
            val rounded = roundToSignificant(v)
            if (rounded.signum() == 0) return "0"
            val order = magnitudeOrder(rounded)
            // Scientific when the integer part would exceed 9 digits, or the
            // value is too small to show any significant digit in plain decimal.
            if (order >= 9 || order < -8) return scientificString(rounded, order)
            return plainString(rounded)
        }

        private fun plainString(v: BigDecimal): String {
            var s = v.stripTrailingZeros().toPlainString()
            var negative = false
            if (s.startsWith("-")) {
                negative = true
                s = s.substring(1)
            }
            return (if (negative) "-" else "") + groupDigits(s)
        }

        private fun scientificString(v: BigDecimal, order: Int): String {
            val mantissa = v.movePointLeft(order)
                .round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
            var m = mantissa.stripTrailingZeros().toPlainString()
            var exponent = order
            var negative = false
            if (m.startsWith("-")) {
                negative = true
                m = m.substring(1)
            }
            if (BigDecimal(m).abs() >= BigDecimal.TEN) {
                m = BigDecimal(m).movePointLeft(1).stripTrailingZeros().toPlainString()
                exponent += 1
            }
            return (if (negative) "-" else "") + m + "e" + exponent
        }

        /**
         * Inserts en-US grouping separators into the integer part of a plain
         * unsigned numeric string (may contain a fraction part).
         */
        fun groupDigits(s: String): String {
            val dotIndex = s.indexOf('.')
            val intPart = if (dotIndex >= 0) s.substring(0, dotIndex) else s
            if (intPart.length <= 3) return s
            val grouped = StringBuilder()
            for ((i, ch) in intPart.withIndex()) {
                if (i > 0 && (intPart.length - i) % 3 == 0) grouped.append(',')
                grouped.append(ch)
            }
            return if (dotIndex >= 0) grouped.toString() + s.substring(dotIndex) else grouped.toString()
        }
    }
}
