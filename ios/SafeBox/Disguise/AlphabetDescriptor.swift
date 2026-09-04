import Foundation

/// The versioned set of token IDs a face emits, plus the canonical
/// serialization that is the KDF's byte input (skeleton §3.2c, decisions §1.7).
///
/// Rules: token IDs are unique within an alphabet, never contain `|`, and once
/// shipped in a `tokenSetId` are never renamed or removed. `alphabetVersion` is
/// provenance metadata only — the serialization is version-invariant, which is
/// exactly why a calculator.v1 enrollment verifies byte-for-byte unchanged
/// after this refactor.
struct AlphabetDescriptor: Equatable, Sendable {
    let tokenSetId: String
    let alphabetVersion: Int
    let tokens: [String]

    /// The universal serialization: `|`-join of token IDs.
    /// `["D1", "D2", "ADD"] -> "D1|D2|ADD"`.
    static func canonical(_ tokens: [String]) -> String {
        tokens.joined(separator: "|")
    }
}
