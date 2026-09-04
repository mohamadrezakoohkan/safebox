import Foundation
import Testing
@testable import SafeBox

final class FakeClock: @unchecked Sendable {
    var now: TimeInterval = 1_000
}

@MainActor
struct AppLockCoordinatorTests {
    /// Every coordinator here drives a fake icon controller: a real one would
    /// reach `UIApplication.shared` and move the test host's home-screen icon.
    private func makeSetupCoordinator(icons: FakeAlternateIcons = FakeAlternateIcons())
    -> (AppLockCoordinator, InMemoryPasscodeStore) {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store,
                                             appIcons: AppIconManager(icons: icons))
        return (coordinator, store)
    }

    private func makeLockedCoordinator(clock: FakeClock = FakeClock(),
                                       disguiseId: String = "calculator",
                                       icons: FakeAlternateIcons = FakeAlternateIcons())
    async -> (AppLockCoordinator, SpyPasscodeStore) {
        let store = SpyPasscodeStore()
        await store.seed(["D1", "D2", "ADD", "D3", "D4"], activeDisguiseId: disguiseId)
        let coordinator = AppLockCoordinator(passcodeStore: store,
                                             appIcons: AppIconManager(icons: icons),
                                             uptime: { clock.now })
        return (coordinator, store)
    }

    // MARK: - First-run setup

    @Test func freshInstallStartsInSetup() {
        let (coordinator, _) = makeSetupCoordinator()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.caption?.primary == .promptNewSetup)
        #expect(coordinator.caption?.secondary == .strengthHint)
        #expect(coordinator.surfaceMode == .captureNew)
    }

    @Test func setupTooShortStaysInEntry() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "D3"], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.caption?.primary == .tooShort)
        #expect(!store.hasPasscode)
    }

    @Test func setupOverflowStaysInEntry() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(tokens: Array(repeating: "D7", count: 32), overflowed: true)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.caption?.primary == .tooLong)
    }

    @Test func setupValidGoesToConfirm() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.confirm(pending: ["D1", "D2", "D3", "D4"])))
        #expect(coordinator.caption?.primary == .promptConfirmSetup)
        #expect(coordinator.caption?.secondary == nil)
        #expect(coordinator.surfaceMode == .confirmNew)
    }

    @Test func trivialSequenceShowsSoftWarning() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D7", "D7", "D7", "D7"], overflowed: false)
        #expect(coordinator.caption?.secondary == .trivialWarning)
    }

    @Test func confirmMismatchReturnsToEntry() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D5"], overflowed: false)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.caption?.primary == .mismatch)
        #expect(!store.hasPasscode)
    }

    @Test func confirmMatchStoresAndUnlocks() async {
        let (coordinator, store) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        #expect(coordinator.state == .unlocked)
        #expect(coordinator.showNoRecoveryNotice)
        #expect(store.stored == ["D1", "D2", "ADD", "D3", "D4"])
        #expect(coordinator.caption == nil)
    }

    @Test func backgroundingMidSetupDiscardsBuffers() async {
        let (coordinator, _) = makeSetupCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        let epochBefore = coordinator.disguiseEpoch
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.disguiseEpoch > epochBefore)
    }

    // MARK: - Locked

    @Test func existingPasscodeBootsLocked() async {
        let (coordinator, _) = await makeLockedCoordinator()
        #expect(coordinator.state == .locked)
        #expect(coordinator.caption == nil)
        #expect(coordinator.surfaceMode == .disguise)
    }

    @Test func correctSequenceUnlocks() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        #expect(coordinator.state == .unlocked)
    }

    @Test func wrongSequenceStaysLockedSilently() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        #expect(coordinator.state == .locked)
        #expect(coordinator.caption == nil)
    }

    @Test func subMinimumCommitSkipsCompare() async {
        let (coordinator, store) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D1", "D2"], overflowed: false)
        #expect(coordinator.state == .locked)
        #expect(store.matchesCallCount == 0) // no Keychain read, no KDF
    }

    @Test func overflowedCommitNeverMatches() async {
        let (coordinator, store) = await makeLockedCoordinator()
        // Even the correct sequence with the overflow flag set is skipped.
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: true)
        #expect(coordinator.state == .locked)
        #expect(store.matchesCallCount == 0)
    }

    // MARK: - The §1.1 pulse matrix, in `disguise` mode

    @Test func covertFaceIsNeverPulsedInDisguiseMode() async {
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "calculator")
        await coordinator.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        await coordinator.commit(tokens: ["D1"], overflowed: false)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: true)
        #expect(coordinator.failedAttemptCount == 0)
    }

    @Test func overtFaceIsPulsedOnEveryNonAcceptedCommit() async {
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "numpad")
        await coordinator.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        #expect(coordinator.failedAttemptCount == 1)
        await coordinator.commit(tokens: ["D1"], overflowed: false) // too short
        #expect(coordinator.failedAttemptCount == 2)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: true)
        #expect(coordinator.failedAttemptCount == 3)
    }

    @Test func overtShortAndOverflowedCommitsStillSkipTheKDF() async {
        let (coordinator, store) = await makeLockedCoordinator(disguiseId: "pattern")
        await coordinator.commit(tokens: ["N1"], overflowed: false)
        await coordinator.commit(tokens: ["N1", "N2", "N3", "N4"], overflowed: true)
        #expect(store.matchesCallCount == 0)
        #expect(coordinator.failedAttemptCount == 2)
    }

    @Test func aSuccessfulUnlockResetsThePulse() async {
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "numpad")
        await coordinator.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        #expect(coordinator.failedAttemptCount == 1)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        #expect(coordinator.state == .unlocked)
        #expect(coordinator.failedAttemptCount == 0)
    }

    @Test func captureModesNeverPulse() async {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()))
        coordinator.pendingDisguiseId = "numpad" // overt
        await coordinator.commit(tokens: ["D1"], overflowed: false)             // too short
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: true) // too long
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await coordinator.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false) // mismatch
        #expect(coordinator.failedAttemptCount == 0)
    }

    // MARK: - Face resolution (§4, §E)

    @Test func theEnrolledFaceIsResolvedAtInit() async {
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "pattern")
        #expect(coordinator.activeDisguise.id == "pattern")
        #expect(coordinator.surfaceDisguise.id == "pattern")
    }

    @Test func anUnknownEnrolledFaceFallsBackToTheCalculator() async {
        let store = SpyPasscodeStore()
        await store.seed(["D1", "D2", "D3", "D4"], activeDisguiseId: "tip-calculator")
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()))
        #expect(coordinator.activeDisguise.id == "calculator")
        #expect(coordinator.surfaceMode == .disguise)
    }

    @Test func lockingDoesNotReResolveTheFace() async {
        let (coordinator, store) = await makeLockedCoordinator(disguiseId: "numpad")
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        // A device-locked Keychain reads as absent; the face must not flip.
        store.clear()
        coordinator.lock()
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.activeDisguise.id == "numpad")
    }

    @Test func setupRendersThePendingFace() {
        let (coordinator, _) = makeSetupCoordinator()
        #expect(coordinator.surfaceDisguise.id == "calculator")
        coordinator.pendingDisguiseId = "pattern"
        #expect(coordinator.surfaceDisguise.id == "pattern")
    }

    @Test func aFaceIdentityChangeRebuildsTheSurface() {
        let (coordinator, _) = makeSetupCoordinator()
        let before = coordinator.surfaceIdentity
        coordinator.pendingDisguiseId = "numpad"
        #expect(coordinator.surfaceIdentity != before)
    }

    @Test func aPhaseChangeWithinSetupKeepsTheSurface() async {
        let (coordinator, _) = makeSetupCoordinator()
        let before = coordinator.surfaceIdentity
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(coordinator.surfaceMode == .confirmNew)
        #expect(coordinator.surfaceIdentity == before)
    }

    @Test func setupStoresTheChosenFacesAlphabetAndId() async {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()))
        coordinator.completeOnboarding(selectedDisguiseId: "pattern")
        await coordinator.commit(tokens: ["N0", "N1", "N2", "N5"], overflowed: false)
        await coordinator.commit(tokens: ["N0", "N1", "N2", "N5"], overflowed: false)
        #expect(coordinator.state == .unlocked)
        #expect(store.storedDisguiseId == "pattern")
        #expect(store.storedAlphabet?.tokenSetId == "pattern")
        #expect(coordinator.activeDisguise.id == "pattern")
    }

    @Test func reloadPicksUpASwitchedFace() async {
        let (coordinator, store) = await makeLockedCoordinator(disguiseId: "calculator")
        await store.seed(["D1", "D2", "D3", "D4"],
                         alphabet: NumpadDisguise().alphabet, activeDisguiseId: "numpad")
        let epochBefore = coordinator.disguiseEpoch
        coordinator.reloadActiveDisguise()
        #expect(coordinator.activeDisguise.id == "numpad")
        #expect(coordinator.disguiseEpoch > epochBefore)
    }

    // MARK: - Cover identities (§9a)

    @Test func finishingSetupAppliesTheChosenFacesIcon() async {
        let icons = FakeAlternateIcons()
        let (coordinator, _) = makeSetupCoordinator(icons: icons)
        coordinator.completeOnboarding(selectedDisguiseId: "pattern")
        await coordinator.commit(tokens: ["N0", "N1", "N2", "N3"], overflowed: false)
        #expect(icons.setCalls.isEmpty) // still only the confirm step
        await coordinator.commit(tokens: ["N0", "N1", "N2", "N3"], overflowed: false)
        #expect(icons.setCalls == ["AppIconGallery"])
    }

    /// §9a: never before the envelope write. A failed write must leave the icon
    /// agreeing with the code that is still enrolled.
    @Test func aFailedFirstWriteLeavesTheIconAlone() async {
        let icons = FakeAlternateIcons()
        let store = InMemoryPasscodeStore()
        store.failNextSet = true
        let coordinator = AppLockCoordinator(passcodeStore: store,
                                             appIcons: AppIconManager(icons: icons))
        coordinator.completeOnboarding(selectedDisguiseId: "numpad")
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(icons.setCalls.isEmpty)
    }

    @Test func aSwitchCommitMovesTheIconToTheNewFace() async {
        let icons = FakeAlternateIcons()
        let (coordinator, store) = await makeLockedCoordinator(disguiseId: "calculator", icons: icons)
        await store.seed(["D1", "D2", "D3", "D4"],
                         alphabet: NumpadDisguise().alphabet, activeDisguiseId: "numpad")
        coordinator.reloadActiveDisguise()
        #expect(icons.setCalls == ["AppIconNotepad"])
    }

    @Test func eraseEverythingRestoresTheCalculatorIcon() async {
        let icons = FakeAlternateIcons(current: "AppIconNotepad")
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "numpad", icons: icons)
        coordinator.reset()
        #expect(icons.setCalls == [String?.none])
        #expect(coordinator.activeDisguise.id == "calculator")
    }

    /// Locking, backgrounding and a plain unlock must never touch the icon —
    /// each set pops iOS's system alert.
    @Test func theOrdinaryLockCycleNeverTouchesTheIcon() async {
        let icons = FakeAlternateIcons(current: "AppIconGallery")
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "pattern", icons: icons)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.sceneDidEnterBackground()
        coordinator.sceneDidBecomeActive()
        #expect(icons.setCalls.isEmpty)
    }

    // MARK: - Setup-complete hook (§4)

    @Test func theSetupHookFiresOnlyWithTheFirstEnvelope() async {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()))
        var completions = 0
        coordinator.onSetupComplete = { completions += 1 }

        coordinator.completeOnboarding(selectedDisguiseId: "numpad")
        #expect(completions == 0) // finishing the guide writes nothing

        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(completions == 0) // still only pending
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(completions == 1)
    }

    @Test func aFailedFirstWriteDoesNotFireTheSetupHook() async {
        let store = InMemoryPasscodeStore()
        store.failNextSet = true
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()))
        var completions = 0
        coordinator.onSetupComplete = { completions += 1 }
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await coordinator.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(completions == 0)
        #expect(coordinator.state == .firstRunSetup(.enterNew))
    }

    // MARK: - Re-lock model

    @Test func backgroundingWhileUnlockedLocksImmediately() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }

    @Test func manualLockClearsTheFace() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        let epochBefore = coordinator.disguiseEpoch
        coordinator.lock()
        #expect(coordinator.state == .locked)
        #expect(coordinator.disguiseEpoch > epochBefore)
    }

    @Test func pickerSuppressionWithinCapStaysUnlocked() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
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
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground()
        clock.now += AppLockCoordinator.suppressionCap + 1
        coordinator.sceneDidBecomeActive()
        #expect(coordinator.state == .locked)
    }

    @Test func monotonicInconsistencyFailsClosed() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground()
        clock.now -= 100 // clock went backwards (reboot) → lock
        coordinator.sceneDidBecomeActive()
        #expect(coordinator.state == .locked)
    }

    @Test func backgroundingWithoutSuppressionLocksEvenWithPickerFlagCleared() async {
        let (coordinator, _) = await makeLockedCoordinator()
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.systemUIDidDismiss()
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }

    // MARK: - Onboarding gate + erase-everything reset

    @Test func freshInstallShowsOnboardingUntilCompleted() {
        let store = InMemoryPasscodeStore()
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()), onboardingComplete: false)
        #expect(coordinator.showOnboarding)
        coordinator.completeOnboarding()
        #expect(!coordinator.showOnboarding)
        #expect(coordinator.pendingDisguiseId == "calculator")
    }

    @Test func onboardingNeverShowsOncePasscodeExists() async {
        // Even with the flag unset (upgrade path), an existing vault means no explainer.
        let store = SpyPasscodeStore()
        await store.seed(["D1", "D2", "ADD", "D3", "D4"])
        let coordinator = AppLockCoordinator(passcodeStore: store, appIcons: AppIconManager(icons: FakeAlternateIcons()), onboardingComplete: false)
        #expect(!coordinator.showOnboarding)
    }

    @Test func completedOnboardingStaysHiddenDuringSetup() {
        let (coordinator, _) = makeSetupCoordinator() // onboardingComplete defaults to true
        #expect(!coordinator.showOnboarding)
    }

    @Test func resetReturnsToFirstRunState() async {
        let (coordinator, _) = await makeLockedCoordinator(disguiseId: "pattern")
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        let epochBefore = coordinator.disguiseEpoch
        coordinator.reset()
        #expect(coordinator.state == .firstRunSetup(.enterNew))
        #expect(coordinator.caption?.primary == .promptNewSetup)
        #expect(coordinator.showOnboarding)
        #expect(!coordinator.showNoRecoveryNotice)
        #expect(coordinator.disguiseEpoch > epochBefore)
        #expect(!coordinator.systemUIInFlight)
        // Post-erase the app is back on the default face.
        #expect(coordinator.pendingDisguiseId == "calculator")
        #expect(coordinator.activeDisguise.id == "calculator")
    }

    @Test func resetClearsPickerSuppression() async {
        let clock = FakeClock()
        let (coordinator, _) = await makeLockedCoordinator(clock: clock)
        await coordinator.commit(tokens: ["D1", "D2", "ADD", "D3", "D4"], overflowed: false)
        coordinator.systemUIWillPresent()
        coordinator.sceneDidEnterBackground() // suppressed backgrounding pending
        coordinator.reset()
        // A vault set up after the reset must not inherit the old suppression
        // window: the next backgrounding locks immediately.
        await coordinator.commit(tokens: ["D5", "D6", "ADD", "D7"], overflowed: false)
        await coordinator.commit(tokens: ["D5", "D6", "ADD", "D7"], overflowed: false)
        #expect(coordinator.state == .unlocked)
        coordinator.sceneDidEnterBackground()
        #expect(coordinator.state == .locked)
    }
}
