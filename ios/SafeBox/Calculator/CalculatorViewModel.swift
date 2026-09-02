import Foundation
import Observation

/// Feeds every key to two consumers of one key stream: the calculator engine
/// (display — the disguise never flinches) and the passcode recorder.
@MainActor
@Observable
final class CalculatorViewModel {
    private(set) var engine = CalculatorEngine()
    private var recorder = PasscodeRecorder()

    /// (sequence excluding the trailing =, overflowed)
    let onCommit: @MainActor ([CalcKey], Bool) -> Void
    /// Called on every key press (change flow uses it to clear error captions).
    var onKeyPress: (@MainActor () -> Void)?

    init(onCommit: @escaping @MainActor ([CalcKey], Bool) -> Void) {
        self.onCommit = onCommit
    }

    var display: String { engine.display }
    var ringOperator: CalcOperator? { engine.ringOperator }
    var clearLabel: String { engine.showsAllClear ? "AC" : "C" }

    func press(_ key: CalcKey) {
        onKeyPress?()
        // Engine first, always — the arithmetic result renders regardless.
        engine.press(key)
        switch key {
        case .equals:
            let commit = recorder.takeCommit()
            onCommit(commit.keys, commit.overflowed)
        default:
            recorder.record(key) // CLEAR clears the buffer and overflow flag
        }
    }
}
