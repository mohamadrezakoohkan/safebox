import Foundation

enum CalcOperator: Sendable, Equatable {
    case add, sub, mul, div
}

/// Pure basic-calculator state machine matching iOS Calculator basic-mode
/// semantics as pinned by the idea plan §2.1 (immediate execution, operator
/// replacement, unary/binary %, repeated =, ± edge cases, Error on ÷0) and the
/// display-formatting rules of the disguise design spec §4 (9 significant
/// digits, 9-digit entry cap, grouping separators, scientific presentation).
struct CalculatorEngine: Sendable, Equatable {
    static let entryDigitCap = 9
    static let significantDigits = 9

    // MARK: - State

    private struct Entry: Sendable, Equatable {
        var digits: String = ""   // typed characters: digits and at most one "."
        var negative = false

        var hasDot: Bool { digits.contains(".") }
        var digitCount: Int { digits.reduce(0) { $1.isNumber ? $0 + 1 : $0 } }

        var value: Decimal {
            var s = digits
            if s.hasPrefix(".") { s = "0" + s }
            if s.isEmpty || s == "." { s = "0" }
            let d = Decimal(string: s, locale: Locale(identifier: "en_US_POSIX")) ?? 0
            return negative ? -d : d
        }

        var displayString: String {
            var s = digits
            if s.hasPrefix(".") { s = "0" + s }
            if s.isEmpty { s = "0" }
            return (negative ? "-" : "") + CalculatorEngine.groupDigits(s)
        }
    }

    private var entry: Entry?             // in-progress literal operand entry
    private var value: Decimal = 0        // displayed value when no entry in progress
    private var negativeZero = false      // "±" on a zero display shows "-0"
    private var accumulator: Decimal = 0  // first operand while an operator is pending
    private var pendingOperator: CalcOperator?
    private var secondOperandStarted = false
    private var lastOperator: CalcOperator?
    private var lastOperand: Decimal?
    private(set) var isError = false

    // MARK: - Outputs

    var display: String {
        if isError { return "Error" }
        if let entry { return entry.displayString }
        if value == 0 && negativeZero { return "-0" }
        return Self.format(value)
    }

    /// The operator whose key shows the pending ring (design spec §4.9):
    /// ring while an operator is pending and second-operand entry hasn't begun.
    var ringOperator: CalcOperator? {
        guard !isError, let pendingOperator, !secondOperandStarted else { return nil }
        return pendingOperator
    }

    /// AC/C label rule (design spec §4.8): C during operand entry, AC otherwise.
    var showsAllClear: Bool { entry == nil }

    // MARK: - Input

    mutating func press(_ key: CalcKey) {
        switch key {
        case .clear: pressClear()
        case .equals: pressEquals()
        case .dot: pressDot()
        case .add: pressOperator(.add)
        case .sub: pressOperator(.sub)
        case .mul: pressOperator(.mul)
        case .div: pressOperator(.div)
        case .pct: pressPercent()
        case .sign: pressSign()
        default:
            if let digit = key.digitValue { pressDigit(digit) }
        }
    }

    private mutating func pressDigit(_ digit: Int) {
        if isError { reset() }
        if entry == nil {
            var e = Entry()
            e.negative = negativeZero
            negativeZero = false
            entry = e
            if pendingOperator != nil { secondOperandStarted = true }
        }
        guard var e = entry else { return }
        if e.digits == "0" {
            if digit == 0 { return }        // "0" stays "0"
            e.digits = String(digit)        // leading zero collapses
        } else {
            guard e.digitCount < Self.entryDigitCap else { return } // entry cap: key ignored for display
            e.digits.append(String(digit))
        }
        entry = e
        value = e.value
    }

    private mutating func pressDot() {
        if isError { return }
        if entry == nil {
            var e = Entry()
            e.negative = negativeZero
            negativeZero = false
            e.digits = "."
            entry = e
            if pendingOperator != nil { secondOperandStarted = true }
            value = e.value
            return
        }
        guard var e = entry, !e.hasDot, e.digitCount < Self.entryDigitCap else { return }
        e.digits.append(".")
        entry = e
    }

    private mutating func pressSign() {
        if isError { return }
        if var e = entry {
            e.negative.toggle()
            entry = e
            value = e.value
        } else if value == 0 {
            negativeZero.toggle()
        } else {
            value = -value
        }
    }

    private mutating func pressOperator(_ op: CalcOperator) {
        if isError { return }
        negativeZero = false
        if pendingOperator != nil && secondOperandStarted {
            // Chaining: evaluate the pending operation, carry the result.
            guard evaluatePending() else { return }
            accumulator = value
        } else if pendingOperator == nil {
            accumulator = value
        }
        // Operator replacement: a second operator before second-operand entry
        // simply replaces the pending one.
        pendingOperator = op
        entry = nil
        secondOperandStarted = false
    }

    private mutating func pressPercent() {
        if isError { return }
        negativeZero = false
        switch pendingOperator {
        case .add, .sub:
            // Binary % in an additive context: percentage OF the first operand.
            value = Self.multiply(accumulator, Self.divideBy100(value))
            entry = nil
            secondOperandStarted = true
        case .mul, .div:
            value = Self.divideBy100(value)
            entry = nil
            secondOperandStarted = true
        case nil:
            value = Self.divideBy100(value)
            entry = nil
        }
    }

    private mutating func pressEquals() {
        if isError { return }
        negativeZero = false
        if pendingOperator != nil {
            let op = pendingOperator!
            let rhs = value // second operand if started; otherwise the shown accumulator (2 × = → 4)
            lastOperator = op
            lastOperand = rhs
            guard evaluatePending() else { return }
            pendingOperator = nil
            secondOperandStarted = false
        } else if let op = lastOperator, let operand = lastOperand {
            // Repeated =: repeat the last binary operation with the last RHS.
            apply(op, lhs: value, rhs: operand)
            entry = nil
        } else {
            // "=" with no pending operation: no-op on the display, but the
            // in-progress entry commits as a value (AC label returns; the next
            // digit starts a fresh entry).
            entry = nil
        }
    }

    private mutating func pressClear() {
        if entry != nil {
            // C: clear the current entry only.
            entry = nil
            value = 0
            negativeZero = false
            if pendingOperator != nil { secondOperandStarted = false }
        } else {
            reset()
        }
    }

    private mutating func reset() {
        entry = nil
        value = 0
        negativeZero = false
        accumulator = 0
        pendingOperator = nil
        secondOperandStarted = false
        lastOperator = nil
        lastOperand = nil
        isError = false
    }

    // MARK: - Arithmetic

    @discardableResult
    private mutating func evaluatePending() -> Bool {
        guard let op = pendingOperator else { return true }
        return apply(op, lhs: accumulator, rhs: value)
    }

    @discardableResult
    private mutating func apply(_ op: CalcOperator, lhs: Decimal, rhs: Decimal) -> Bool {
        if op == .div && rhs == 0 {
            enterErrorState()
            return false
        }
        var result: Decimal
        switch op {
        case .add: result = lhs + rhs
        case .sub: result = lhs - rhs
        case .mul: result = lhs * rhs
        case .div: result = lhs / rhs
        }
        if result.isNaN {
            enterErrorState()
            return false
        }
        value = result
        entry = nil
        return true
    }

    private mutating func enterErrorState() {
        isError = true
        entry = nil
        value = 0
        accumulator = 0
        pendingOperator = nil
        secondOperandStarted = false
        lastOperator = nil
        lastOperand = nil
    }

    private static func divideBy100(_ v: Decimal) -> Decimal {
        var input = v
        var result = Decimal()
        NSDecimalMultiplyByPowerOf10(&result, &input, -2, .plain)
        return result
    }

    private static func multiply(_ a: Decimal, _ b: Decimal) -> Decimal {
        a * b
    }

    // MARK: - Formatting (design spec §4)

    /// floor(log10(|v|)) for v != 0, derived from the plain decimal description.
    static func magnitudeOrder(_ v: Decimal) -> Int {
        let s = "\(abs(v))"
        if let dotIndex = s.firstIndex(of: ".") {
            let intPart = s[s.startIndex..<dotIndex]
            if intPart != "0" && !intPart.isEmpty { return intPart.count - 1 }
            var i = 0
            for ch in s[s.index(after: dotIndex)...] {
                if ch != "0" { return -(i + 1) }
                i += 1
            }
            return 0
        }
        return s.count - 1
    }

    static func roundToSignificant(_ v: Decimal, digits: Int = significantDigits) -> Decimal {
        guard v != 0 else { return 0 }
        let order = magnitudeOrder(v)
        var input = v
        var mantissa = Decimal()
        NSDecimalMultiplyByPowerOf10(&mantissa, &input, Int16(-order), .plain)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &mantissa, digits - 1, .plain)
        var back = Decimal()
        NSDecimalMultiplyByPowerOf10(&back, &rounded, Int16(order), .plain)
        return back
    }

    static func format(_ v: Decimal) -> String {
        let rounded = roundToSignificant(v)
        if rounded == 0 { return "0" }
        let order = magnitudeOrder(rounded)
        // Scientific when the integer part would exceed 9 digits, or the value
        // is too small to show any significant digit in plain decimal.
        if order >= 9 || order < -8 {
            return scientificString(rounded, order: order)
        }
        return plainString(rounded)
    }

    private static func plainString(_ v: Decimal) -> String {
        var s = "\(v)"
        var negative = false
        if s.hasPrefix("-") {
            negative = true
            s.removeFirst()
        }
        if s.contains(".") {
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        return (negative ? "-" : "") + groupDigits(s)
    }

    private static func scientificString(_ v: Decimal, order: Int) -> String {
        var input = v
        var mantissa = Decimal()
        NSDecimalMultiplyByPowerOf10(&mantissa, &input, Int16(-order), .plain)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &mantissa, significantDigits - 1, .plain)
        var exponent = order
        if abs(rounded) >= 10 {
            var shifted = Decimal()
            NSDecimalMultiplyByPowerOf10(&shifted, &rounded, -1, .plain)
            rounded = shifted
            exponent += 1
        }
        var m = "\(rounded)"
        var negative = false
        if m.hasPrefix("-") {
            negative = true
            m.removeFirst()
        }
        if m.contains(".") {
            while m.hasSuffix("0") { m.removeLast() }
            if m.hasSuffix(".") { m.removeLast() }
        }
        return (negative ? "-" : "") + m + "e" + String(exponent)
    }

    /// Inserts en-US grouping separators into the integer part of a plain
    /// unsigned numeric string (may contain a fraction part).
    static func groupDigits(_ s: String) -> String {
        let parts = s.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
        let intPart = String(parts[0])
        guard intPart.count > 3 else { return s }
        var grouped = ""
        for (i, ch) in intPart.enumerated() {
            if i > 0 && (intPart.count - i) % 3 == 0 { grouped.append(",") }
            grouped.append(ch)
        }
        if parts.count > 1 {
            return grouped + "." + parts[1]
        }
        return s.hasSuffix(".") ? grouped + "." : grouped
    }
}
