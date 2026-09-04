package com.calcplus.calculator.feature.pattern

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.disguise.DisguiseGuideContent
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.CoverAliases
import com.calcplus.calculator.core.ui.theme.DisguiseTheme

/**
 * The pattern lock face (decisions §2.4). Overt, and the only face that is not
 * usable with a screen reader — a stroke cannot be synthesized, which is
 * disclosed on its carousel and picker card via `pattern_a11y_note`.
 *
 * A node cannot repeat within a stroke, so a commit carries 1–9 tokens and
 * overflow is impossible by construction; `TOO_LONG` and `TRIVIAL_WARNING` are
 * therefore unreachable and map defensively to the current mode's prompt.
 */
object PatternDisguise : DisguiseProvider {
    override val id: String = "pattern"

    override val alphabet: AlphabetDescriptor = AlphabetDescriptor(
        tokenSetId = id,
        alphabetVersion = 1,
        tokens = PatternGeometry.tokens,
    )

    override val isCovert: Boolean = false

    override val displayName: Int = R.string.pattern_display_name
    override val tagline: Int = R.string.pattern_tagline
    override val commitGesture: Int = R.string.pattern_commit_gesture

    /**
     * Cover identity (§9a): Gallery+. A locked photo gallery is ordinary, and
     * a pattern is exactly how an Android app of that kind locks.
     */
    override val coverAlias: String = CoverAliases.GALLERY
    override val coverName: Int = R.string.cover_name_gallery

    override val guide: DisguiseGuideContent = PatternGuide

    @StringRes
    override fun captionRes(kind: CaptionKind): Int = when (kind) {
        CaptionKind.PROMPT_NEW_SETUP -> R.string.pattern_prompt_new
        CaptionKind.PROMPT_NEW_CHANGE -> R.string.pattern_prompt_new_change
        CaptionKind.STRENGTH_HINT -> R.string.pattern_hint
        CaptionKind.TOO_SHORT -> R.string.pattern_too_short
        CaptionKind.PROMPT_CONFIRM_SETUP -> R.string.pattern_prompt_confirm
        CaptionKind.PROMPT_CONFIRM_CHANGE -> R.string.pattern_prompt_confirm_change
        CaptionKind.MISMATCH -> R.string.pattern_mismatch
        CaptionKind.PROMPT_CURRENT -> R.string.pattern_prompt_current
        CaptionKind.WRONG_CODE -> R.string.pattern_wrong_code
        // Unreachable by construction; never leave the strip empty.
        CaptionKind.TOO_LONG -> R.string.pattern_prompt_new
        CaptionKind.TRIVIAL_WARNING -> R.string.pattern_hint
    }

    @Composable
    override fun Surface(
        mode: DisguiseMode,
        caption: CaptionState?,
        failedAttemptToken: Int,
        events: (DisguiseEvent) -> Unit,
        modifier: Modifier,
    ) {
        PatternScreen(
            mode = mode,
            caption = caption,
            failedAttemptToken = failedAttemptToken,
            events = events,
            modifier = modifier,
        )
    }

    @Composable
    override fun CoverFace() {
        PatternCoverFace()
    }
}

private object PatternGuide : DisguiseGuideContent {
    override val page3Title = R.string.pattern_guide_page3_title
    override val page3Body = R.string.pattern_guide_page3_body
    override val page3Try = R.string.pattern_guide_try
    override val page3Ok = R.string.pattern_guide_ok
    override val page4Title = R.string.pattern_guide_page4_title
    override val page4Body = R.string.pattern_guide_page4_body
    override val a11yNote: Int = R.string.pattern_a11y_note

    /** A compact drawable 3×3 grid. Only the node COUNT leaves the demo. */
    @Composable
    override fun Playground(resetToken: Int, onCountChanged: (Int) -> Unit) {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        val selected = remember { mutableStateListOf<Int>() }
        var live by remember { mutableStateOf<Offset?>(null) }
        LaunchedEffect(selected.size) { onCountChanged(selected.size) }
        LaunchedEffect(resetToken) { selected.clear() }
        val side = 180.dp
        val density = LocalDensity.current
        val cell = with(density) { side.toPx() } / 3f
        val nodeRest = with(density) { PatternGeometry.NODE_RESTING_DP.dp.toPx() } * 0.75f
        val nodeOn = with(density) { PatternGeometry.NODE_SELECTED_DP.dp.toPx() } * 0.75f
        val lineWidth = with(density) { PatternGeometry.LINE_WIDTH_DP.dp.toPx() } * 0.75f
        val haptics = LocalHapticFeedback.current
        Box(
            modifier = Modifier
                .size(side)
                .semantics { contentDescription = "demo pattern grid" }
                .pointerInput(cell) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        selected.clear()
                        val path = mutableListOf<Int>()
                        fun visit(position: Offset) {
                            live = position
                            val node = PatternGeometry.nodeAt(position.x, position.y, cell) ?: return
                            val added = PatternGeometry.nodesToSelect(path, node)
                            if (added.isEmpty()) return
                            path.addAll(added)
                            selected.addAll(added)
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        }
                        visit(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            visit(change.position)
                            change.consume()
                        }
                        live = null
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = theme.keyOp.copy(alpha = PatternGeometry.LINE_ALPHA)
                for (i in 0 until selected.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = Offset(
                            PatternGeometry.centerX(selected[i], cell),
                            PatternGeometry.centerY(selected[i], cell),
                        ),
                        end = Offset(
                            PatternGeometry.centerX(selected[i + 1], cell),
                            PatternGeometry.centerY(selected[i + 1], cell),
                        ),
                        strokeWidth = lineWidth,
                    )
                }
                live?.let { point ->
                    selected.lastOrNull()?.let { last ->
                        drawLine(
                            color = lineColor,
                            start = Offset(
                                PatternGeometry.centerX(last, cell),
                                PatternGeometry.centerY(last, cell),
                            ),
                            end = point,
                            strokeWidth = lineWidth,
                        )
                    }
                }
                for (node in 0..8) {
                    val on = node in selected
                    drawCircle(
                        color = if (on) theme.keyOp else theme.keyFn,
                        radius = (if (on) nodeOn else nodeRest) / 2f,
                        center = Offset(
                            PatternGeometry.centerX(node, cell),
                            PatternGeometry.centerY(node, cell),
                        ),
                    )
                }
            }
        }
    }

    /** A looping finger path over a 3×3 grid, ending in a lift. */
    @Composable
    override fun CommitHero() {
        val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
        val infinite = rememberInfiniteTransition(label = "patternHero")
        val progress by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(2400, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "path",
        )
        val side = 104.dp
        val density = LocalDensity.current
        val cell = with(density) { side.toPx() } / 3f
        val nodeRest = with(density) { 10.dp.toPx() }
        val nodeOn = with(density) { 16.dp.toPx() }
        val lineWidth = with(density) { 4.dp.toPx() }
        // 0 → 3 → 6 → 7 → 4: an "L" with a turn, held briefly before the lift.
        val route = listOf(0, 3, 6, 7, 4)
        Box(modifier = Modifier.size(side)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // The last 20% of the loop is the "lift": the path fades out.
                val drawFraction = (progress / 0.8f).coerceAtMost(1f)
                val lifted = progress > 0.8f
                val travelled = drawFraction * (route.size - 1)
                val complete = travelled.toInt()
                val alpha = if (lifted) 1f - (progress - 0.8f) / 0.2f else 1f
                val lineColor = theme.keyOp.copy(alpha = PatternGeometry.LINE_ALPHA * alpha)
                for (i in 0 until complete) {
                    drawLine(
                        color = lineColor,
                        start = Offset(
                            PatternGeometry.centerX(route[i], cell),
                            PatternGeometry.centerY(route[i], cell),
                        ),
                        end = Offset(
                            PatternGeometry.centerX(route[i + 1], cell),
                            PatternGeometry.centerY(route[i + 1], cell),
                        ),
                        strokeWidth = lineWidth,
                    )
                }
                if (complete < route.size - 1) {
                    val t = travelled - complete
                    val from = route[complete]
                    val to = route[complete + 1]
                    val sx = PatternGeometry.centerX(from, cell)
                    val sy = PatternGeometry.centerY(from, cell)
                    val ex = PatternGeometry.centerX(to, cell)
                    val ey = PatternGeometry.centerY(to, cell)
                    drawLine(
                        color = lineColor,
                        start = Offset(sx, sy),
                        end = Offset(sx + (ex - sx) * t, sy + (ey - sy) * t),
                        strokeWidth = lineWidth,
                    )
                }
                for (node in 0..8) {
                    val index = route.indexOf(node)
                    val on = index in 0..complete && alpha > 0f
                    drawCircle(
                        color = if (on) theme.keyOp.copy(alpha = alpha) else theme.keyFn,
                        radius = (if (on) nodeOn else nodeRest) / 2f,
                        center = Offset(
                            PatternGeometry.centerX(node, cell),
                            PatternGeometry.centerY(node, cell),
                        ),
                    )
                }
            }
        }
    }
}
