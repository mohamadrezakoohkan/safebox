import Foundation
import Testing
@testable import SafeBox

/// Registry order, the fail-closed default, and the alphabet-drift checks that
/// keep each face's descriptor honest (decisions §9 steps 4 and 5).
@MainActor
struct DisguiseRegistryTests {
    private let registry = DisguiseRegistry()

    @Test func theRegistryIsOrderedAndAppendOnly() {
        #expect(registry.all.map(\.id) == ["calculator", "numpad", "pattern"])
        #expect(DisguiseRegistry.defaultId == "calculator")
        #expect(registry.defaultDisguise.id == "calculator")
    }

    @Test func resolveFailsClosedToTheCalculator() {
        #expect(registry.resolve(id: "numpad").id == "numpad")
        #expect(registry.resolve(id: nil).id == "calculator")
        #expect(registry.resolve(id: "").id == "calculator")
        #expect(registry.resolve(id: "unit-converter").id == "calculator")
    }

    @Test func onlyTheCalculatorIsCovert() {
        #expect(registry.resolve(id: "calculator").isCovert)
        #expect(!registry.resolve(id: "numpad").isCovert)
        #expect(!registry.resolve(id: "pattern").isCovert)
    }

    @Test func onlyThePatternDisclosesTheScreenReaderLimit() {
        #expect(registry.resolve(id: "calculator").a11yNote == nil)
        #expect(registry.resolve(id: "numpad").a11yNote == nil)
        #expect(registry.resolve(id: "pattern").a11yNote == "Not usable with a screen reader")
    }

    // MARK: - Cover identities (§9a)

    @Test func everyFaceCarriesItsCoverIdentity() {
        #expect(registry.resolve(id: "calculator").coverIdentityName == "Calculator+")
        #expect(registry.resolve(id: "numpad").coverIdentityName == "Notepad+")
        #expect(registry.resolve(id: "pattern").coverIdentityName == "Gallery+")
        // Only the calculator is the shipped (primary) icon.
        #expect(registry.resolve(id: "calculator").alternateIconName == nil)
        #expect(registry.resolve(id: "numpad").alternateIconName == "AppIconNotepad")
        #expect(registry.resolve(id: "pattern").alternateIconName == "AppIconGallery")
    }

    @Test func alternateIconNamesAreUniqueAndNonEmpty() {
        let names = registry.all.compactMap(\.alternateIconName)
        // Exactly one face — the calculator — declines an alternate.
        #expect(names.count == registry.all.count - 1)
        #expect(Set(names).count == names.count, "two faces share an alternate icon")
        #expect(!names.contains { $0.isEmpty })
        #expect(registry.all.filter { $0.alternateIconName == nil }.map(\.id) == ["calculator"])
    }

    /// The build settings half of §9a. `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`
    /// is what puts these into Info.plist; without it `setAlternateIconName`
    /// fails silently at runtime and nothing else in the suite would notice.
    @Test func theAlternatesAreRegisteredInTheBuiltInfoPlist() throws {
        let icons = try #require(Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any])
        let alternates = try #require(icons["CFBundleAlternateIcons"] as? [String: Any])
        for disguise in registry.all {
            guard let name = disguise.alternateIconName else { continue }
            #expect(alternates[name] != nil, "\(name) is missing from CFBundleAlternateIcons")
        }
    }

    // MARK: - Alphabet hygiene

    @Test func everyAlphabetIsWellFormed() {
        for disguise in registry.all {
            let tokens = disguise.alphabet.tokens
            #expect(disguise.alphabet.tokenSetId == disguise.id)
            #expect(disguise.alphabet.alphabetVersion == 1)
            #expect(Set(tokens).count == tokens.count, "duplicate token in \(disguise.id)")
            #expect(!tokens.contains { $0.contains("|") }, "separator in a \(disguise.id) token")
            #expect(!tokens.contains { $0.isEmpty })
        }
    }

    @Test func alphabetSizesArePinned() {
        #expect(registry.resolve(id: "calculator").alphabet.tokens.count == 17)
        #expect(registry.resolve(id: "numpad").alphabet.tokens.count == 10)
        #expect(registry.resolve(id: "pattern").alphabet.tokens.count == 9)
    }

    // MARK: - Alphabet drift: what the surface can emit == what it declares

    @Test func theCalculatorSurfaceEmitsExactlyItsAlphabet() {
        let emittable = CalculatorKeypad.emittableTokens
        #expect(emittable.count == 17)
        #expect(Set(emittable) == Set(registry.resolve(id: "calculator").alphabet.tokens))
        // `=` and `AC`/`C` are signals, never tokens.
        #expect(!emittable.contains("EQUALS"))
        #expect(!emittable.contains("CLEAR"))
    }

    @Test func theNumpadSurfaceEmitsExactlyItsAlphabet() {
        let emittable = NumpadKeypad.emittableTokens
        #expect(emittable.count == 10)
        #expect(Set(emittable) == Set(registry.resolve(id: "numpad").alphabet.tokens))
    }

    @Test func thePatternSurfaceEmitsExactlyItsAlphabet() {
        #expect(PatternGeometry.tokens == ["N0", "N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8"])
        #expect(Set(PatternGeometry.tokens) == Set(registry.resolve(id: "pattern").alphabet.tokens))
    }

    @Test func theNumpadDeliberatelySharesTheCalculatorsDigitIds() {
        // Decisions §2.3: the overlap gives the fail-closed calculator face a
        // chance to still accept a digits-only PIN.
        let numpad = Set(registry.resolve(id: "numpad").alphabet.tokens)
        let calculator = Set(registry.resolve(id: "calculator").alphabet.tokens)
        #expect(numpad.isSubset(of: calculator))
    }

    // MARK: - Serialization

    @Test func serializationContract() {
        #expect(AlphabetDescriptor.canonical(["D7", "ADD", "D3", "DOT"]) == "D7|ADD|D3|DOT")
        #expect(AlphabetDescriptor.canonical(["D1", "D2", "ADD", "D3", "D4"]) == "D1|D2|ADD|D3|D4")
        #expect(AlphabetDescriptor.canonical(["N0", "N4", "N8"]) == "N0|N4|N8")
        #expect(AlphabetDescriptor.canonical([]).isEmpty)
    }

    /// The whole point of the iteration-3 refactor: a calculator.v1 enrollment
    /// hashes exactly the same bytes as iteration 1 did.
    @Test func theCalculatorSerializationIsByteIdenticalToIterationOne() {
        let keys: [CalcKey] = [.d1, .d2, .add, .d3, .d4]
        #expect(AlphabetDescriptor.canonical(keys.map(\.rawValue)) == "D1|D2|ADD|D3|D4")
    }
}
