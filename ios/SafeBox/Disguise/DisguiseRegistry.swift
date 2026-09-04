import Foundation

/// The compiled-in, ordered, **append-only** face list (decisions §1.6). A
/// shipped face is never removed: an install enrolled on it would otherwise be
/// unable to render its own lock screen.
@MainActor
struct DisguiseRegistry {
    /// Fail-closed target for every unresolvable id (§3). Deliberately
    /// `nonisolated` so the envelope decoder — which runs off the main actor —
    /// can name it.
    nonisolated static let defaultId = "calculator"

    let all: [any DisguiseProviding]

    init(all: [any DisguiseProviding] = [CalculatorDisguise(), NumpadDisguise(), PatternDisguise()]) {
        self.all = all
    }

    /// The default face. The registry is never empty (the initializer's default
    /// argument is the shipped list), but a caller-supplied empty list still
    /// resolves rather than trapping.
    var defaultDisguise: any DisguiseProviding {
        all.first { $0.id == Self.defaultId } ?? all[0]
    }

    /// Fail closed: missing, unknown, or undecodable ids all render the
    /// calculator. Never an error surface, never a non-disguise surface.
    func resolve(id: String?) -> any DisguiseProviding {
        guard let id, let match = all.first(where: { $0.id == id }) else {
            return defaultDisguise
        }
        return match
    }
}
