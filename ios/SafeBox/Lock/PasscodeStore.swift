import Foundation

/// Versioned envelope stored in the Keychain (decisions §3).
///
/// v1 carried `{algo, version, iterations, salt, hash}`. v2 adds the alphabet
/// of enrollment and the face to show while locked. **Absence of the three new
/// fields means calculator.v1, by definition** — that is the whole migration:
/// the calculator's serialization is byte-identical, so salt and hash carry
/// over untouched and no upgrade ever forces a re-enrollment.
struct PasscodeEnvelope: Codable, Sendable, Equatable {
    /// Written by this build.
    static let currentVersion = 2
    /// Anything above this is rejected outright (skeleton §3.4 forward
    /// obligation) — the compatibility boundary is defined behavior, not
    /// decoder tolerance.
    static let maxAcceptedVersion = 2

    var algo: String
    var version: Int
    var iterations: Int
    var salt: Data
    var hash: Data
    var tokenSetId: String
    var alphabetVersion: Int
    var activeDisguiseId: String

    /// True when this value came from a v1 blob. Never encoded; it exists so
    /// the store knows to rewrite once.
    var decodedFromLegacy = false

    enum CodingKeys: String, CodingKey {
        case algo, version, iterations, salt, hash, tokenSetId, alphabetVersion, activeDisguiseId
    }

    init(algo: String,
         version: Int,
         iterations: Int,
         salt: Data,
         hash: Data,
         tokenSetId: String,
         alphabetVersion: Int,
         activeDisguiseId: String,
         decodedFromLegacy: Bool = false) {
        self.algo = algo
        self.version = version
        self.iterations = iterations
        self.salt = salt
        self.hash = hash
        self.tokenSetId = tokenSetId
        self.alphabetVersion = alphabetVersion
        self.activeDisguiseId = activeDisguiseId
        self.decodedFromLegacy = decodedFromLegacy
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        algo = try container.decode(String.self, forKey: .algo)
        version = try container.decode(Int.self, forKey: .version)
        guard version <= Self.maxAcceptedVersion else {
            throw DecodingError.dataCorruptedError(
                forKey: .version, in: container,
                debugDescription: "envelope version above the accepted ceiling"
            )
        }
        iterations = try container.decode(Int.self, forKey: .iterations)
        salt = try container.decode(Data.self, forKey: .salt)
        hash = try container.decode(Data.self, forKey: .hash)
        let storedTokenSetId = try container.decodeIfPresent(String.self, forKey: .tokenSetId)
        tokenSetId = storedTokenSetId ?? DisguiseRegistry.defaultId
        alphabetVersion = try container.decodeIfPresent(Int.self, forKey: .alphabetVersion) ?? 1
        activeDisguiseId = try container.decodeIfPresent(String.self, forKey: .activeDisguiseId)
            ?? DisguiseRegistry.defaultId
        decodedFromLegacy = version < Self.currentVersion || storedTokenSetId == nil
    }
}

@MainActor
protocol PasscodeStore: AnyObject {
    /// True whenever a stored item exists — even one this build cannot decode.
    /// Setup can never be reached over an existing vault.
    var hasPasscode: Bool { get }
    /// `nil` when absent or unreadable; a v1 envelope reads `"calculator"`.
    var activeDisguiseId: String? { get }
    func set(tokens: [String], alphabet: AlphabetDescriptor, activeDisguiseId: String) async throws
    func matches(tokens: [String]) async -> Bool
    func clear()
}

@MainActor
final class KeychainPasscodeStore: PasscodeStore {
    static let service = "com.calcplus.calculator"
    /// The account names a *location*, not a schema version. Renaming it on the
    /// v2 bump would orphan every install, so it stays `passcode.v1` forever.
    static let account = "passcode.v1"
    static let defaultIterations = 600_000

    private let keychain: any KeychainProviding
    private let iterations: Int

    init(keychain: any KeychainProviding = KeychainWrapper(),
         iterations: Int = KeychainPasscodeStore.defaultIterations) {
        self.keychain = keychain
        self.iterations = iterations
    }

    var hasPasscode: Bool {
        keychain.data(service: Self.service, account: Self.account) != nil
    }

    var activeDisguiseId: String? {
        readEnvelope()?.activeDisguiseId
    }

    func set(tokens: [String], alphabet: AlphabetDescriptor, activeDisguiseId: String) async throws {
        let salt = PBKDF2.randomSalt()
        let password = Data(AlphabetDescriptor.canonical(tokens).utf8)
        let iters = iterations
        // KDF runs off the main actor.
        let hash = await Task.detached(priority: .userInitiated) {
            PBKDF2.derive(password: password, salt: salt, iterations: iters)
        }.value
        let envelope = PasscodeEnvelope(
            algo: "PBKDF2-HMAC-SHA256",
            version: PasscodeEnvelope.currentVersion,
            iterations: iters,
            salt: salt,
            hash: hash,
            tokenSetId: alphabet.tokenSetId,
            alphabetVersion: alphabet.alphabetVersion,
            activeDisguiseId: activeDisguiseId
        )
        let encoded = try JSONEncoder().encode(envelope)
        try keychain.set(encoded, service: Self.service, account: Self.account)
    }

    func matches(tokens: [String]) async -> Bool {
        // Version- and set-invariant by rule: the `|`-join is the serialization
        // for every alphabet, so `tokenSetId` is deliberately NOT compared.
        guard let envelope = readEnvelope() else { return false }
        let password = Data(AlphabetDescriptor.canonical(tokens).utf8)
        let derived = await Task.detached(priority: .userInitiated) {
            PBKDF2.derive(password: password, salt: envelope.salt, iterations: envelope.iterations)
        }.value
        return PBKDF2.constantTimeEquals(derived, envelope.hash)
    }

    func clear() {
        keychain.deleteAll(service: Self.service)
    }

    // MARK: - Reading and the eager v1 → v2 rewrite

    /// Decodes the stored item. A legacy (v1) blob is interpreted as
    /// calculator.v1 and eagerly rewritten as v2 with **salt and hash copied
    /// byte for byte**. A rewrite failure is silent: the interpretation is
    /// returned either way and the v1 read path is never removed.
    private func readEnvelope() -> PasscodeEnvelope? {
        guard let data = keychain.data(service: Self.service, account: Self.account),
              let envelope = try? JSONDecoder().decode(PasscodeEnvelope.self, from: data) else {
            return nil
        }
        guard envelope.decodedFromLegacy else { return envelope }
        var upgraded = envelope
        upgraded.version = PasscodeEnvelope.currentVersion
        upgraded.decodedFromLegacy = false
        if let encoded = try? JSONEncoder().encode(upgraded) {
            try? keychain.set(encoded, service: Self.service, account: Self.account)
        }
        return upgraded
    }
}
