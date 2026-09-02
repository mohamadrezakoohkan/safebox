import Foundation
import Observation

/// Single owner of the lock state; the root view is a pure switch over it.
/// Fail closed: any ambiguity resolves to the locked/calculator state.
@MainActor
@Observable
final class AppLockCoordinator {
    /// Hard cap for the picker suppression window (idea plan §2.5.1), measured
    /// on the monotonic clock — never wall-clock.
    static let suppressionCap: TimeInterval = 120

    private(set) var state: LockState
    private(set) var banner: LockBanner?
    var showNoRecoveryNotice = false
    /// First-run guide gate: true only while no passcode exists AND the guide
    /// was never finished or skipped. Persisting completion is the caller's
    /// job (OnboardingSentinel) — this is the in-memory switch the root reads.
    private(set) var showOnboarding: Bool
    /// Bumped on every lock transition so the calculator UI (display + recorder
    /// buffer) is recreated pristine.
    private(set) var calculatorEpoch = 0

    /// Set while an app-initiated system presentation (PhotosPicker) is in
    /// flight; suppresses immediate lock-on-background for that round-trip.
    private(set) var systemUIInFlight = false
    private var suppressedBackgroundedAt: TimeInterval?

    private let passcodeStore: any PasscodeStore
    private let uptime: @Sendable () -> TimeInterval
    /// Invoked on every transition INTO .locked (tmp cleanup etc.).
    var onLock: (() -> Void)?

    init(passcodeStore: any PasscodeStore,
         uptime: @escaping @Sendable () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
         onboardingComplete: Bool = true) {
        self.passcodeStore = passcodeStore
        self.uptime = uptime
        if passcodeStore.hasPasscode {
            state = .locked
            showOnboarding = false
        } else {
            state = .firstRunSetup(.enterNew)
            banner = LockBanner(primary: LockCopy.setupEntryBanner, secondary: LockCopy.setupEntryHint)
            showOnboarding = !onboardingComplete
        }
    }

    func completeOnboarding() {
        showOnboarding = false
    }

    /// Post-nuke: content and passcode are already gone; return the state
    /// machine to its just-installed shape — setup mode, onboarding showing,
    /// every transient buffer discarded.
    func reset() {
        state = .firstRunSetup(.enterNew)
        banner = LockBanner(primary: LockCopy.setupEntryBanner, secondary: LockCopy.setupEntryHint)
        showNoRecoveryNotice = false
        showOnboarding = true
        calculatorEpoch += 1
        suppressedBackgroundedAt = nil
        systemUIInFlight = false
    }

    // MARK: - Commits from the calculator

    func commit(sequence: [CalcKey], overflowed: Bool) async {
        switch state {
        case .firstRunSetup(.enterNew):
            if overflowed {
                banner = LockBanner(primary: LockCopy.setupTooLong, secondary: LockCopy.setupEntryHint)
                return
            }
            guard sequence.count >= PasscodeRules.minKeys else {
                banner = LockBanner(primary: LockCopy.setupTooShort, secondary: LockCopy.setupEntryHint)
                return
            }
            // Hold the pending plain sequence in memory; hash only on confirm.
            let warning = PasscodeRules.isTrivial(sequence) ? LockCopy.setupTrivialWarning : nil
            state = .firstRunSetup(.confirm(pending: sequence))
            banner = LockBanner(primary: LockCopy.setupConfirmBanner, secondary: warning)

        case .firstRunSetup(.confirm(let pending)):
            if !overflowed && sequence == pending {
                do {
                    try await passcodeStore.set(sequence: sequence)
                    banner = nil
                    showNoRecoveryNotice = true
                    state = .unlocked
                } catch {
                    // Storing failed: fail closed back to entry.
                    state = .firstRunSetup(.enterNew)
                    banner = LockBanner(primary: LockCopy.setupEntryBanner, secondary: LockCopy.setupEntryHint)
                }
            } else {
                state = .firstRunSetup(.enterNew)
                banner = LockBanner(primary: LockCopy.setupMismatch, secondary: LockCopy.setupEntryHint)
            }

        case .locked:
            // Sub-minimum or overflowed commits skip the compare entirely —
            // no Keychain read, no KDF.
            guard !overflowed, PasscodeRules.isValidLength(sequence) else { return }
            if await passcodeStore.matches(sequence: sequence) {
                state = .unlocked
                calculatorEpoch += 1
            }
            // Non-match: do nothing, forever, silently.

        case .unlocked:
            break
        }
    }

    // MARK: - Locking

    func lock() {
        guard state == .unlocked else { return }
        state = .locked
        banner = nil
        calculatorEpoch += 1
        suppressedBackgroundedAt = nil
        systemUIInFlight = false
        onLock?()
    }

    func sceneDidEnterBackground() {
        switch state {
        case .firstRunSetup:
            // Backgrounding mid-setup discards both buffers — fail closed.
            state = .firstRunSetup(.enterNew)
            banner = LockBanner(primary: LockCopy.setupEntryBanner, secondary: LockCopy.setupEntryHint)
            calculatorEpoch += 1
        case .locked:
            // A half-typed code never survives a background/foreground cycle.
            calculatorEpoch += 1
        case .unlocked:
            if systemUIInFlight {
                suppressedBackgroundedAt = uptime()
            } else {
                lock()
            }
        }
    }

    func sceneDidBecomeActive() {
        if state == .unlocked, let backgroundedAt = suppressedBackgroundedAt {
            suppressedBackgroundedAt = nil
            let now = uptime()
            // Fail closed on any monotonic inconsistency (reboot, restart).
            if now < backgroundedAt || now - backgroundedAt > Self.suppressionCap {
                lock()
            }
        }
    }

    // MARK: - System-UI suppression (photo picker)

    func systemUIWillPresent() {
        systemUIInFlight = true
    }

    func systemUIDidDismiss() {
        systemUIInFlight = false
        suppressedBackgroundedAt = nil
    }
}
