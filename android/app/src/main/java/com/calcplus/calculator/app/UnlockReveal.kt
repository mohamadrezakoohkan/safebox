package com.calcplus.calculator.app

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.calcplus.calculator.core.lock.LockState

/**
 * The calculator → vault "zoom-in reveal" (iteration-2 decisions §1): the one
 * place that owns the reveal's constants and the rule deciding when it plays.
 * Mirrors iOS `enum UnlockReveal`; the numbers are shared verbatim.
 *
 * The rule is deliberately narrow. A change INTO [LockState.Unlocked] — from
 * [LockState.Locked] or from first-run setup ([LockState.NeedsSetup]) — is the
 * only lock-state change that animates. Locking in any form (manual, background,
 * erase), setup ↔ locked, and every `calculatorEpoch` bump are instant cuts:
 * the calculator's recreation must never read as a transition.
 *
 * Android has no reduce-motion flag. Its only signal is the developer option
 * "Remove animations", i.e. `Settings.Global.ANIMATOR_DURATION_SCALE == 0`,
 * which means *no animation at all* — so the reveal becomes a cut there
 * (iOS, which does have `accessibilityReduceMotion`, drops only the scale).
 */
object UnlockReveal {
    /** Reveal length at the default animator scale. Shared with iOS (`UnlockReveal.durationMs`). */
    const val UNLOCK_REVEAL_DURATION_MS = 260

    /** The vault starts at this scale and settles at 1.0; the calculator only fades. */
    const val INITIAL_SCALE = 0.92f

    /** Emphasized-decelerate, cubic-bezier(0.05, 0.7, 0.1, 1.0). Shared with iOS. */
    val EASING: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * Z order of a surface brought in by a [CUT][Kind.CUT] — always a locked
     * surface (calculator or first-run guide), or a vault entering with
     * animations off. Strictly above [REVEAL_Z_INDEX] so that, on a lock, the
     * calculator is drawn OVER the vault from the very first frame: the
     * outgoing vault stays composed for one frame before it is disposed, and
     * not one of its pixels may show through.
     */
    const val CUT_Z_INDEX = 1f

    /**
     * Z order of the vault brought in by a [REVEAL][Kind.REVEAL]. Below the
     * calculator it replaces, which is the depth the motion describes: the
     * calculator fades away in front, the vault grows up from behind it.
     */
    const val REVEAL_Z_INDEX = 0f

    /** What a lock-state change looks like on screen. */
    enum class Kind {
        /** Vault fades in while scaling [INITIAL_SCALE] → 1.0; calculator fades out in place. */
        REVEAL,

        /** Instant switch; nothing animates. */
        CUT,
    }

    /**
     * Pure decision. [REVEAL][Kind.REVEAL] only when [to] is [LockState.Unlocked],
     * [from] is not, and system animations are on ([animatorDurationScale] > 0).
     * Every other combination — including a same-state "change", which is what
     * an epoch bump looks like at this level — is a [CUT][Kind.CUT].
     */
    fun kind(from: LockState, to: LockState, animatorDurationScale: Float): Kind =
        if (to == LockState.Unlocked && from != LockState.Unlocked && animatorDurationScale > 0f) {
            Kind.REVEAL
        } else {
            Kind.CUT
        }

    /**
     * The system "Animator duration scale". `0` is the "Remove animations"
     * developer option; an unset value reads as the platform default `1`.
     * Read at each lock-state change (not cached) so toggling the option while
     * the app runs is honored without a restart.
     */
    fun animatorDurationScale(context: Context): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )

    /**
     * The `AnimatedContent` transform for a [kind]. Never animates size
     * (`sizeTransform = null`): both surfaces fill the window, and a size
     * animation would read as a glitch.
     *
     * The CUT does not rely on `ExitTransition.None`. `AnimatedContent` keeps
     * tracking an in-flight enter (the vault's fade+scale) when the target
     * flips back mid-reveal, and with no exit of its own the outgoing surface
     * would then *spring* from its partial alpha/scale to fully opaque over
     * ~0.5 s before being disposed. Snapping alpha to 0 (and scale to 1, so
     * the tracked scale-in has an explicit zero-length target too) gives the
     * exit a zero-length target, which is what pins the FAST DISPOSAL of the
     * outgoing surface: on a plain lock it is gone on the next frame.
     *
     * The snap is NOT what guarantees that no vault pixel shows. When a lock
     * interrupts a running reveal, animation-core's `Transition` replaces a
     * non-spring spec on an in-flight animation with its interruption spring,
     * so the vault's alpha eases to 0 over ~150–300 ms instead of snapping,
     * and the reused calculator springs from its mid-fade alpha back to 1
     * (~400 ms) — the vault would be visible THROUGH the translucent
     * calculator for those frames. That case is closed by the draw guard in
     * `SafeBoxApp` (`drawWithContent` skips an exiting Unlocked surface),
     * which does not depend on the alpha animation at all. Together: the snap
     * keeps disposal quick, the draw guard keeps the vault invisible.
     */
    fun contentTransform(kind: Kind): ContentTransform = when (kind) {
        Kind.REVEAL -> ContentTransform(
            targetContentEnter = fadeIn(spec()) + scaleIn(spec(), initialScale = INITIAL_SCALE),
            initialContentExit = fadeOut(spec()),
            targetContentZIndex = REVEAL_Z_INDEX,
            sizeTransform = null,
        )
        Kind.CUT -> ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = fadeOut(cutSpec()) + scaleOut(cutSpec(), targetScale = 1f),
            targetContentZIndex = CUT_Z_INDEX,
            sizeTransform = null,
        )
    }

    private fun spec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = UNLOCK_REVEAL_DURATION_MS, easing = EASING)

    /** Zero-duration: the value is the target from the first animated frame. */
    private fun cutSpec(): FiniteAnimationSpec<Float> = snap()
}
