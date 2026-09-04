import Foundation
import Observation

/// Single owner of the lock state; the root view is a pure switch over it.
/// Fail closed: any ambiguity resolves to the locked state on the default face.
@MainActor
@Observable
final class AppLockCoordinator {
    /// Hard cap for the picker suppression window (idea plan §2.5.1), measured
    /// on the monotonic clock — never wall-clock.
    static let suppressionCap: TimeInterval = 120

    private(set) var state: LockState
    /// Semantic caption for the surface. Never a literal string (§1.3).
    private(set) var caption: LockBanner?
    var showNoRecoveryNotice = false
    /// First-run guide gate: true only while no passcode exists AND the guide
    /// was never finished or skipped.
    private(set) var showOnboarding: Bool
    /// Bumped on every lock transition so the lock face (display + recorder
    /// buffer) is recreated pristine.
    private(set) var disguiseEpoch = 0
    /// The §1.1 failed-attempt pulse. Monotonic per surface instance; the
    /// surface reacts only to increments observed after its first render.
    private(set) var failedAttemptCount = 0
    /// Face chosen in the guide, before any envelope exists (§4). In memory
    /// only: it survives backgrounding and dies with the process. A change is
    /// a face-identity change, so the surface is rebuilt and the pulse reset
    /// (§1.5).
    var pendingDisguiseId = DisguiseRegistry.defaultId {
        didSet {
            guard oldValue != pendingDisguiseId else { return }
            failedAttemptCount = 0
        }
    }
    /// The enrolled face. Resolved at init and after a switch — **never** in
    /// `lock()` or `sceneDidEnterBackground()`: a device-locked Keychain reads
    /// as absent, which would flash the wrong face at exactly the wrong moment.
    private(set) var activeDisguise: any DisguiseProviding

    /// Set while an app-initiated system presentation (PhotosPicker) is in
    /// flight; suppresses immediate lock-on-background for that round-trip.
    private(set) var systemUIInFlight = false
    private var suppressedBackgroundedAt: TimeInterval?

    let registry: DisguiseRegistry
    private let passcodeStore: any PasscodeStore
    /// Cover identities (§9a). Only ever driven from the three points below —
    /// never from `lock()`, a re-lock, or launch: the icon is already right,
    /// and a redundant set would pop iOS's system alert for nothing.
    private let appIcons: AppIconManager
    private let uptime: @Sendable () -> TimeInterval
    /// Invoked on every transition INTO .locked (tmp cleanup etc.).
    var onLock: (() -> Void)?
    /// Invoked when the FIRST envelope is stored — not when the guide finishes
    /// (§4). A process death between the two would otherwise strand the user on
    /// a face they can no longer choose.
    var onSetupComplete: (() -> Void)?

    init(passcodeStore: any PasscodeStore,
         registry: DisguiseRegistry = DisguiseRegistry(),
         appIcons: AppIconManager = AppIconManager(),
         uptime: @escaping @Sendable () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
         onboardingComplete: Bool = true) {
        self.passcodeStore = passcodeStore
        self.registry = registry
        self.appIcons = appIcons
        self.uptime = uptime
        activeDisguise = registry.resolve(id: passcodeStore.activeDisguiseId)
        if passcodeStore.hasPasscode {
            state = .locked
            showOnboarding = false
        } else {
            state = .firstRunSetup(.enterNew)
            caption = LockBanner(primary: .promptNewSetup, secondary: .strengthHint)
            showOnboarding = !onboardingComplete
        }
    }

    // MARK: - Face resolution

    /// The face to render right now: the pending face while setting up, the
    /// enrolled face otherwise.
    var surfaceDisguise: any DisguiseProviding {
        if case .firstRunSetup = state {
            return registry.resolve(id: pendingDisguiseId)
        }
        return activeDisguise
    }

    var surfaceMode: DisguiseMode {
        switch state {
        case .firstRunSetup(.enterNew): .captureNew
        case .firstRunSetup(.confirm): .confirmNew
        case .locked, .unlocked: .disguise
        }
    }

    /// View identity for the surface (§1.5): a new epoch OR a new face identity
    /// tears the surface down and builds a fresh one. Phase changes within a
    /// flow deliberately do not.
    var surfaceIdentity: String {
        "\(disguiseEpoch)|\(surfaceDisguise.id)"
    }

    /// Re-reads the enrolled face after a switch and recreates the surface.
    /// Reached only from the switch flow's success path, so the envelope write
    /// has already landed — which is exactly when the cover identity may move
    /// (§9a: never before, so a failed write cannot leave the icon disagreeing
    /// with the code).
    func reloadActiveDisguise() {
        activeDisguise = registry.resolve(id: passcodeStore.activeDisguiseId)
        appIcons.apply(activeDisguise)
        disguiseEpoch += 1
        failedAttemptCount = 0
    }

    // MARK: - Onboarding

    /// Finishing (or skipping) the guide. `selectedDisguiseId` is whatever card
    /// was centered at that moment — calculator unless the user scrolled.
    /// The persisted sentinel is deliberately NOT written here (§4).
    func completeOnboarding(selectedDisguiseId: String = DisguiseRegistry.defaultId) {
        showOnboarding = false
        pendingDisguiseId = selectedDisguiseId
    }

    /// Post-nuke: content and passcode are already gone; return the state
    /// machine to its just-installed shape.
    func reset() {
        state = .firstRunSetup(.enterNew)
        caption = LockBanner(primary: .promptNewSetup, secondary: .strengthHint)
        showNoRecoveryNotice = false
        showOnboarding = true
        pendingDisguiseId = DisguiseRegistry.defaultId
        activeDisguise = registry.defaultDisguise
        // Just-installed state includes the shipped icon (§9a).
        appIcons.apply(activeDisguise)
        disguiseEpoch += 1
        failedAttemptCount = 0
        suppressedBackgroundedAt = nil
        systemUIInFlight = false
    }

    // MARK: - Commits from the lock face

    func commit(tokens: [String], overflowed: Bool) async {
        switch state {
        case .firstRunSetup(.enterNew):
            // No pulse in the capture modes, ever: the caption carries the
            // outcome (§1.1).
            if overflowed {
                caption = LockBanner(primary: .tooLong, secondary: .strengthHint)
                return
            }
            guard tokens.count >= PasscodeRules.minTokens else {
                caption = LockBanner(primary: .tooShort, secondary: .strengthHint)
                return
            }
            // Hold the pending plain tokens in memory; hash only on confirm.
            let warning: CaptionKind? = PasscodeRules.isTrivial(tokens) ? .trivialWarning : nil
            state = .firstRunSetup(.confirm(pending: tokens))
            caption = LockBanner(primary: .promptConfirmSetup, secondary: warning)

        case .firstRunSetup(.confirm(let pending)):
            if !overflowed && tokens == pending {
                let face = registry.resolve(id: pendingDisguiseId)
                do {
                    try await passcodeStore.set(tokens: tokens,
                                                alphabet: face.alphabet,
                                                activeDisguiseId: face.id)
                    caption = nil
                    showNoRecoveryNotice = true
                    activeDisguise = face
                    // Only now, with the first envelope on disk (§9a).
                    appIcons.apply(face)
                    // The sentinel is written here, with the first envelope.
                    onSetupComplete?()
                    state = .unlocked
                } catch {
                    // Storing failed: fail closed back to entry.
                    state = .firstRunSetup(.enterNew)
                    caption = LockBanner(primary: .promptNewSetup, secondary: .strengthHint)
                }
            } else {
                state = .firstRunSetup(.enterNew)
                caption = LockBanner(primary: .mismatch, secondary: .strengthHint)
            }

        case .locked:
            // Sub-minimum or overflowed commits skip the compare entirely —
            // no Keychain read, no KDF. The pulse (overt faces only) still
            // fires, immediately, because there is nothing to wait for.
            guard !overflowed, PasscodeRules.isValidLength(tokens) else {
                pulseIfOvert()
                return
            }
            if await passcodeStore.matches(tokens: tokens) {
                state = .unlocked
                disguiseEpoch += 1
                failedAttemptCount = 0
            } else {
                // Covert face: nothing, forever, silently.
                pulseIfOvert()
            }

        case .unlocked:
            break
        }
    }

    /// §1.1: in `disguise` mode only an overt face is told an attempt failed.
    /// The buffer clear that accompanies it lives in `DisguiseSurfaceHost`,
    /// which owns the recorder.
    private func pulseIfOvert() {
        guard !surfaceDisguise.isCovert else { return }
        failedAttemptCount += 1
    }

    // MARK: - Locking

    func lock() {
        guard state == .unlocked else { return }
        state = .locked
        caption = nil
        disguiseEpoch += 1
        failedAttemptCount = 0
        suppressedBackgroundedAt = nil
        systemUIInFlight = false
        onLock?()
    }

    func sceneDidEnterBackground() {
        switch state {
        case .firstRunSetup:
            // Backgrounding mid-setup discards both buffers — fail closed. The
            // pending face survives: setup resumes on the same face (§4).
            state = .firstRunSetup(.enterNew)
            caption = LockBanner(primary: .promptNewSetup, secondary: .strengthHint)
            disguiseEpoch += 1
            failedAttemptCount = 0
        case .locked:
            // A half-typed code never survives a background/foreground cycle.
            disguiseEpoch += 1
            failedAttemptCount = 0
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
