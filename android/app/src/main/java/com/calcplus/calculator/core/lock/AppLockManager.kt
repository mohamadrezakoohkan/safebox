package com.calcplus.calculator.core.lock

import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide owner of the lock state; the root composable is a pure switch
 * over it. Fail closed: any ambiguity resolves to the locked state on the
 * default (calculator) face. LockState lives in memory only and defaults to
 * Locked on process creation.
 *
 * @param initialActiveDisguiseId the launch-read mirror value (decisions §3).
 *   Never the envelope: unwrapping it at process start would mean Keystore work
 *   in `Application.onCreate`. An unknown or null id resolves to the default.
 * @param reconcileCoverIdentity brings the home-screen icon and name into
 *   agreement with the enrolled face (§9a). Called ONLY from [onAppStop], and
 *   never at a moment the user is looking at the app — see the note there.
 *   Best-effort by contract: it must not throw and must not block.
 */
class AppLockManager(
    private val passcodeRepository: PasscodeRepository,
    private val registry: DisguiseRegistry,
    hasPasscode: Boolean,
    private val elapsedRealtime: () -> Long,
    onboardingComplete: Boolean = true,
    initialActiveDisguiseId: String? = null,
    private val reconcileCoverIdentity: (DisguiseProvider) -> Unit = {},
) {
    companion object {
        /** Hard cap for the picker suppression window (idea plan §2.5.1), ms, monotonic. */
        const val SUPPRESSION_CAP_MS = 120_000L
    }

    private val _lockState = MutableStateFlow(if (hasPasscode) LockState.Locked else LockState.NeedsSetup)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _setupPhase = MutableStateFlow<SetupPhase>(SetupPhase.Entry)
    val setupPhase: StateFlow<SetupPhase> = _setupPhase.asStateFlow()

    private val _caption = MutableStateFlow(
        if (hasPasscode) null else CaptionState(CaptionKind.PROMPT_NEW_SETUP, CaptionKind.STRENGTH_HINT)
    )
    val caption: StateFlow<CaptionState?> = _caption.asStateFlow()

    private val _showNoRecoveryNotice = MutableStateFlow(false)
    val showNoRecoveryNotice: StateFlow<Boolean> = _showNoRecoveryNotice.asStateFlow()

    /**
     * First-run guide gate: true only while no passcode exists AND the guide
     * was never finished or skipped. Persisting completion is the caller's
     * job — and, since iteration 3, it happens on the NeedsSetup → Unlocked
     * transition rather than at guide finish (decisions §4).
     */
    private val _showOnboarding = MutableStateFlow(!hasPasscode && !onboardingComplete)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    /** The enrolled lock face. Fail-closed to the registry default. */
    private val _activeDisguise = MutableStateFlow(registry.resolve(initialActiveDisguiseId))
    val activeDisguise: StateFlow<DisguiseProvider> = _activeDisguise.asStateFlow()

    /**
     * The face first-run setup will enroll (decisions §4). Chosen in the guide
     * carousel, survives backgrounding, dies with the process.
     */
    private val _pendingDisguiseId = MutableStateFlow(registry.default.id)
    val pendingDisguiseId: StateFlow<String> = _pendingDisguiseId.asStateFlow()

    /**
     * The failed-attempt pulse (§1.1): monotonically increasing, reset to 0
     * whenever a fresh surface is instantiated. Carries no semantics beyond
     * "that attempt failed" — never why, never which token.
     */
    private val _failedAttemptToken = MutableStateFlow(0)
    val failedAttemptToken: StateFlow<Int> = _failedAttemptToken.asStateFlow()

    /** Bumped on every lock transition so the lock face is recreated pristine. */
    private val _surfaceEpoch = MutableStateFlow(0)
    val surfaceEpoch: StateFlow<Int> = _surfaceEpoch.asStateFlow()

    /** Set while an app-initiated system presentation (photo picker) is in flight. */
    var systemUiInFlight = false
        private set
    private var suppressedStoppedAt: Long? = null

    /** The face the root should render right now: the pending one while in setup. */
    fun surfaceDisguise(state: LockState): DisguiseProvider = when (state) {
        LockState.NeedsSetup -> registry.resolve(_pendingDisguiseId.value)
        else -> _activeDisguise.value
    }

    fun dismissNoRecoveryNotice() {
        _showNoRecoveryNotice.value = false
    }

    /**
     * The guide finished (or was skipped) on [selectedDisguiseId] — whatever
     * card was centered at that moment. Only the in-memory gate flips here; the
     * persisted sentinel is written when the first envelope lands (§4).
     */
    fun completeOnboarding(selectedDisguiseId: String = registry.default.id) {
        selectPendingDisguise(selectedDisguiseId)
        _showOnboarding.value = false
    }

    /** Live carousel selection during the guide. A face change is a fresh surface (§1.5). */
    fun selectPendingDisguise(id: String) {
        val resolved = registry.resolve(id).id
        if (_pendingDisguiseId.value == resolved) return
        _pendingDisguiseId.value = resolved
        if (_lockState.value == LockState.NeedsSetup) bumpEpoch()
    }

    /**
     * The switch flow committed a new face; the next lock shows it (§5). The
     * caller invokes this only after the single atomic envelope write landed.
     *
     * The home-screen identity is deliberately NOT changed here — see
     * [onAppStop]. It follows at the next background, which the user must pass
     * through before they can see the home screen at all.
     */
    fun setActiveDisguise(id: String) {
        val resolved = registry.resolve(id)
        if (_activeDisguise.value.id == resolved.id) return
        _activeDisguise.value = resolved
        bumpEpoch()
    }

    /**
     * Post-nuke: content and passcode are already gone; return the state
     * machine to its just-installed shape — setup mode, onboarding showing,
     * the default face, every transient buffer discarded.
     */
    fun reset() {
        _lockState.value = LockState.NeedsSetup
        _setupPhase.value = SetupPhase.Entry
        _caption.value = CaptionState(CaptionKind.PROMPT_NEW_SETUP, CaptionKind.STRENGTH_HINT)
        _showNoRecoveryNotice.value = false
        _showOnboarding.value = true
        _pendingDisguiseId.value = registry.default.id
        _activeDisguise.value = registry.default
        // The home screen follows at the next background, like every other
        // identity change (§9a) — erase leaves the user inside the app, on
        // first-run setup, and must not eject them to the launcher.
        bumpEpoch()
        suppressedStoppedAt = null
        systemUiInFlight = false
    }

    // MARK: Commits from the lock face

    /**
     * A commit from the active surface. Tokens are opaque; the manager never
     * learns which face produced them beyond [activeDisguise].
     *
     * EVERY suspending read happens BEFORE the synchronous state writes, with
     * no suspension point between them. `SafeBoxApp.rememberFrozenWhile`
     * depends on `_lockState`, `_surfaceEpoch`, `_caption` and
     * `_activeDisguise` landing in one composition — a suspension in the middle
     * would let a pristine epoch or a cleared caption paint one frame before
     * the exit freeze engages.
     */
    suspend fun commit(tokens: List<String>, overflowed: Boolean) {
        when (_lockState.value) {
            LockState.NeedsSetup -> commitDuringSetup(tokens, overflowed)
            LockState.Locked -> {
                // Sub-minimum or overflowed commits skip the compare entirely —
                // no store read, no KDF. The pulse (overt faces only) is the
                // only thing they can produce.
                if (overflowed || !PasscodeRules.isValidLength(tokens)) {
                    pulseFailedAttempt()
                    return
                }
                val matched = passcodeRepository.matches(tokens)
                // Envelope is authoritative; reading it here also heals a
                // desynced launch mirror. Both suspending reads are done.
                val enrolledFace = if (matched) passcodeRepository.activeDisguiseId() else null
                // ---- no suspension point below this line ----
                if (matched) {
                    _activeDisguise.value = registry.resolve(enrolledFace)
                    _lockState.value = LockState.Unlocked
                    bumpEpoch()
                } else {
                    // Covert face: nothing, forever, silently.
                    pulseFailedAttempt()
                }
            }
            LockState.Unlocked -> Unit
        }
    }

    private suspend fun commitDuringSetup(tokens: List<String>, overflowed: Boolean) {
        // Capture modes never pulse (§1.1) — the captions carry the outcome.
        when (val phase = _setupPhase.value) {
            SetupPhase.Entry -> {
                if (overflowed) {
                    _caption.value = CaptionState(CaptionKind.TOO_LONG, CaptionKind.STRENGTH_HINT)
                    return
                }
                if (tokens.size < PasscodeRules.MIN_TOKENS) {
                    _caption.value = CaptionState(CaptionKind.TOO_SHORT, CaptionKind.STRENGTH_HINT)
                    return
                }
                // Hold the pending plain sequence in memory; hash only on confirm.
                val warning = if (PasscodeRules.isTrivial(tokens)) CaptionKind.TRIVIAL_WARNING else null
                _setupPhase.value = SetupPhase.Confirm(tokens)
                _caption.value = CaptionState(CaptionKind.PROMPT_CONFIRM_SETUP, warning)
            }
            is SetupPhase.Confirm -> {
                if (!overflowed && tokens == phase.pending) {
                    val face = registry.resolve(_pendingDisguiseId.value)
                    passcodeRepository.set(tokens, face.alphabet, face.id)
                    // ---- no suspension point below this line ----
                    _activeDisguise.value = face
                    _caption.value = null
                    _setupPhase.value = SetupPhase.Entry
                    _showNoRecoveryNotice.value = true
                    _lockState.value = LockState.Unlocked
                } else {
                    _setupPhase.value = SetupPhase.Entry
                    _caption.value = CaptionState(CaptionKind.MISMATCH, CaptionKind.STRENGTH_HINT)
                }
            }
        }
    }

    /**
     * §1.1 bump rule for the two modes this manager owns. In `disguise` mode
     * only an OVERT face is pulsed; a covert one stays silent, unchanged from
     * iteration 1. Setup modes never pulse. `verifyCurrent` lives in the
     * change/switch view model, which always pulses.
     */
    private fun pulseFailedAttempt() {
        if (_lockState.value != LockState.Locked) return
        if (_activeDisguise.value.isCovert) return
        _failedAttemptToken.value += 1
    }

    private fun bumpEpoch() {
        _surfaceEpoch.value += 1
        // A fresh surface starts at 0, so a stale count can never shake it.
        _failedAttemptToken.value = 0
    }

    // MARK: Locking

    fun lock() {
        if (_lockState.value != LockState.Unlocked) return
        _lockState.value = LockState.Locked
        _caption.value = null
        bumpEpoch()
        suppressedStoppedAt = null
        systemUiInFlight = false
    }

    /**
     * The activity's onStop — the app left the foreground.
     *
     * This is also the ONLY place the home-screen cover identity is applied
     * (§9a). Disabling an `activity-alias` tears down the task rooted at it,
     * even with `DONT_KILL_APP` keeping the process alive: applied at the
     * moment of a setup or switch commit, the swap drops the user onto the
     * launcher and — because backgrounding locks the vault — makes them
     * re-enter the code they just set. So the identity is reconciled to the
     * enrolled face here instead, where the user is already leaving and the
     * vault is already locking, and a task teardown costs nothing.
     *
     * This loses nothing: the home screen is the only place the icon and name
     * are visible, and reaching it means passing through this method first.
     *
     * The one exception is the suppressed photo-picker round trip. The user is
     * coming straight back into this task with the vault still unlocked, so it
     * is a foreground moment wearing a background's clothes, and tearing the
     * task down there would lose the import they are in the middle of.
     */
    fun onAppStop() {
        if (!systemUiInFlight) reconcileCoverIdentity(_activeDisguise.value)
        when (_lockState.value) {
            LockState.NeedsSetup -> {
                // Backgrounding mid-setup discards both buffers — fail closed.
                // The chosen face survives: setup returns to captureNew on it.
                _setupPhase.value = SetupPhase.Entry
                _caption.value = CaptionState(CaptionKind.PROMPT_NEW_SETUP, CaptionKind.STRENGTH_HINT)
                bumpEpoch()
            }
            LockState.Locked -> {
                // A half-typed code never survives a background/foreground cycle.
                bumpEpoch()
            }
            LockState.Unlocked -> {
                if (systemUiInFlight) {
                    suppressedStoppedAt = elapsedRealtime()
                } else {
                    lock()
                }
            }
        }
    }

    /** ProcessLifecycleOwner onStart — back to the foreground. */
    fun onAppStart() {
        val stoppedAt = suppressedStoppedAt ?: return
        suppressedStoppedAt = null
        if (_lockState.value != LockState.Unlocked) return
        val now = elapsedRealtime()
        // Fail closed on any monotonic inconsistency (process restart, reboot).
        if (now < stoppedAt || now - stoppedAt > SUPPRESSION_CAP_MS) {
            lock()
        }
    }

    // MARK: System-UI suppression (photo picker)

    fun beginExternalActivity() {
        systemUiInFlight = true
    }

    fun endExternalActivity() {
        systemUiInFlight = false
        suppressedStoppedAt = null
    }
}
