import Foundation
import Testing
@testable import SafeBox

@MainActor
struct PasscodeStoreTests {
    private func makeStore(iterations: Int = 1_000) -> (KeychainPasscodeStore, FakeKeychain) {
        let keychain = FakeKeychain()
        return (KeychainPasscodeStore(keychain: keychain, iterations: iterations), keychain)
    }

    @Test func knownPBKDF2Vectors() {
        // PBKDF2-HMAC-SHA256(P="password", S="salt", c=1, dkLen=32)
        let one = PBKDF2.derive(password: Data("password".utf8), salt: Data("salt".utf8), iterations: 1)
        #expect(one.map { String(format: "%02x", $0) }.joined()
                == "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b")
        let two = PBKDF2.derive(password: Data("password".utf8), salt: Data("salt".utf8), iterations: 2)
        #expect(two.map { String(format: "%02x", $0) }.joined()
                == "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43")
    }

    @Test func constantTimeCompare() {
        let a = Data([1, 2, 3])
        #expect(PBKDF2.constantTimeEquals(a, Data([1, 2, 3])))
        #expect(!PBKDF2.constantTimeEquals(a, Data([1, 2, 4])))
        #expect(!PBKDF2.constantTimeEquals(a, Data([1, 2])))
    }

    @Test func setThenMatch() async throws {
        let (store, _) = makeStore()
        #expect(!store.hasPasscode)
        try await store.set(sequence: [.d1, .d2, .add, .d3, .d4])
        #expect(store.hasPasscode)
        #expect(await store.matches(sequence: [.d1, .d2, .add, .d3, .d4]))
        #expect(!(await store.matches(sequence: [.d1, .d2, .add, .d3])))
        #expect(!(await store.matches(sequence: [.d1, .d2, .sub, .d3, .d4])))
    }

    @Test func orderSensitivity() async throws {
        let (store, _) = makeStore()
        try await store.set(sequence: [.d1, .d2])
        #expect(!(await store.matches(sequence: [.d2, .d1])))
    }

    @Test func versionedBlobRoundTrip() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        try await store.set(sequence: [.d7, .pct])
        let raw = keychain.data(service: KeychainPasscodeStore.service,
                                account: KeychainPasscodeStore.account)
        let blob = try JSONDecoder().decode(PasscodeBlob.self, from: raw!)
        #expect(blob.algo == "PBKDF2-HMAC-SHA256")
        #expect(blob.version == 1)
        #expect(blob.iterations == 1_000)
        #expect(blob.salt.count == 16)
        #expect(blob.hash.count == 32)
    }

    @Test func distinctSaltsPerSet() async throws {
        let (store, keychain) = makeStore()
        try await store.set(sequence: [.d1, .d1, .d1, .d1])
        let first = try JSONDecoder().decode(PasscodeBlob.self, from: keychain.data(
            service: KeychainPasscodeStore.service, account: KeychainPasscodeStore.account)!)
        try await store.set(sequence: [.d1, .d1, .d1, .d1])
        let second = try JSONDecoder().decode(PasscodeBlob.self, from: keychain.data(
            service: KeychainPasscodeStore.service, account: KeychainPasscodeStore.account)!)
        #expect(first.salt != second.salt)
    }

    @Test func clearRemovesPasscode() async throws {
        let (store, keychain) = makeStore()
        try await store.set(sequence: [.d1, .d2, .d3, .d4])
        store.clear()
        #expect(!store.hasPasscode)
        #expect(keychain.isEmpty)
        #expect(!(await store.matches(sequence: [.d1, .d2, .d3, .d4])))
    }

    /// One full-cost spot check at the production iteration count.
    @Test func fullCostDerivationSpotCheck() async throws {
        let (store, _) = makeStore(iterations: KeychainPasscodeStore.defaultIterations)
        try await store.set(sequence: [.d7, .add, .d7, .pct])
        #expect(await store.matches(sequence: [.d7, .add, .d7, .pct]))
    }

    @Test func installSentinelWipesKeychainOnFreshInstall() async throws {
        let keychain = FakeKeychain()
        let store = KeychainPasscodeStore(keychain: keychain, iterations: 1_000)
        try await store.set(sequence: [.d1, .d2, .d3, .d4])

        let suiteName = "test.sentinel.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        // Sentinel absent (fresh install / reinstall): wipe Keychain items.
        InstallSentinel.checkAndWipeIfNeeded(defaults: defaults, passcodeStore: store)
        #expect(!store.hasPasscode)
        #expect(defaults.bool(forKey: InstallSentinel.key))

        // Sentinel present: nothing is wiped.
        try await store.set(sequence: [.d5, .d6, .d7, .d8])
        InstallSentinel.checkAndWipeIfNeeded(defaults: defaults, passcodeStore: store)
        #expect(store.hasPasscode)
    }
}
