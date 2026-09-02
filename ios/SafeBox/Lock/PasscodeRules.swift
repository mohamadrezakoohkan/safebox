import Foundation

/// Shared validation for first-run setup and the Settings change flow, so the
/// rules cannot drift apart.
enum PasscodeRules {
    static let minKeys = 4
    static let maxKeys = 32

    static func isValidLength(_ sequence: [CalcKey]) -> Bool {
        sequence.count >= minKeys && sequence.count <= maxKeys
    }

    /// Soft warning only, never a block: a single repeated key (e.g. 7777).
    static func isTrivial(_ sequence: [CalcKey]) -> Bool {
        guard !sequence.isEmpty else { return false }
        return Set(sequence).count == 1
    }
}
