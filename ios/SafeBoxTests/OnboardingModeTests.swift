import Foundation
import Testing
@testable import SafeBox

/// P5 revisit mode (decisions §5): the guide re-opened from Settings must never
/// touch first-run state. The decision is `OnboardingMode.recordsCompletion`
/// and the only gate to the persisted flag is
/// `OnboardingSentinel.recordCompletion(for:)`, exercised here against a
/// throwaway `UserDefaults` suite.
struct OnboardingModeTests {
    private func makeDefaults() -> (defaults: UserDefaults, suiteName: String) {
        let suiteName = "test.onboardingMode.\(UUID().uuidString)"
        return (UserDefaults(suiteName: suiteName)!, suiteName)
    }

    // MARK: - The decision

    @Test func onlyTheFirstRunRecordsCompletion() {
        #expect(OnboardingMode.firstRun.recordsCompletion)
        #expect(!OnboardingMode.revisit.recordsCompletion)
    }

    @Test func onlyARevisitKeepsTheTrailingButtonOnTheLastPage() {
        #expect(OnboardingMode.revisit.showsTrailingButtonOnLastPage)
        #expect(!OnboardingMode.firstRun.showsTrailingButtonOnLastPage)
    }

    // MARK: - Persisted flag

    @Test func revisitLeavesAnUnsetFlagUnset() {
        let (defaults, suiteName) = makeDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        OnboardingSentinel.recordCompletion(for: .revisit, defaults: defaults)

        #expect(!OnboardingSentinel.isComplete(defaults: defaults))
        // Not merely `false`: the key must not have been written at all.
        #expect(defaults.object(forKey: OnboardingSentinel.key) == nil)
    }

    @Test func revisitLeavesASetFlagSet() {
        let (defaults, suiteName) = makeDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        OnboardingSentinel.setComplete(defaults: defaults)

        OnboardingSentinel.recordCompletion(for: .revisit, defaults: defaults)

        #expect(OnboardingSentinel.isComplete(defaults: defaults))
    }

    @Test func firstRunRecordsCompletion() {
        let (defaults, suiteName) = makeDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        #expect(!OnboardingSentinel.isComplete(defaults: defaults))

        OnboardingSentinel.recordCompletion(for: .firstRun, defaults: defaults)

        #expect(OnboardingSentinel.isComplete(defaults: defaults))
    }

    @Test func resetStillClearsAFirstRunCompletion() {
        let (defaults, suiteName) = makeDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        OnboardingSentinel.recordCompletion(for: .firstRun, defaults: defaults)

        OnboardingSentinel.reset(defaults: defaults)

        #expect(!OnboardingSentinel.isComplete(defaults: defaults))
    }
}
