package com.calcplus.calculator.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.lock.BannerText
import com.calcplus.calculator.core.lock.LockBanner
import com.calcplus.calculator.core.lock.PasscodeRules
import com.calcplus.calculator.feature.calculator.CalcKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings change-passcode flow: VerifyCurrent → EnterNew → Confirm (idea plan
 * §2.7). Silence is a disguise feature only on the lock screen — here wrong
 * input gets visible feedback (shake + error caption). Unlimited retries.
 */
class ChangePasscodeViewModel(
    private val passcodeRepository: PasscodeRepository,
) : ViewModel() {
    sealed interface Phase {
        data object VerifyCurrent : Phase
        data object EnterNew : Phase
        data class Confirm(val pending: List<CalcKey>) : Phase
        data object Done : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.VerifyCurrent)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _banner = MutableStateFlow(LockBanner(BannerText.VERIFY_CURRENT))
    val banner: StateFlow<LockBanner> = _banner.asStateFlow()

    private val _bannerIsError = MutableStateFlow(false)
    val bannerIsError: StateFlow<Boolean> = _bannerIsError.asStateFlow()

    /** Bumped to trigger the shake animation on the display readout. */
    private val _shakeToken = MutableStateFlow(0)
    val shakeToken: StateFlow<Int> = _shakeToken.asStateFlow()

    /** Reverts an error caption back to the phase caption (called on key press). */
    fun keyPressed() {
        if (_bannerIsError.value) {
            _bannerIsError.value = false
            _banner.value = LockBanner(BannerText.VERIFY_CURRENT)
        }
    }

    fun commit(sequence: List<CalcKey>, overflowed: Boolean) {
        viewModelScope.launch { commitInternal(sequence, overflowed) }
    }

    suspend fun commitInternal(sequence: List<CalcKey>, overflowed: Boolean) {
        when (val phase = _phase.value) {
            Phase.VerifyCurrent -> {
                // ANY commit that is not the exact current code shows verify_error —
                // including sub-minimum and overflowed commits (design spec §5.6).
                val matches = !overflowed &&
                    PasscodeRules.isValidLength(sequence) &&
                    passcodeRepository.matches(sequence)
                if (matches) {
                    _phase.value = Phase.EnterNew
                    _bannerIsError.value = false
                    _banner.value = LockBanner(BannerText.CHANGE_ENTER_NEW, BannerText.SETUP_HINT)
                } else {
                    _banner.value = LockBanner(BannerText.VERIFY_ERROR)
                    _bannerIsError.value = true
                    _shakeToken.value += 1
                }
            }
            Phase.EnterNew -> {
                if (overflowed) {
                    _banner.value = LockBanner(BannerText.SETUP_TOO_LONG, BannerText.SETUP_HINT)
                    return
                }
                if (sequence.size < PasscodeRules.MIN_KEYS) {
                    _banner.value = LockBanner(BannerText.SETUP_TOO_SHORT, BannerText.SETUP_HINT)
                    return
                }
                val warning = if (PasscodeRules.isTrivial(sequence)) BannerText.SETUP_TRIVIAL_WARNING else null
                _phase.value = Phase.Confirm(sequence)
                _banner.value = LockBanner(BannerText.CHANGE_CONFIRM, warning)
            }
            is Phase.Confirm -> {
                if (!overflowed && sequence == phase.pending) {
                    // Fresh salt; the stored blob is replaced atomically.
                    passcodeRepository.set(sequence)
                    _phase.value = Phase.Done
                } else {
                    _phase.value = Phase.EnterNew
                    _banner.value = LockBanner(BannerText.SETUP_MISMATCH, BannerText.SETUP_HINT)
                }
            }
            Phase.Done -> Unit
        }
    }
}
