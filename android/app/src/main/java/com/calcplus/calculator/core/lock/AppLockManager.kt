package com.calcplus.calculator.core.lock

import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.feature.calculator.CalcKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide owner of the lock state; the root composable is a pure switch
 * over it. Fail closed: any ambiguity resolves to the locked/calculator state.
 * LockState lives in memory only and defaults to Locked on process creation.
 */
class AppLockManager(
    private val passcodeRepository: PasscodeRepository,
    hasPasscode: Boolean,
    private val elapsedRealtime: () -> Long,
    onboardingComplete: Boolean = true,
) {
    companion object {
        /** Hard cap for the picker suppression window (idea plan §2.5.1), ms, monotonic. */
        const val SUPPRESSION_CAP_MS = 120_000L
    }

    private val _lockState = MutableStateFlow(if (hasPasscode) LockState.Locked else LockState.NeedsSetup)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _setupPhase = MutableStateFlow<SetupPhase>(SetupPhase.Entry)
    val setupPhase: StateFlow<SetupPhase> = _setupPhase.asStateFlow()

    private val _banner = MutableStateFlow(
        if (hasPasscode) null else LockBanner(BannerText.SETUP_ENTRY, BannerText.SETUP_HINT)
    )
    val banner: StateFlow<LockBanner?> = _banner.asStateFlow()

    private val _showNoRecoveryNotice = MutableStateFlow(false)
    val showNoRecoveryNotice: StateFlow<Boolean> = _showNoRecoveryNotice.asStateFlow()

    /**
     * First-run guide gate: true only while no passcode exists AND the guide
     * was never finished or skipped. Persisting completion is the caller's
     * job (OnboardingStore) — this is the in-memory switch the root UI reads.
     */
    private val _showOnboarding = MutableStateFlow(!hasPasscode && !onboardingComplete)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    /** Bumped on every lock transition so the calculator UI is recreated pristine. */
    private val _calculatorEpoch = MutableStateFlow(0)
    val calculatorEpoch: StateFlow<Int> = _calculatorEpoch.asStateFlow()

    /** Set while an app-initiated system presentation (photo picker) is in flight. */
    var systemUiInFlight = false
        private set
    private var suppressedStoppedAt: Long? = null

    fun dismissNoRecoveryNotice() {
        _showNoRecoveryNotice.value = false
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
    }

    /**
     * Post-nuke: content and passcode are already gone; return the state
     * machine to its just-installed shape — setup mode, onboarding showing,
     * every transient buffer discarded.
     */
    fun reset() {
        _lockState.value = LockState.NeedsSetup
        _setupPhase.value = SetupPhase.Entry
        _banner.value = LockBanner(BannerText.SETUP_ENTRY, BannerText.SETUP_HINT)
        _showNoRecoveryNotice.value = false
        _showOnboarding.value = true
        _calculatorEpoch.value += 1
        suppressedStoppedAt = null
        systemUiInFlight = false
    }

    // MARK: Commits from the calculator

    suspend fun commit(sequence: List<CalcKey>, overflowed: Boolean) {
        when (_lockState.value) {
            LockState.NeedsSetup -> commitDuringSetup(sequence, overflowed)
            LockState.Locked -> {
                // Sub-minimum or overflowed commits skip the compare entirely —
                // no store read, no KDF.
                if (overflowed || !PasscodeRules.isValidLength(sequence)) return
                if (passcodeRepository.matches(sequence)) {
                    _lockState.value = LockState.Unlocked
                    _calculatorEpoch.value += 1
                }
                // Non-match: do nothing, forever, silently.
            }
            LockState.Unlocked -> Unit
        }
    }

    private suspend fun commitDuringSetup(sequence: List<CalcKey>, overflowed: Boolean) {
        when (val phase = _setupPhase.value) {
            SetupPhase.Entry -> {
                if (overflowed) {
                    _banner.value = LockBanner(BannerText.SETUP_TOO_LONG, BannerText.SETUP_HINT)
                    return
                }
                if (sequence.size < PasscodeRules.MIN_KEYS) {
                    _banner.value = LockBanner(BannerText.SETUP_TOO_SHORT, BannerText.SETUP_HINT)
                    return
                }
                // Hold the pending plain sequence in memory; hash only on confirm.
                val warning = if (PasscodeRules.isTrivial(sequence)) BannerText.SETUP_TRIVIAL_WARNING else null
                _setupPhase.value = SetupPhase.Confirm(sequence)
                _banner.value = LockBanner(BannerText.SETUP_CONFIRM, warning)
            }
            is SetupPhase.Confirm -> {
                if (!overflowed && sequence == phase.pending) {
                    passcodeRepository.set(sequence)
                    _banner.value = null
                    _setupPhase.value = SetupPhase.Entry
                    _showNoRecoveryNotice.value = true
                    _lockState.value = LockState.Unlocked
                } else {
                    _setupPhase.value = SetupPhase.Entry
                    _banner.value = LockBanner(BannerText.SETUP_MISMATCH, BannerText.SETUP_HINT)
                }
            }
        }
    }

    // MARK: Locking

    fun lock() {
        if (_lockState.value != LockState.Unlocked) return
        _lockState.value = LockState.Locked
        _banner.value = null
        _calculatorEpoch.value += 1
        suppressedStoppedAt = null
        systemUiInFlight = false
    }

    /** ProcessLifecycleOwner onStop — the app left the foreground. */
    fun onAppStop() {
        when (_lockState.value) {
            LockState.NeedsSetup -> {
                // Backgrounding mid-setup discards both buffers — fail closed.
                _setupPhase.value = SetupPhase.Entry
                _banner.value = LockBanner(BannerText.SETUP_ENTRY, BannerText.SETUP_HINT)
                _calculatorEpoch.value += 1
            }
            LockState.Locked -> {
                // A half-typed code never survives a background/foreground cycle.
                _calculatorEpoch.value += 1
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
