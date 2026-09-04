import Foundation
@testable import SafeBox

/// Dictionary-backed keychain — the real Keychain isn't available in plain
/// unit-test contexts without host-app entitlements.
final class FakeKeychain: KeychainProviding, @unchecked Sendable {
    private var storage: [String: Data] = [:]
    private let lock = NSLock()

    /// Makes the next `set(...)` throw, so the silent v1 → v2 rewrite failure
    /// path can be exercised (decisions §9 step 3c).
    var failNextSet = false

    private func key(_ service: String, _ account: String) -> String {
        service + "/" + account
    }

    func data(service: String, account: String) -> Data? {
        lock.withLock { storage[key(service, account)] }
    }

    func set(_ data: Data, service: String, account: String) throws {
        if failNextSet {
            failNextSet = false
            throw CocoaError(.fileWriteUnknown)
        }
        lock.withLock { storage[key(service, account)] = data }
    }

    /// Writes bypassing `failNextSet`, for seeding a test fixture.
    func seed(_ data: Data, service: String, account: String) {
        lock.withLock { storage[key(service, account)] = data }
    }

    func delete(service: String, account: String) {
        lock.withLock { storage[key(service, account)] = nil }
    }

    func deleteAll(service: String) {
        lock.withLock {
            for k in storage.keys where k.hasPrefix(service + "/") {
                storage[k] = nil
            }
        }
    }

    var isEmpty: Bool {
        lock.withLock { storage.isEmpty }
    }
}

/// Wraps a store and counts matches() calls, to prove that sub-minimum and
/// overflowed commits skip the compare entirely.
@MainActor
final class SpyPasscodeStore: PasscodeStore {
    private let wrapped: InMemoryPasscodeStore
    private(set) var matchesCallCount = 0
    private(set) var setCallCount = 0

    init() {
        wrapped = InMemoryPasscodeStore()
    }

    func seed(_ tokens: [String],
              alphabet: AlphabetDescriptor = CalculatorDisguise().alphabet,
              activeDisguiseId: String = "calculator") async {
        try? await wrapped.set(tokens: tokens, alphabet: alphabet, activeDisguiseId: activeDisguiseId)
    }

    var hasPasscode: Bool { wrapped.hasPasscode }
    var activeDisguiseId: String? { wrapped.activeDisguiseId }
    var stored: [String]? { wrapped.stored }
    var storedAlphabet: AlphabetDescriptor? { wrapped.storedAlphabet }
    var failNextSet: Bool {
        get { wrapped.failNextSet }
        set { wrapped.failNextSet = newValue }
    }

    func set(tokens: [String], alphabet: AlphabetDescriptor, activeDisguiseId: String) async throws {
        setCallCount += 1
        try await wrapped.set(tokens: tokens, alphabet: alphabet, activeDisguiseId: activeDisguiseId)
    }

    func matches(tokens: [String]) async -> Bool {
        matchesCallCount += 1
        return await wrapped.matches(tokens: tokens)
    }

    func clear() {
        wrapped.clear()
    }
}
