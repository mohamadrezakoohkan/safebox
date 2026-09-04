import Foundation
import Testing
@testable import SafeBox

@MainActor
struct PasscodeStoreTests {
    private let calculator = AlphabetDescriptor(tokenSetId: "calculator", alphabetVersion: 1,
                                                tokens: CalculatorKeypad.emittableTokens)

    private func makeStore(iterations: Int = 1_000) -> (KeychainPasscodeStore, FakeKeychain) {
        let keychain = FakeKeychain()
        return (KeychainPasscodeStore(keychain: keychain, iterations: iterations), keychain)
    }

    private func storedEnvelope(_ keychain: FakeKeychain) throws -> PasscodeEnvelope {
        let raw = keychain.data(service: KeychainPasscodeStore.service,
                                account: KeychainPasscodeStore.account)
        return try JSONDecoder().decode(PasscodeEnvelope.self, from: #require(raw))
    }

    /// A v1 blob exactly as iteration 1 wrote it: no `tokenSetId`,
    /// no `alphabetVersion`, no `activeDisguiseId`.
    private func seedLegacyBlob(_ keychain: FakeKeychain,
                                tokens: [String],
                                iterations: Int) throws -> (salt: Data, hash: Data) {
        let salt = PBKDF2.randomSalt()
        let hash = PBKDF2.derive(password: Data(AlphabetDescriptor.canonical(tokens).utf8),
                                 salt: salt, iterations: iterations)
        let legacy: [String: Any] = [
            "algo": "PBKDF2-HMAC-SHA256",
            "version": 1,
            "iterations": iterations,
            "salt": salt.base64EncodedString(),
            "hash": hash.base64EncodedString(),
        ]
        let data = try JSONSerialization.data(withJSONObject: legacy)
        keychain.seed(data, service: KeychainPasscodeStore.service,
                      account: KeychainPasscodeStore.account)
        return (salt, hash)
    }

    // MARK: - KDF

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

    // MARK: - Round trips

    @Test func setThenMatch() async throws {
        let (store, _) = makeStore()
        #expect(!store.hasPasscode)
        try await store.set(tokens: ["D1", "D2", "ADD", "D3", "D4"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        #expect(store.hasPasscode)
        #expect(await store.matches(tokens: ["D1", "D2", "ADD", "D3", "D4"]))
        #expect(!(await store.matches(tokens: ["D1", "D2", "ADD", "D3"])))
        #expect(!(await store.matches(tokens: ["D1", "D2", "SUB", "D3", "D4"])))
    }

    @Test func orderSensitivity() async throws {
        let (store, _) = makeStore()
        try await store.set(tokens: ["D1", "D2"], alphabet: calculator, activeDisguiseId: "calculator")
        #expect(!(await store.matches(tokens: ["D2", "D1"])))
    }

    @Test func versionedEnvelopeRoundTrip() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        try await store.set(tokens: ["N0", "N4", "N8"],
                            alphabet: PatternDisguise().alphabet, activeDisguiseId: "pattern")
        let envelope = try storedEnvelope(keychain)
        #expect(envelope.algo == "PBKDF2-HMAC-SHA256")
        #expect(envelope.version == 2)
        #expect(envelope.iterations == 1_000)
        #expect(envelope.salt.count == 16)
        #expect(envelope.hash.count == 32)
        #expect(envelope.tokenSetId == "pattern")
        #expect(envelope.alphabetVersion == 1)
        #expect(envelope.activeDisguiseId == "pattern")
        #expect(store.activeDisguiseId == "pattern")
    }

    @Test func distinctSaltsPerSet() async throws {
        let (store, keychain) = makeStore()
        try await store.set(tokens: ["D1", "D1", "D1", "D1"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        let first = try storedEnvelope(keychain)
        try await store.set(tokens: ["D1", "D1", "D1", "D1"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        let second = try storedEnvelope(keychain)
        #expect(first.salt != second.salt)
    }

    @Test func matchingIsAlphabetInvariant() async throws {
        // The `|`-join is universal, so the same tokens verify regardless of
        // which alphabet is recorded. Only one envelope, one salt, ever exists.
        let (store, _) = makeStore()
        try await store.set(tokens: ["D1", "D2", "D3", "D4"],
                            alphabet: NumpadDisguise().alphabet, activeDisguiseId: "numpad")
        #expect(await store.matches(tokens: ["D1", "D2", "D3", "D4"]))
    }

    @Test func clearRemovesPasscode() async throws {
        let (store, keychain) = makeStore()
        try await store.set(tokens: ["D1", "D2", "D3", "D4"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        store.clear()
        #expect(!store.hasPasscode)
        #expect(store.activeDisguiseId == nil)
        #expect(keychain.isEmpty)
        #expect(!(await store.matches(tokens: ["D1", "D2", "D3", "D4"])))
    }

    /// One full-cost spot check at the production iteration count.
    @Test func fullCostDerivationSpotCheck() async throws {
        let (store, _) = makeStore(iterations: KeychainPasscodeStore.defaultIterations)
        try await store.set(tokens: ["D7", "ADD", "D7", "PCT"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        #expect(await store.matches(tokens: ["D7", "ADD", "D7", "PCT"]))
    }

    // MARK: - v1 → v2 migration (decisions §9 step 3)

    @Test func aLegacyBlobVerifiesTheSameCodeBeforeAndAfterTheRewrite() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        _ = try seedLegacyBlob(keychain, tokens: ["D1", "D2", "ADD", "D3", "D4"], iterations: 1_000)
        #expect(store.hasPasscode)
        // First read: verifies AND rewrites.
        #expect(await store.matches(tokens: ["D1", "D2", "ADD", "D3", "D4"]))
        #expect(try storedEnvelope(keychain).version == 2)
        // Second read, now against the rewritten v2 blob.
        #expect(await store.matches(tokens: ["D1", "D2", "ADD", "D3", "D4"]))
        #expect(!(await store.matches(tokens: ["D9", "D9", "D9", "D9"])))
    }

    @Test func theRewritePreservesSaltAndHashByteForByte() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        let original = try seedLegacyBlob(keychain, tokens: ["D5", "MUL", "D6"], iterations: 1_000)
        _ = store.activeDisguiseId // triggers the eager rewrite
        let upgraded = try storedEnvelope(keychain)
        #expect(upgraded.version == 2)
        #expect(upgraded.salt == original.salt)
        #expect(upgraded.hash == original.hash)
        #expect(upgraded.iterations == 1_000)
    }

    @Test func aLegacyBlobReadsAsCalculatorV1() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        _ = try seedLegacyBlob(keychain, tokens: ["D1", "D2", "D3", "D4"], iterations: 1_000)
        #expect(store.activeDisguiseId == "calculator")
        let upgraded = try storedEnvelope(keychain)
        #expect(upgraded.tokenSetId == "calculator")
        #expect(upgraded.alphabetVersion == 1)
        #expect(upgraded.activeDisguiseId == "calculator")
    }

    @Test func aFailedRewriteLeavesAVerifiableLegacyBlob() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        let original = try seedLegacyBlob(keychain, tokens: ["D1", "D2", "ADD", "D3", "D4"], iterations: 1_000)
        keychain.failNextSet = true
        // Silent failure: the v1 interpretation still holds.
        #expect(store.activeDisguiseId == "calculator")
        let stillLegacy = try storedEnvelope(keychain)
        #expect(stillLegacy.version == 1)
        #expect(stillLegacy.salt == original.salt)
        #expect(await store.matches(tokens: ["D1", "D2", "ADD", "D3", "D4"]))
        #expect(store.hasPasscode)
    }

    @Test func aVersionThreeEnvelopeIsRejectedButStillCountsAsAPasscode() async throws {
        let (store, keychain) = makeStore(iterations: 1_000)
        let future: [String: Any] = [
            "algo": "PBKDF2-HMAC-SHA256",
            "version": 3,
            "iterations": 1_000,
            "salt": PBKDF2.randomSalt().base64EncodedString(),
            "hash": Data(repeating: 7, count: 32).base64EncodedString(),
            "tokenSetId": "calculator",
            "alphabetVersion": 1,
            "activeDisguiseId": "calculator",
        ]
        keychain.seed(try JSONSerialization.data(withJSONObject: future),
                      service: KeychainPasscodeStore.service,
                      account: KeychainPasscodeStore.account)
        // Fail closed: unreadable means no match and no resolvable face…
        #expect(!(await store.matches(tokens: ["D1", "D2", "D3", "D4"])))
        #expect(store.activeDisguiseId == nil)
        // …but setup can never be reached over an existing vault.
        #expect(store.hasPasscode)
        // And the unknown envelope is left untouched.
        let raw = keychain.data(service: KeychainPasscodeStore.service,
                                account: KeychainPasscodeStore.account)
        #expect(raw != nil)
    }

    @Test func anUnresolvableFaceRendersTheCalculator() {
        let registry = DisguiseRegistry()
        #expect(registry.resolve(id: nil).id == "calculator")
        #expect(registry.resolve(id: "tip").id == "calculator")
    }

    // MARK: - Install sentinel

    @Test func installSentinelWipesKeychainOnFreshInstall() async throws {
        let keychain = FakeKeychain()
        let store = KeychainPasscodeStore(keychain: keychain, iterations: 1_000)
        try await store.set(tokens: ["D1", "D2", "D3", "D4"],
                            alphabet: calculator, activeDisguiseId: "calculator")

        let suiteName = "test.sentinel.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        // Sentinel absent (fresh install / reinstall): wipe Keychain items.
        InstallSentinel.checkAndWipeIfNeeded(defaults: defaults, passcodeStore: store)
        #expect(!store.hasPasscode)
        #expect(defaults.bool(forKey: InstallSentinel.key))

        // Sentinel present: nothing is wiped.
        try await store.set(tokens: ["D5", "D6", "D7", "D8"],
                            alphabet: calculator, activeDisguiseId: "calculator")
        InstallSentinel.checkAndWipeIfNeeded(defaults: defaults, passcodeStore: store)
        #expect(store.hasPasscode)
    }
}
