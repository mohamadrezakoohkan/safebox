import Foundation

/// Versioned envelope stored in the Keychain; allows raising iterations or
/// swapping algorithms later without a breaking change.
struct PasscodeBlob: Codable, Sendable, Equatable {
    var algo: String
    var version: Int
    var iterations: Int
    var salt: Data
    var hash: Data
}

@MainActor
protocol PasscodeStore: AnyObject {
    var hasPasscode: Bool { get }
    func set(sequence: [CalcKey]) async throws
    func matches(sequence: [CalcKey]) async -> Bool
    func clear()
}

@MainActor
final class KeychainPasscodeStore: PasscodeStore {
    static let service = "com.calcplus.calculator"
    static let account = "passcode.v1"
    static let defaultIterations = 600_000

    private let keychain: any KeychainProviding
    private let iterations: Int

    init(keychain: any KeychainProviding = KeychainWrapper(), iterations: Int = KeychainPasscodeStore.defaultIterations) {
        self.keychain = keychain
        self.iterations = iterations
    }

    var hasPasscode: Bool {
        keychain.data(service: Self.service, account: Self.account) != nil
    }

    func set(sequence: [CalcKey]) async throws {
        let salt = PBKDF2.randomSalt()
        let password = Data(CalcKey.serialize(sequence).utf8)
        let iters = iterations
        // KDF runs off the main actor.
        let hash = await Task.detached(priority: .userInitiated) {
            PBKDF2.derive(password: password, salt: salt, iterations: iters)
        }.value
        let blob = PasscodeBlob(algo: "PBKDF2-HMAC-SHA256", version: 1, iterations: iters, salt: salt, hash: hash)
        let encoded = try JSONEncoder().encode(blob)
        try keychain.set(encoded, service: Self.service, account: Self.account)
    }

    func matches(sequence: [CalcKey]) async -> Bool {
        guard let data = keychain.data(service: Self.service, account: Self.account),
              let blob = try? JSONDecoder().decode(PasscodeBlob.self, from: data) else {
            return false
        }
        let password = Data(CalcKey.serialize(sequence).utf8)
        let derived = await Task.detached(priority: .userInitiated) {
            PBKDF2.derive(password: password, salt: blob.salt, iterations: blob.iterations)
        }.value
        return PBKDF2.constantTimeEquals(derived, blob.hash)
    }

    func clear() {
        keychain.deleteAll(service: Self.service)
    }
}
