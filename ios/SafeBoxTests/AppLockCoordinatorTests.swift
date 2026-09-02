import Foundation
import Testing
@testable import SafeBox

final class FakeClock: @unchecked Sendable {
    var now: TimeInterval = 1_000
}

@MainActor
struct AppLockCoordinatorTests {
    private func makeSetupCoordinator() -> (AppLockCoordinator, InMemoryPasscodeStore) {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store)
        return (coordinator, store)
    }

    private func makeLockedCoordinator(clock: FakeClock = FakeClock())
    async -> (AppLockCoordinator, SpyPasscodeStore) {
        let store = SpyPasscodeStore()
        await store.seed([.d1, .d2, .add, .d3, .d4])
        let coordinator = AppLockCoordinator(passcodeStore: store, uptime: { clock.now })
        return (coordinator, store)
    }

    // MARK: - First-run setup

    @Test func freshInstallStartsInSetup() {
        let (coordinator, _) = makeSetupCoordinator()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.banner?.primary == LockCopy.setupEntryBanner)
        #expect(coordinator.banner?.secondary == LockCopy.setupEntryHint)
    }

    @Test func setupTooShortStaysInEntry() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .d3], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.banner?.primary == LockCopy.setupTooShort)
        #expect(!store.hasPasscode)
    }

    @Test func setupOverflowStaysInEntry() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(sequence: Array(repeating: .d7, count: 32), overflowed: true)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.banner?.primary == LockCopy.setupTooLong)
    }

    @Test func setupValidGoesToConfirm() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.confirm(pending: [.d1, .d2, .d3, .d4])))
        #expect(coordinator.banner?.primary == LockCopy.setupConfirmBanner)
        #expect(coordinator.banner?.secondary == nil)
    }

    @Test func trivialSequenceShowsSoftWarning() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d7, .d7, .d7, .d7], overflowed: false)
        #expect(coordinator.banner?.secondary == LockCopy.setupTrivialWarning)
    }

    @Test func confirmMismatchReturnsToEntry() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        await coordinator.commit(sequence: [.d1, .d2, .d3, .d5], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.banner?.primary == LockCopy.setupMismatch)
        #expect(!store.hasPasscode)
    }

    @Test func confirmMatchStoresAndUnlocks() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        #expect(coordinator.state == .unlocked)
        #expect(coordinator.showNoRecoveryNotice)
        #expect(store.stored == [.d1, .d2, .add, .d3, .d4])
        #expect(coordinator.banner == nil)
    }

    @Test func backgroundingMidSetupDiscardsBuffers() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        let epochBefore = coordinator.calculatorEpoch
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.calculatorEpoch > epochBefore)
    }

    // MARK: - Locked

    @Test func existingPasscodeBootsLocked() async {
        let (coordinator, _) = await makeLockedCoordinator()
        #expect(coordinator.state == .locked)
        #expect(coordinator.banner == nil)
    }

    @Test func correctSequenceUnlocks() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        #expect(coordinator.state == .unlocked)
    }

    @Test func wrongSequenceStaysLockedSilently() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d9, .d9, .d9, .d9], overflowed: false)
        #expect(coordinator.state == .locked)
        #expect(coordinator.banner == nil)
    }

    @Test func subMinimumCommitSkipsCompare() async {
        let (coordinator, store) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2], overflowed: false)
        #expect(coordinator.state == .locked)
        #expect(store.matchesCallCount == 0) // no Keychain read, no KDF
    }

    @Test func overflowedCommitNeverMatches() async {
        let (coordinator, store) = await makeLockedCoordinator()
        // Even the correct sequence with the overflow flag set is skipped.
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: true)
        #expect(coordinator.state == .locked)
        #expect(store.matchesCallCount == 0)
    }

    // MARK: - Re-lock model

    @Test func backgroundingWhileUnlockedLocksImmediately() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }

    @Test func manualLockClearsCalculator() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        let epochBefore = coordinator.calculatorEpoch
        coordinator.lock()
        #expect(coordinator.state == .locked)
        #expect(coordinator.calculatorEpoch > epochBefore)
    }

    @Test func pickerSuppressionWithinCapStaysUnlocked() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .unlocked)
        clock.now += 60
        coordinator.sceneDidBecomeActive()
        #expect(coordinator.state == .unlocked)
    }

    @Test func pickerSuppressionBeyondCapLocks() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground()
        clock.now += AppLockCoordinator.suppressionCap + 1
        coordinator.sceneDidBecomeActive()
        #expect(coordinator.state == .locked)
    }

    @Test func monotonicInconsistencyFailsClosed() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground()
        clock.now -= 100 // clock went backwards (reboot) → lock
        coordinator.sceneDidBecomeActive()
        #expect(coordinator.state == .locked)
    }

    @Test func backgroundingWithoutSuppressionLocksEvenWithPickerFlagCleared() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.systemUIDidDismiss()
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }

    // MARK: - Onboarding gate + erase-everything reset

    @Test func freshInstallShowsOnboardingUntilCompleted() {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store, onboardingComplete: false)
        #expect(coordinator.showOnboarding)
        coordinator.completeOnboarding()
        #expect(!coordinator.showOnboarding)
    }

    @Test func onboardingNeverShowsOncePasscodeExists() async {
        // Even with the flag unset (upgrade path), an existing vault means no explainer.
        let store = SpyPasscodeStore()
        await store.seed([.d1, .d2, .add, .d3, .d4])
        let coordinator = AppLockCoordinator(passcodeStore: store, onboardingComplete: false)
        #expect(!coordinator.showOnboarding)
    }

    @Test func completedOnboardingStaysHiddenDuringSetup() {
        let (coordinator, _) = makeSetupCoordinator() // onboardingComplete defaults to true
        #expect(!coordinator.showOnboarding)
    }

    @Test func resetReturnsToFirstRunState() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        let epochBefore = coordinator.calculatorEpoch
        coordinator.reset()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.banner?.primary == LockCopy.setupEntryBanner)
        #expect(coordinator.showOnboarding)
        #expect(!coordinator.showNoRecoveryNotice)
        #expect(coordinator.calculatorEpoch > epochBefore)
        #expect(!coordinator.systemUIInFlight)
    }

    @Test func resetClearsPickerSuppression() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(sequence: [.d1, .d2, .add, .d3, .d4], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground() // suppressed backgrounding pending
        coordinator.reset()
        // A vault set up after the reset must not inherit the old suppression
        // window: the next backgrounding locks immediately.
        await coordinator.commit(sequence: [.d5, .d6, .add, .d7], overflowed: false)
        await coordinator.commit(sequence: [.d5, .d6, .add, .d7], overflowed: false)
        #expect(coordinator.state == .unlocked)
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }
}
