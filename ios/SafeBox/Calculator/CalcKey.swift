import Foundation

/// Canonical calculator key identifiers shared with Android.
/// The raw values are the cross-platform serialization contract: a passcode is
/// the ordered key IDs joined with "|" (e.g. "D7|ADD|D3|DOT"), which is the
/// exact byte input to the KDF on both platforms.
enum CalcKey: String, CaseIterable, Codable, Sendable, Hashable {
    case d0 = "D0"
    case d1 = "D1"
    case d2 = "D2"
    case d3 = "D3"
    case d4 = "D4"
    case d5 = "D5"
    case d6 = "D6"
    case d7 = "D7"
    case d8 = "D8"
    case d9 = "D9"
    case dot = "DOT"
    case add = "ADD"
    case sub = "SUB"
    case mul = "MUL"
    case div = "DIV"
    case pct = "PCT"
    case sign = "SIGN"
    // Non-passcode keys: EQUALS commits, CLEAR is the "start over" gesture.
    case clear = "CLEAR"
    case equals = "EQUALS"

    /// Keys allowed inside a passcode sequence.
    var isPasscodeKey: Bool {
        switch self {
        case .clear, .equals: return false
        default: return true
        }
    }

    var digitValue: Int? {
        switch self {
        case .d0: return 0
        case .d1: return 1
        case .d2: return 2
        case .d3: return 3
        case .d4: return 4
        case .d5: return 5
        case .d6: return 6
        case .d7: return 7
        case .d8: return 8
        case .d9: return 9
        default: return nil
        }
    }

    static func serialize(_ sequence: [CalcKey]) -> String {
        sequence.map(\.rawValue).joined(separator: "|")
    }
}
