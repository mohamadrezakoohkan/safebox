package com.calcplus.calculator.feature.calculator

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.disguise.DisguiseGuideContent
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.IdentityGrade
import com.calcplus.calculator.core.ui.theme.DisguiseTheme

/**
 * The calculator lock face (decisions §2.1) — re-homed, behavior unchanged.
 *
 * The only **covert** face: a non-match is silent, it never receives a
 * failed-attempt pulse in `disguise` mode, and its caption copy is the pinned
 * table from `calculator-disguise-design.md` §6, reused verbatim.
 */
object CalculatorDisguise : DisguiseProvider {
    override val id: String = "calculator"

    /**
     * The 17 passcode keys, in enum order. `=` and `AC`/`C` are gestures, not
     * tokens, and are deliberately absent.
     */
    override val alphabet: AlphabetDescriptor = AlphabetDescriptor(
        tokenSetId = id,
        alphabetVersion = 1,
        tokens = CalcKey.entries.filter { it.isPasscodeKey }.map { it.id },
    )

    override val isCovert: Boolean = true

    override val displayName: Int = R.string.calculator_display_name
    override val tagline: Int = R.string.calculator_tagline
    override val commitGesture: Int = R.string.calculator_commit_gesture

    override val guide: DisguiseGuideContent = CalculatorGuide

    /** Pinned strings, verbatim — design §6 remains the string authority. */
    @StringRes
    override fun captionRes(kind: CaptionKind): Int = when (kind) {
        CaptionKind.PROMPT_NEW_SETUP -> R.string.setup_entry_banner
        CaptionKind.PROMPT_NEW_CHANGE -> R.string.change_enter_new_caption
        CaptionKind.STRENGTH_HINT -> R.string.setup_entry_hint
        CaptionKind.TOO_SHORT -> R.string.setup_too_short
        CaptionKind.TOO_LONG -> R.string.setup_too_long
        CaptionKind.PROMPT_CONFIRM_SETUP -> R.string.setup_confirm_banner
        CaptionKind.PROMPT_CONFIRM_CHANGE -> R.string.change_confirm_caption
        CaptionKind.MISMATCH -> R.string.setup_mismatch
        CaptionKind.TRIVIAL_WARNING -> R.string.setup_trivial_warning
        CaptionKind.PROMPT_CURRENT -> R.string.verify_current_caption
        CaptionKind.WRONG_CODE -> R.string.verify_error
    }

    @Composable
    override fun Surface(
        mode: DisguiseMode,
        caption: CaptionState?,
        failedAttemptToken: Int,
        events: (DisguiseEvent) -> Unit,
        modifier: Modifier,
    ) {
        val session = remember(events) { CalculatorSession(events) }
        CalculatorScreen(
            session = session,
            // Never composed in `disguise` mode — the calculator's caption slot
            // stays empty, which is the whole point of a covert face.
            caption = if (mode == DisguiseMode.DISGUISE) null else caption,
            failedAttemptToken = failedAttemptToken,
            modifier = modifier,
        )
    }

    @Composable
    override fun CoverFace() {
        val session = remember { CalculatorSession {} }
        CalculatorScreen(session = session, caption = null)
    }
}

/** Guide pages 3 and 4 for the calculator (copy renamed per decisions §7). */
private object CalculatorGuide : DisguiseGuideContent {
    override val identityGrade = IdentityGrade.NATIVE
    override val page3Title = R.string.calculator_guide_page3_title
    override val page3Body = R.string.calculator_guide_page3_body
    override val page3Try = R.string.calculator_guide_try
    override val page3Ok = R.string.calculator_guide_ok
    override val page4Title = R.string.calculator_guide_page4_title
    override val page4Body = R.string.calculator_guide_page4_body
    override val a11yNote: Int? = null

    /**
     * A real mini keypad the user can tap to feel how a key-sequence code
     * works. Purely illustrative: only the tap COUNT leaves this composable —
     * never a label, never an order — and nothing is saved.
     */
    @Composable
    override fun Playground(resetToken: Int, onCountChanged: (Int) -> Unit) {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        val taps = remember { mutableStateListOf<String>() }
        LaunchedEffect(taps.size) { onCountChanged(taps.size) }
        LaunchedEffect(resetToken) { taps.clear() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Recorded-sequence chips (last 8 shown), each popping in with a spring.
            Row(
                modifier = Modifier.heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (taps.size > 8) {
                    Text("…", color = theme.caption, fontSize = 18.sp)
                }
                taps.takeLast(8).forEachIndexed { index, label ->
                    // Key on absolute position so existing chips don't re-animate.
                    androidx.compose.runtime.key(taps.size - minOf(taps.size, 8) + index) {
                        TapChip(label, theme)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val rows = listOf(
                    listOf("7" to false, "8" to false, "9" to false, "÷" to true),
                    listOf("4" to false, "5" to false, "6" to false, "×" to true),
                    listOf("1" to false, "2" to false, "3" to false, "+" to true),
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, isOp) ->
                            DemoKey(label, isOp, theme) { taps.add(label) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun CommitHero() {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        val infinite = rememberInfiniteTransition(label = "equalsPulse")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.09f,
            animationSpec = infiniteRepeatable(
                tween(850, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .background(theme.keyOp, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("=", color = theme.keyLabelOnOp, fontSize = 48.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TapChip(label: String, theme: DisguiseTheme) {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(theme.keyFn, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = theme.keyLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DemoKey(label: String, isOperator: Boolean, theme: DisguiseTheme, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rest = if (isOperator) theme.keyOp else theme.keyDigit
    val pressedFill = if (isOperator) theme.keyOpPressed else theme.keyDigitPressed
    val fill by animateColorAsState(
        targetValue = if (pressed) pressedFill else rest,
        animationSpec = if (pressed) snap() else tween(180),
        label = "demoKeyFill",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(fill, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onTap()
            }
            .semantics { contentDescription = "demo key $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isOperator) theme.keyLabelOnOp else theme.keyLabel,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
