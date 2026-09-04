import Foundation

/// Host-owned buffer of token IDs, identical for every face (decisions §1.2).
/// Generalized from iteration 1's `PasscodeRecorder` over `[CalcKey]`.
///
/// | Event | Buffer | Overflow flag |
/// |---|---|---|
/// | `token`, buffer < 32 | append | unchanged |
/// | `token`, buffer == 32 | unchanged | set |
/// | `removeLast`, empty | no-op | unchanged |
/// | `removeLast`, non-empty | pop | **unchanged (sticky)** |
/// | `clear` | empty | reset |
/// | `commit` | taken, then empty | reset |
///
/// The flag is sticky through `removeLast` on purpose: once a 33rd token has
/// been seen the entry is unrecoverable by backspacing, and the only ways out
/// are `clear` or a commit that fails as TOO_LONG.
///
/// In memory only. Never logged, never persisted.
struct TokenRecorder: Sendable, Equatable {
    static let maxTokens = 32

    private(set) var buffer: [String] = []
    private(set) var overflowed = false

    mutating func record(_ token: String) {
        if buffer.count >= Self.maxTokens {
            overflowed = true
        } else {
            buffer.append(token)
        }
    }

    mutating func removeLast() {
        guard !buffer.isEmpty else { return }
        buffer.removeLast()
        // overflowed deliberately untouched.
    }

    mutating func clear() {
        buffer.removeAll()
        overflowed = false
    }

    /// Returns the buffered tokens and the overflow flag, then resets both.
    mutating func takeCommit() -> (tokens: [String], overflowed: Bool) {
        defer { clear() }
        return (buffer, overflowed)
    }

    /// Applies one face event. Returns the commit payload for `.commit` and
    /// `nil` for every other event — so a caller can drive the whole stream
    /// through this single entry point.
    mutating func apply(_ event: DisguiseEvent) -> (tokens: [String], overflowed: Bool)? {
        switch event {
        case .token(let id):
            record(id)
            return nil
        case .removeLast:
            removeLast()
            return nil
        case .clear:
            clear()
            return nil
        case .commit:
            return takeCommit()
        }
    }
}
