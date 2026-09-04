package com.calcplus.calculator.feature.numpad

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * The PIN pad lock face (decisions §2.3). Overt: a wrong PIN shakes and clears,
 * like a phone lock screen — visible, but it says nothing about what it guards.
 *
 * The token IDs deliberately coincide with the calculator's digits: token IDs
 * are opaque *per alphabet*, salts differ per enrollment, and the overlap gives
 * the fail-closed calculator face a chance to still accept a digits-only PIN if
 * a face ever fails to resolve.
 */
object NumpadDisguise : DisguiseProvider {
    override val id: String = "numpad"

    override val alphabet: AlphabetDescriptor = AlphabetDescriptor(
        tokenSetId = id,
        alphabetVersion = 1,
        tokens = (0..9).map { "D$it" },
    )

    override val isCovert: Boolean = false

    override val displayName: Int = R.string.numpad_display_name
    override val tagline: Int = R.string.numpad_tagline
    override val commitGesture: Int = R.string.numpad_commit_gesture

    override val guide: DisguiseGuideContent = NumpadGuide

    @StringRes
    override fun captionRes(kind: CaptionKind): Int = when (kind) {
        CaptionKind.PROMPT_NEW_SETUP -> R.string.numpad_prompt_new
        CaptionKind.PROMPT_NEW_CHANGE -> R.string.numpad_prompt_new_change
        CaptionKind.STRENGTH_HINT -> R.string.numpad_hint
        CaptionKind.TOO_SHORT -> R.string.numpad_too_short
        CaptionKind.TOO_LONG -> R.string.numpad_too_long
        CaptionKind.PROMPT_CONFIRM_SETUP -> R.string.numpad_prompt_confirm
        CaptionKind.PROMPT_CONFIRM_CHANGE -> R.string.numpad_prompt_confirm_change
        CaptionKind.MISMATCH -> R.string.numpad_mismatch
        CaptionKind.TRIVIAL_WARNING -> R.string.numpad_trivial_warning
        CaptionKind.PROMPT_CURRENT -> R.string.numpad_prompt_current
        CaptionKind.WRONG_CODE -> R.string.numpad_wrong_code
    }

    @Composable
    override fun Surface(
        mode: DisguiseMode,
        caption: CaptionState?,
        failedAttemptToken: Int,
        events: (DisguiseEvent) -> Unit,
        modifier: Modifier,
    ) {
        NumpadScreen(
            mode = mode,
            caption = caption,
            failedAttemptToken = failedAttemptToken,
            events = events,
            modifier = modifier,
        )
    }

    @Composable
    override fun CoverFace() {
        NumpadCoverFace()
    }
}

private object NumpadGuide : DisguiseGuideContent {
    override val identityGrade = IdentityGrade.INCOHERENT
    override val page3Title = R.string.numpad_guide_page3_title
    override val page3Body = R.string.numpad_guide_page3_body
    override val page3Try = R.string.numpad_guide_try
    override val page3Ok = R.string.numpad_guide_ok
    override val page4Title = R.string.numpad_guide_page4_title
    override val page4Body = R.string.numpad_guide_page4_body
    override val a11yNote: Int? = null

    /** A compact 3×4 PIN pad filling a mini dot row. Only the count escapes. */
    @Composable
    override fun Playground(resetToken: Int, onCountChanged: (Int) -> Unit) {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        var count by remember { mutableIntStateOf(0) }
        LaunchedEffect(count) { onCountChanged(count) }
        LaunchedEffect(resetToken) { count = 0 }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.heightIn(min = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(minOf(count, 10)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(theme.displayText, CircleShape)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        row.forEach { digit ->
                            DemoDigit(digit, theme) { count += 1 }
                        }
                    }
                }
            }
        }
    }

    /** A pulsing ✓ key — same 0.85 s / 1.09× loop as the calculator's `=` hero. */
    @Composable
    override fun CommitHero() {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        val infinite = rememberInfiniteTransition(label = "checkPulse")
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
                .background(theme.keyFn, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = theme.keyLabel,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun DemoDigit(digit: Int, theme: DisguiseTheme, onTap: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(theme.keyDigit, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onTap()
            }
            .semantics { contentDescription = "demo key $digit" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            digit.toString(),
            color = theme.keyLabel,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
