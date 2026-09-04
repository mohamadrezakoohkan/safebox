import Testing
@testable import SafeBox

/// Records every set, so "the manager did nothing" is an assertable outcome.
@MainActor
final class FakeAlternateIcons: AlternateIconControlling {
    var supportsAlternateIcons: Bool
    var currentAlternateIconName: String?
    private(set) var setCalls: [String?] = []

    init(supportsAlternateIcons: Bool = true, current: String? = nil) {
        self.supportsAlternateIcons = supportsAlternateIcons
        currentAlternateIconName = current
    }

    func setAlternateIconName(_ name: String?) {
        setCalls.append(name)
        currentAlternateIconName = name
    }
}

/// Cover identities, iOS half (decisions §9a).
@MainActor
struct AppIconManagerTests {
    @Test func itSetsTheFacesAlternateIcon() {
        let icons = FakeAlternateIcons()
        AppIconManager(icons: icons).apply(NumpadDisguise())
        #expect(icons.setCalls == ["AppIconNotepad"])
    }

    @Test func theCalculatorRestoresThePrimaryIcon() {
        let icons = FakeAlternateIcons(current: "AppIconGallery")
        AppIconManager(icons: icons).apply(CalculatorDisguise())
        #expect(icons.setCalls == [String?.none])
        #expect(icons.currentAlternateIconName == nil)
    }

    /// The whole reason the manager exists. iOS pops its system alert on every
    /// call that actually sets an icon — including one that sets the icon
    /// already in force — so a redundant apply would nag for nothing.
    @Test func itNoOpsWhenTheIconAlreadyMatches() {
        let icons = FakeAlternateIcons(current: "AppIconNotepad")
        AppIconManager(icons: icons).apply(NumpadDisguise())
        #expect(icons.setCalls.isEmpty)
    }

    @Test func itNoOpsWhenThePrimaryIconIsAlreadyInForce() {
        let icons = FakeAlternateIcons(current: nil)
        AppIconManager(icons: icons).apply(CalculatorDisguise())
        #expect(icons.setCalls.isEmpty)
    }

    @Test func repeatedAppliesSetOnlyOnce() {
        let icons = FakeAlternateIcons()
        let manager = AppIconManager(icons: icons)
        manager.apply(PatternDisguise())
        manager.apply(PatternDisguise())
        manager.apply(PatternDisguise())
        #expect(icons.setCalls == ["AppIconGallery"])
    }

    @Test func itDoesNothingWhereAlternateIconsAreUnsupported() {
        let icons = FakeAlternateIcons(supportsAlternateIcons: false)
        AppIconManager(icons: icons).apply(NumpadDisguise())
        #expect(icons.setCalls.isEmpty)
    }

    @Test func switchingBetweenTwoAlternatesSetsEachOnce() {
        let icons = FakeAlternateIcons()
        let manager = AppIconManager(icons: icons)
        manager.apply(NumpadDisguise())
        manager.apply(PatternDisguise())
        manager.apply(NumpadDisguise())
        #expect(icons.setCalls == ["AppIconNotepad", "AppIconGallery", "AppIconNotepad"])
    }
}
