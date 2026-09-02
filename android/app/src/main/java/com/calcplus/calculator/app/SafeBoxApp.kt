package com.calcplus.calculator.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcplus.calculator.R
import com.calcplus.calculator.core.lock.BannerText
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.calculator.CalculatorScreen
import com.calcplus.calculator.feature.calculator.CalculatorSession
import com.calcplus.calculator.feature.calculator.CaptionState
import com.calcplus.calculator.feature.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

@Composable
fun bannerString(text: BannerText): String = stringResource(
    when (text) {
        BannerText.SETUP_ENTRY -> R.string.setup_entry_banner
        BannerText.SETUP_HINT -> R.string.setup_entry_hint
        BannerText.SETUP_TOO_SHORT -> R.string.setup_too_short
        BannerText.SETUP_TOO_LONG -> R.string.setup_too_long
        BannerText.SETUP_CONFIRM -> R.string.setup_confirm_banner
        BannerText.SETUP_MISMATCH -> R.string.setup_mismatch
        BannerText.SETUP_TRIVIAL_WARNING -> R.string.setup_trivial_warning
        BannerText.VERIFY_CURRENT -> R.string.verify_current_caption
        BannerText.VERIFY_ERROR -> R.string.verify_error
        BannerText.CHANGE_ENTER_NEW -> R.string.change_enter_new_caption
        BannerText.CHANGE_CONFIRM -> R.string.change_confirm_caption
    }
)

/**
 * Root composable: app lock is root-level state, above navigation. The locked
 * branch contains no navigation graph and no vault composables — nothing
 * vault-related is even composed while locked.
 */
@Composable
fun SafeBoxApp(container: AppContainer) {
    val lockManager = container.lockManager
    val lockState by lockManager.lockState.collectAsStateWithLifecycle()
    val showNotice by lockManager.showNoRecoveryNotice.collectAsStateWithLifecycle()
    val showOnboarding by lockManager.showOnboarding.collectAsStateWithLifecycle()

    when (lockState) {
        LockState.NeedsSetup ->
            // First run (and post-erase): the guide runs before the calculator
            // ever appears. Only while NO passcode exists — the disguise is
            // never preceded by an explainer once a vault is set up.
            if (showOnboarding) {
                OnboardingScreen(
                    onFinish = {
                        lockManager.completeOnboarding()
                        // Persist in the lock-surviving scope: the composable
                        // is disposed the moment the switch flips.
                        container.applicationScope.launch {
                            container.onboardingStore.setComplete()
                        }
                    },
                )
            } else {
                LockCalculator(container)
            }
        LockState.Locked -> LockCalculator(container)
        LockState.Unlocked -> VaultScaffold(container)
    }

    // One-time no-recovery notice over the just-revealed vault.
    if (showNotice && lockState == LockState.Unlocked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.setup_no_recovery_title)) },
            text = { Text(stringResource(R.string.setup_no_recovery_body)) },
            confirmButton = {
                TextButton(onClick = { lockManager.dismissNoRecoveryNotice() }) {
                    Text(stringResource(R.string.setup_no_recovery_button))
                }
            },
        )
    }
}

@Composable
private fun LockCalculator(container: AppContainer) {
    val lockManager = container.lockManager
    val banner by lockManager.banner.collectAsStateWithLifecycle()
    val epoch by lockManager.calculatorEpoch.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // key(epoch): every lock transition recreates a pristine calculator
    // (display and recorder buffer cleared).
    key(epoch) {
        val session = remember {
            CalculatorSession(
                onCommit = { keys, overflowed ->
                    // Verification runs off the UI path; the display rendered already.
                    scope.launch { lockManager.commit(keys, overflowed) }
                },
            )
        }
        CalculatorScreen(
            session = session,
            caption = banner?.let { b ->
                CaptionState(
                    primary = bannerString(b.primary),
                    secondary = b.secondary?.let { bannerString(it) },
                )
            },
        )
    }
}
