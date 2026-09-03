package com.calcplus.calculator.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcplus.calculator.R
import com.calcplus.calculator.core.lock.BannerText
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.calculator.CalculatorScreen
import com.calcplus.calculator.feature.calculator.CalculatorSession
import com.calcplus.calculator.feature.calculator.CaptionState
import com.calcplus.calculator.feature.onboarding.OnboardingMode
import com.calcplus.calculator.feature.onboarding.OnboardingScreen
import com.calcplus.calculator.feature.onboarding.recordOnboardingCompletion
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
 * vault-related is composed while locked, modulo [AnimatedContent] keeping the
 * outgoing vault composed for the frames it takes to dispose it after a lock.
 * During those frames the vault is NOT DRAWN at all: the per-surface draw
 * guard below skips `drawContent()` for an exiting Unlocked surface, so no
 * vault pixel exists from the first post-lock frame — independent of whatever
 * alpha the animation system settles on for it.
 *
 * The switch over lock state is an [AnimatedContent]. Only a change INTO
 * Unlocked plays the zoom-in reveal ([UnlockReveal.kind]); every other change
 * — a lock of any kind, setup ↔ locked, every epoch bump — is an instant cut,
 * where the outgoing surface is covered by the incoming one on the very first
 * frame and disposed right after. FLAG_SECURE is unconditional (MainActivity),
 * so no transitional frame can reach a recents snapshot either way.
 */
@Composable
fun SafeBoxApp(container: AppContainer) {
    val lockManager = container.lockManager
    val lockState by lockManager.lockState.collectAsStateWithLifecycle()
    val showNotice by lockManager.showNoRecoveryNotice.collectAsStateWithLifecycle()
    val showOnboarding by lockManager.showOnboarding.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // An explicit Transition (rather than AnimatedContent(targetState = …)) so
    // the no-recovery notice below can wait for the reveal to actually finish.
    val lockTransition = updateTransition(targetState = lockState, label = "root")
    lockTransition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // The animator scale is read per change, never cached, so "Remove
            // animations" toggled while the app runs is honored immediately.
            UnlockReveal.contentTransform(
                UnlockReveal.kind(
                    from = initialState,
                    to = targetState,
                    animatorDurationScale = UnlockReveal.animatorDurationScale(context),
                ),
            )
        },
    ) { state ->
        // `this.transition` is this surface's own enter/exit transition (not
        // lockTransition): a PostExit target means it is on its way out.
        val exiting = this.transition.targetState == EnterExitState.PostExit
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Draw guard: an exiting VAULT is never drawn. `exiting` flips in
                // the same composition as the lock, so this holds from the first
                // post-lock frame whether the lock was a plain cut or landed
                // mid-reveal. It does not rely on the exit animation: when a
                // lock interrupts a running reveal, animation-core replaces the
                // CUT's snap with its interruption spring, and the vault's alpha
                // would otherwise ease to 0 over ~150–300 ms behind a calculator
                // that is itself still translucent. The snap in UnlockReveal
                // remains what pins the fast disposal; this is what guarantees
                // no vault pixel. The fading calculator is unaffected (it is
                // meant to be seen on its way out).
                .drawWithContent {
                    if (!(exiting && state == LockState.Unlocked)) drawContent()
                }
                // An outgoing surface is also invisible to accessibility: the
                // fading calculator (or the undrawn vault) must not be a
                // TalkBack focus target while the incoming surface is live.
                .then(if (exiting) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            when (state) {
                LockState.NeedsSetup ->
                    // First run (and post-erase): the guide runs before the
                    // calculator ever appears. Only while NO passcode exists —
                    // the disguise is never preceded by an explainer once a
                    // vault is set up.
                    if (showOnboarding) {
                        OnboardingScreen(
                            mode = OnboardingMode.FIRST_RUN,
                            onFinish = {
                                // The mode-gated single writer: flips the
                                // in-memory flag now and persists in the
                                // lock-surviving scope (the composable is
                                // disposed the moment the switch flips).
                                recordOnboardingCompletion(
                                    mode = OnboardingMode.FIRST_RUN,
                                    lockManager = lockManager,
                                    onboardingStore = container.onboardingStore,
                                    scope = container.applicationScope,
                                )
                            },
                        )
                    } else {
                        LockCalculator(container, exiting = exiting)
                    }
                LockState.Locked -> LockCalculator(container, exiting = exiting)
                LockState.Unlocked -> VaultScaffold(container)
            }
            // An outgoing surface is inert: a tap during the reveal (or the
            // one-frame cut) must not fall through a region of the incoming
            // surface that has no pointer handling of its own — a top-bar
            // title, the calculator display — onto whatever sits underneath.
            if (exiting) {
                Box(modifier = Modifier.matchParentSize().pointerInput(Unit) {})
            }
        }
    }

    // One-time no-recovery notice over the just-revealed vault. It must not pop
    // over the reveal: currentState catches up with targetState only once the
    // transition — the 260 ms reveal, or the one-frame cut — has completed.
    val vaultSettled =
        lockTransition.currentState == LockState.Unlocked &&
            lockTransition.targetState == LockState.Unlocked
    if (showNotice && vaultSettled) {
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
private fun LockCalculator(container: AppContainer, exiting: Boolean) {
    val lockManager = container.lockManager
    val liveBanner by lockManager.banner.collectAsStateWithLifecycle()
    val liveEpoch by lockManager.calculatorEpoch.collectAsStateWithLifecycle()
    // The reveal fades the calculator out "in place", but the unlock commit
    // bumps calculatorEpoch — and a setup confirm clears the banner — in the
    // same step. An exiting calculator therefore holds its last frame instead
    // of repainting to a pristine "0" mid-fade. Should the target flip back
    // before the exit completes (a lock during the reveal), the surface is
    // reused as the target, unfreezes, and the bumped epoch recreates it
    // pristine exactly as before.
    //
    // The freeze is only correct because AppLockManager writes the side flows
    // and the lock state without a suspension point between them: `commit`
    // sets `_lockState = Unlocked` then `_calculatorEpoch += 1`, and the setup
    // confirm sets `_banner = null` … `_lockState = Unlocked` back to back.
    // `exiting` (derived from the lock state) therefore flips in the same
    // composition that would otherwise show the new epoch / cleared banner.
    // A suspension between those writes would let the pristine values land
    // one composition before the freeze engages, and the caption would blink.
    val epoch = rememberFrozenWhile(exiting, liveEpoch)
    val banner = rememberFrozenWhile(exiting, liveBanner)
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

/**
 * [value] while not [frozen]; once frozen, the last value seen before freezing.
 * A plain holder rather than snapshot state: nothing needs to observe it, the
 * caller already recomposes on the source of [value].
 */
@Composable
private fun <T> rememberFrozenWhile(frozen: Boolean, value: T): T {
    val latest = remember { Latest(value) }
    if (!frozen) latest.value = value
    return latest.value
}

private class Latest<T>(var value: T)
