import Foundation

/// The keypad as data, so the alphabet-drift test can assert that the set of
/// tokens the surface can actually emit equals the descriptor's 17 (§9 step 4).
/// The layout renders straight from this table.
enum CalculatorKeypad {
    struct Key: Sendable, Equatable {
        let label: String
        let a11yLabel: String
        let kind: CalcKeyKind
        let key: CalcKey
        /// Column span; only the zero key is 2.
        var span: Int = 1
    }

    /// Top to bottom, left to right. `AC`/`C` shares one cell — its label is
    /// state-dependent, so the renderer substitutes it.
    static let rows: [[Key]] = [
        [
            Key(label: "AC", a11yLabel: "all clear", kind: .fn, key: .clear),
            Key(label: "±", a11yLabel: "plus minus", kind: .fn, key: .sign),
            Key(label: "%", a11yLabel: "percent", kind: .fn, key: .pct),
            Key(label: "÷", a11yLabel: "divide", kind: .op, key: .div),
        ],
        [
            Key(label: "7", a11yLabel: "seven", kind: .digit, key: .d7),
            Key(label: "8", a11yLabel: "eight", kind: .digit, key: .d8),
            Key(label: "9", a11yLabel: "nine", kind: .digit, key: .d9),
            Key(label: "×", a11yLabel: "multiply", kind: .op, key: .mul),
        ],
        [
            Key(label: "4", a11yLabel: "four", kind: .digit, key: .d4),
            Key(label: "5", a11yLabel: "five", kind: .digit, key: .d5),
            Key(label: "6", a11yLabel: "six", kind: .digit, key: .d6),
            Key(label: "−", a11yLabel: "minus", kind: .op, key: .sub),
        ],
        [
            Key(label: "1", a11yLabel: "one", kind: .digit, key: .d1),
            Key(label: "2", a11yLabel: "two", kind: .digit, key: .d2),
            Key(label: "3", a11yLabel: "three", kind: .digit, key: .d3),
            Key(label: "+", a11yLabel: "plus", kind: .op, key: .add),
        ],
        [
            Key(label: "0", a11yLabel: "zero", kind: .digit, key: .d0, span: 2),
            Key(label: ".", a11yLabel: "decimal point", kind: .digit, key: .dot),
            Key(label: "=", a11yLabel: "equals", kind: .op, key: .equals),
        ],
    ]

    /// Every token ID this surface can emit. `=` commits and `AC`/`C` clears —
    /// signals, never tokens.
    static var emittableTokens: [String] {
        rows.flatMap { $0 }.filter { $0.key.isPasscodeKey }.map(\.key.rawValue)
    }
}
