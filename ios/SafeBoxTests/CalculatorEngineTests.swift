import Testing
@testable import SafeBox

/// The idea plan's shared input→display sequence table is the cross-platform
/// acceptance contract — the engine must reproduce it exactly.
struct CalculatorEngineTests {
    private func run(_ tokens: [CalcKey]) -> String {
        var engine = CalculatorEngine()
        for token in tokens {
            engine.press(token)
        }
        return engine.display
    }

    private func keys(_ spec: String) -> [CalcKey] {
        spec.split(separator: " ").map { token in
            switch token {
            case "0": return .d0
            case "1": return .d1
            case "2": return .d2
            case "3": return .d3
            case "4": return .d4
            case "5": return .d5
            case "6": return .d6
            case "7": return .d7
            case "8": return .d8
            case "9": return .d9
            case ".": return .dot
            case "+": return .add
            case "-", "−": return .sub
            case "×", "*": return .mul
            case "÷", "/": return .div
            case "%": return .pct
            case "±": return .sign
            case "=": return .equals
            case "C", "AC": return .clear
            default: fatalError("unknown token \(token)")
            }
        }
    }

    @Test func sharedTableRow1() { #expect(run(keys("2 + 3 =")) == "5") }
    @Test func sharedTableRow2() { #expect(run(keys("2 + 3 = =")) == "8") }
    @Test func sharedTableRow3() { #expect(run(keys("2 + × 3 =")) == "6") }
    @Test func sharedTableRow4() { #expect(run(keys("7 + 7 % =")) == "7.49") }
    @Test func sharedTableRow5() { #expect(run(keys("7 %")) == "0.07") }
    @Test func sharedTableRow6() { #expect(run(keys("8 × 5 0 % =")) == "4") }
    @Test func sharedTableRow7() { #expect(run(keys("2 × =")) == "4") }
    @Test func sharedTableRow8() { #expect(run(keys(". 5 + . 5 =")) == "1") }
    @Test func sharedTableRow9() { #expect(run(keys("8 ÷ 0 =")) == "Error") }
    @Test func sharedTableRow10() { #expect(run(keys("0 . 1 + 0 . 2 =")) == "0.3") }
    @Test func sharedTableRow11() { #expect(run(keys("=")) == "0") }
    @Test func sharedTableRow12() { #expect(run(keys("±")) == "-0") }
    @Test func sharedTableRow13() { #expect(run(keys("± ±")) == "0") }
    @Test func sharedTableRow14() { #expect(run(keys("± ± + % =")) == "0") }
    @Test func sharedTableRow15() { #expect(run(keys("+ + =")) == "0") }
    @Test func sharedTableRow16() { #expect(run(keys("% =")) == "0") }
    @Test func sharedTableRow17() { #expect(run(keys("1 2 + 3 4 =")) == "46") }

    // MARK: - Semantics beyond the table

    @Test func immediateExecutionNoPrecedence() {
        #expect(run(keys("2 + 3 × 4 =")) == "20")
    }

    @Test func chainingShowsIntermediateResult() {
        #expect(run(keys("2 + 3 ×")) == "5")
    }

    @Test func repeatedEqualsAfterNewEntry() {
        // 2+3=5, then typing 10 and = applies the last op: 10+3=13.
        #expect(run(keys("2 + 3 = 1 0 =")) == "13")
    }

    @Test func doubleDotIgnored() {
        #expect(run(keys("1 . 5 . 2 =")) == "1.52")
    }

    @Test func signDuringEntry() {
        #expect(run(keys("5 ±")) == "-5")
        #expect(run(keys("± 5")) == "-5")
    }

    @Test func errorRecoveryByDigit() {
        #expect(run(keys("8 ÷ 0 = 5")) == "5")
    }

    @Test func errorRecoveryByClear() {
        #expect(run(keys("8 ÷ 0 = AC")) == "0")
    }

    @Test func clearEntryVsAllClear() {
        // C clears only the current entry: 5 + 3, C, 4 = → 9.
        #expect(run(keys("5 + 3 C 4 =")) == "9")
    }

    @Test func equalsWithoutOperationCommitsEntry() {
        var engine = CalculatorEngine()
        for key in keys("9 9 9 9 =") { engine.press(key) }
        #expect(engine.display == "9,999")
        #expect(engine.showsAllClear) // entry committed; AC label returns
        engine.press(.d5)
        #expect(engine.display == "5") // next digit starts a fresh entry
    }

    @Test func acClearLabelStateMachine() {
        var engine = CalculatorEngine()
        #expect(engine.showsAllClear)                 // cleared state → AC
        engine.press(.d5)
        #expect(!engine.showsAllClear)                // during entry → C
        engine.press(.clear)
        #expect(engine.showsAllClear)                 // entry cleared → AC
    }

    @Test func pendingOperatorRing() {
        var engine = CalculatorEngine()
        engine.press(.d2)
        #expect(engine.ringOperator == nil)
        engine.press(.add)
        #expect(engine.ringOperator == .add)          // ring appears
        engine.press(.mul)
        #expect(engine.ringOperator == .mul)          // hops on replacement
        engine.press(.d3)
        #expect(engine.ringOperator == nil)           // clears on 2nd-operand entry
    }

    // MARK: - Display formatting

    @Test func thousandsSeparators() {
        #expect(run(keys("1 2 3 4")) == "1,234")
        #expect(run(keys("1 0 0 0 × 1 0 0 0 =")) == "1,000,000")
    }

    @Test func entryCapAtNineDigits() {
        // The 10th digit is ignored for display entry.
        #expect(run(keys("1 2 3 4 5 6 7 8 9 0")) == "123,456,789")
    }

    @Test func scientificOverflow() {
        // 999999999 × 9 = 8,999,999,991 → must NOT render 9e9.
        #expect(run(keys("9 9 9 9 9 9 9 9 9 × 9 =")) == "8.99999999e9")
    }

    @Test func scientificAtBillion() {
        #expect(run(keys("9 9 9 9 9 9 9 9 9 + 1 =")) == "1e9")
    }

    @Test func tinyValuesGoScientific() {
        // 0.00001 × 0.0001 = 1e-9 (below the 1e-8 plain-decimal floor).
        #expect(run(keys(". 0 0 0 0 1 × . 0 0 0 1 =")) == "1e-9")
    }

    @Test func smallPlainDecimalStaysPlain() {
        // 1 ÷ 100000000 = 0.00000001 (1e-8) renders plain.
        #expect(run(keys("1 ÷ 1 0 0 0 0 0 0 0 0 =")) == "0.00000001")
    }

    @Test func nineSignificantDigitRounding() {
        // 2 ÷ 3 = 0.666666667 (9 significant digits, rounded).
        #expect(run(keys("2 ÷ 3 =")) == "0.666666667")
    }

    @Test func trailingZerosStripped() {
        #expect(run(keys("0 . 5 0 + 0 =")) == "0.5")
    }

    @Test func decimalEntryEcho() {
        #expect(run(keys(".")) == "0.")
        #expect(run(keys("1 2 .")) == "12.")
    }

    @Test func degenerateOperatorStreams() {
        // Symbol-heavy passcodes feed operator streams; never look broken.
        #expect(run(keys("+ × ÷ − =")) == "0")
        #expect(run(keys("% % % =")) == "0")
        #expect(run(keys("± % + ± % =")) == "0")
    }
}
