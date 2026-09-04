package com.calcplus.calculator.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.lock.PasscodeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Settings re-enrollment state machine, serving BOTH flows (decisions §5):
 *
 * - **Change passcode** (`switchDisguise = false`):
 *   `VERIFY_CURRENT → ENTER_NEW → CONFIRM → DONE`, on the current face
 *   throughout. The face is preserved: the single `set()` passes the active
 *   face's alphabet and id.
 * - **Change disguise** (`switchDisguise = true`): the same machine with a
 *   `PICK_DISGUISE` step wedged in after verification, and the new face used
 *   from `ENTER_NEW` onwards.
 *
 * Silence is a disguise feature only on the lock screen — here a wrong entry
 * always gets visible feedback (the failed-attempt pulse + an error caption),
 * on every face. Unlimited retries, no lockout.
 */
class ChangePasscodeViewModel(
    private val passcodeRepository: PasscodeRepository,
    private val registry: DisguiseRegistry,
    private val currentFace: DisguiseProvider,
    val switchDisguise: Boolean = false,
    private val onDisguiseChanged: (String) -> Unit = {},
) : ViewModel() {
    sealed interface Phase {
        data object VerifyCurrent : Phase
        /** Only reachable in the switch flow. */
        data object PickDisguise : Phase
        data object EnterNew : Phase
        data class Confirm(val pending: List<String>) : Phase
        data object Done : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.VerifyCurrent)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _caption = MutableStateFlow(CaptionState(CaptionKind.PROMPT_CURRENT))
    val caption: StateFlow<CaptionState> = _caption.asStateFlow()

    /**
     * The failed-attempt pulse. `verifyCurrent` bumps it for ANY face (§1.1),
     * covert included — this is not the lock screen.
     */
    private val _failedAttemptToken = MutableStateFlow(0)
    val failedAttemptToken: StateFlow<Int> = _failedAttemptToken.asStateFlow()

    /** The face being enrolled. Equals the current face in a plain change. */
    private val _targetDisguiseId = MutableStateFlow(currentFace.id)
    val targetDisguiseId: StateFlow<String> = _targetDisguiseId.asStateFlow()

    val targetFace: DisguiseProvider get() = registry.resolve(_targetDisguiseId.value)

    /** Which face renders a given phase: the old one until the new one is picked. */
    fun faceForPhase(phase: Phase): DisguiseProvider = when (phase) {
        Phase.VerifyCurrent, Phase.PickDisguise -> currentFace
        else -> targetFace
    }

    fun modeForPhase(phase: Phase): DisguiseMode = when (phase) {
        Phase.VerifyCurrent, Phase.PickDisguise -> DisguiseMode.VERIFY_CURRENT
        Phase.EnterNew, Phase.Done -> DisguiseMode.CAPTURE_NEW
        is Phase.Confirm -> DisguiseMode.CONFIRM_NEW
    }

    /** Carousel selection while in [Phase.PickDisguise]. */
    fun selectTargetDisguise(id: String) {
        _targetDisguiseId.value = registry.resolve(id).id
    }

    /** The picker's CTA. Disabled while the centered card is the current face. */
    fun confirmPick() {
        if (_phase.value != Phase.PickDisguise) return
        if (_targetDisguiseId.value == currentFace.id) return
        _phase.value = Phase.EnterNew
        _caption.value = CaptionState(CaptionKind.PROMPT_NEW_CHANGE, CaptionKind.STRENGTH_HINT)
    }

    /**
     * §1.3 revert rule: while the caption is WRONG_CODE, any token, clear or
     * removeLast reverts it to PROMPT_CURRENT. A commit does not revert.
     */
    fun inputReceived() {
        if (_caption.value.primary == CaptionKind.WRONG_CODE) {
            _caption.value = CaptionState(CaptionKind.PROMPT_CURRENT)
        }
    }

    fun commit(tokens: List<String>, overflowed: Boolean) {
        viewModelScope.launch { commitInternal(tokens, overflowed) }
    }

    suspend fun commitInternal(tokens: List<String>, overflowed: Boolean) {
        when (val phase = _phase.value) {
            Phase.VerifyCurrent -> {
                // ANY commit that is not the exact current code pulses and shows
                // the error caption — sub-minimum and overflowed included. The
                // KDF is still skipped for those (no store read).
                val matches = !overflowed &&
                    PasscodeRules.isValidLength(tokens) &&
                    passcodeRepository.matches(tokens)
                if (matches) {
                    if (switchDisguise) {
                        _phase.value = Phase.PickDisguise
                        _caption.value = CaptionState(CaptionKind.PROMPT_CURRENT)
                    } else {
                        _phase.value = Phase.EnterNew
                        _caption.value =
                            CaptionState(CaptionKind.PROMPT_NEW_CHANGE, CaptionKind.STRENGTH_HINT)
                    }
                } else {
                    _caption.value = CaptionState(CaptionKind.WRONG_CODE)
                    _failedAttemptToken.value += 1
                }
            }
            // The picker has no keypad; a stray commit cannot reach it.
            Phase.PickDisguise -> Unit
            Phase.EnterNew -> {
                if (overflowed) {
                    _caption.value = CaptionState(CaptionKind.TOO_LONG, CaptionKind.STRENGTH_HINT)
                    return
                }
                if (tokens.size < PasscodeRules.MIN_TOKENS) {
                    _caption.value = CaptionState(CaptionKind.TOO_SHORT, CaptionKind.STRENGTH_HINT)
                    return
                }
                val warning =
                    if (PasscodeRules.isTrivial(tokens)) CaptionKind.TRIVIAL_WARNING else null
                _phase.value = Phase.Confirm(tokens)
                _caption.value = CaptionState(CaptionKind.PROMPT_CONFIRM_CHANGE, warning)
            }
            is Phase.Confirm -> {
                if (!overflowed && tokens == phase.pending) {
                    val target = targetFace
                    // ONE atomic replace: fresh salt, new alphabet, new face id,
                    // blob and mirror in a single transaction. Until it lands the
                    // old envelope is authoritative in every respect.
                    val stored = runCatching {
                        passcodeRepository.set(tokens, target.alphabet, target.id)
                    }.isSuccess
                    if (stored) {
                        onDisguiseChanged(target.id)
                        _phase.value = Phase.Done
                    } else {
                        // Write failure: the old envelope is intact, so the user
                        // is simply asked for the new code again.
                        _phase.value = Phase.EnterNew
                        _caption.value =
                            CaptionState(CaptionKind.PROMPT_NEW_CHANGE, CaptionKind.STRENGTH_HINT)
                    }
                } else {
                    _phase.value = Phase.EnterNew
                    _caption.value = CaptionState(CaptionKind.MISMATCH, CaptionKind.STRENGTH_HINT)
                }
            }
            Phase.Done -> Unit
        }
    }
}
