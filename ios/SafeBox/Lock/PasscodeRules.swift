import Foundation

/// Shared validation for first-run setup and the Settings change flows, so the
/// rules cannot drift apart. Face-agnostic: the unit is a token ID.
enum PasscodeRules {
    static let minTokens = 4
    static let maxTokens = 32

    static func isValidLength(_ tokens: [String]) -> Bool {
        tokens.count >= minTokens && tokens.count <= maxTokens
    }

    /// Soft warning only, never a block: a single repeated token (e.g. 7777).
    static func isTrivial(_ tokens: [String]) -> Bool {
        guard !tokens.isEmpty else { return false }
        return Set(tokens).count == 1
    }
}
