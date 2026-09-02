import Foundation

/// Records every allowed key pressed since the last AC/=. Capped at 32 keys:
/// the 33rd key sets an overflow flag, and an overflowed buffer never matches
/// on commit. CLEAR resets both the buffer and the flag.
struct PasscodeRecorder: Sendable, Equatable {
    static let maxKeys = 32

    private(set) var buffer: [CalcKey] = []
    private(set) var overflowed = false

    mutating func record(_ key: CalcKey) {
        guard key.isPasscodeKey else {
            if key == .clear { clear() }
            return
        }
        if buffer.count >= Self.maxKeys {
            overflowed = true
        } else {
            buffer.append(key)
        }
    }

    /// Returns the buffered sequence (excluding the trailing =) and the
    /// overflow flag, then resets both.
    mutating func takeCommit() -> (keys: [CalcKey], overflowed: Bool) {
        defer { clear() }
        return (buffer, overflowed)
    }

    mutating func clear() {
        buffer.removeAll()
        overflowed = false
    }
}
