import Foundation
@testable import SafeBox

/// Dictionary-backed keychain — the real Keychain isn't available in plain
/// unit-test contexts without host-app entitlements.
final class FakeKeychain: KeychainProviding, @unchecked Sendable {
    private var storage: [String: Data] = [:]
    private let lock = NSLock()

    private func key(_ service: String, _ account: String) -> String {
        service + "/" + account
    }

    func data(service: String, account: String) -> Data? {
        lock.withLock { storage[key(service, account)] }
    }

    func set(_ data: Data, service: String, account: String) throws {
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

    init(stored: [CalcKey]? = nil) {
        wrapped = InMemoryPasscodeStore()
        if let stored {
            Task { try? await wrapped.set(sequence: stored) }
        }
    }

    func seed(_ sequence: [CalcKey]) async {
        try? await wrapped.set(sequence: sequence)
    }

    var hasPasscode: Bool { wrapped.hasPasscode }

    func set(sequence: [CalcKey]) async throws {
        try await wrapped.set(sequence: sequence)
    }

    func matches(sequence: [CalcKey]) async -> Bool {
        matchesCallCount += 1
        return await wrapped.matches(sequence: sequence)
    }

    func clear() {
        wrapped.clear()
    }
}
