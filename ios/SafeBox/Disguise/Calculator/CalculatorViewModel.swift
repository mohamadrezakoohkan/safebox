import Foundation
import Observation

/// Feeds every key to two consumers of one key stream: the calculator engine
/// (display — the disguise never flinches) and the host's event seam.
///
/// The recorder is gone: buffering is host-owned (`DisguiseSurfaceHost`).
@MainActor
@Observable
final class CalculatorViewModel {
    private(set) var engine = CalculatorEngine()

    private let events: (DisguiseEvent) -> Void

    init(events: @escaping (DisguiseEvent) -> Void) {
        self.events = events
    }

    var display: String { engine.display }
    var ringOperator: CalcOperator? { engine.ringOperator }
    var clearLabel: String { engine.showsAllClear ? "AC" : "C" }

    func press(_ key: CalcKey) {
        // Engine first, always — the arithmetic result renders regardless.
        engine.press(key)
        switch key {
        case .equals: events(.commit)
        case .clear: events(.clear)
        default: events(.token(key.rawValue))
        }
    }
}
