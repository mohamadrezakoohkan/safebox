package com.calcplus.calculator.feature.pattern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.disguise.FaceCaptionStrip
import com.calcplus.calculator.core.disguise.ShakeSpec
import com.calcplus.calculator.core.disguise.rememberShakeOffset
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * The pattern lock face (decisions §2.4): caption slot above a centered 3×3
 * grid drawn in one stroke. Overt — a wrong pattern shakes the whole grid and
 * clears it.
 *
 * The gesture loop uses [awaitEachGesture] rather than `detectDragGestures`
 * deliberately: touch slop would swallow a plain node tap and delay the first
 * token until the finger had already travelled. Nothing in this loop is ever
 * logged — not a coordinate, not a node index, not a length.
 */
@Composable
fun PatternScreen(
    mode: DisguiseMode,
    caption: CaptionState?,
    failedAttemptToken: Int,
    events: (DisguiseEvent) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
    val selected = remember { mutableStateListOf<Int>() }
    var livePoint by remember { mutableStateOf<Offset?>(null) }
    var failing by remember { mutableStateOf(false) }

    val shake = rememberShakeOffset(failedAttemptToken)
    val firstToken = remember { failedAttemptToken }
    LaunchedEffect(failedAttemptToken) {
        if (failedAttemptToken == firstToken) return@LaunchedEffect
        failing = true
        delay(ShakeSpec.OVERT_FAIL_HOLD_MS)
        selected.clear()
        livePoint = null
        failing = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val m = (maxWidth * 0.04f).coerceIn(12.dp, 20.dp)
            val side = min((maxWidth - m * 2).value, PatternGeometry.GRID_MAX_DP).dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FaceCaptionStrip(
                    primary = when {
                        mode == DisguiseMode.DISGUISE -> stringResource(R.string.pattern_face_title)
                        caption != null -> stringResource(PatternDisguise.captionRes(caption.primary))
                        else -> null
                    },
                    secondary = if (mode != DisguiseMode.DISGUISE) {
                        caption?.secondary?.let { stringResource(PatternDisguise.captionRes(it)) }
                    } else {
                        null
                    },
                    isError = mode != DisguiseMode.DISGUISE && caption?.isError == true,
                    theme = theme,
                    horizontalPadding = m,
                )
                Spacer(modifier = Modifier.height(PatternGeometry.CAPTION_GAP_DP.dp))

                PatternGrid(
                    side = side,
                    selected = selected,
                    livePoint = livePoint,
                    failing = failing,
                    theme = theme,
                    shake = shake,
                    interactive = interactive,
                    onStrokeStart = {
                        selected.clear()
                        livePoint = null
                        events(DisguiseEvent.Clear)
                    },
                    onNodes = { nodes ->
                        nodes.forEach { node ->
                            selected.add(node)
                            events(DisguiseEvent.Token(PatternGeometry.tokenFor(node)))
                        }
                    },
                    onLive = { livePoint = it },
                    onLift = {
                        livePoint = null
                        // A stroke that touched no node is not a pattern: no
                        // commit, no event. This is the face defining its own
                        // commit gesture, not a length rule.
                        if (selected.isNotEmpty()) {
                            events(DisguiseEvent.Commit)
                            if (mode == DisguiseMode.CAPTURE_NEW || mode == DisguiseMode.CONFIRM_NEW) {
                                selected.clear()
                            }
                        }
                    },
                    onCancel = {
                        selected.clear()
                        livePoint = null
                        events(DisguiseEvent.Clear)
                    },
                )
            }
        }
    }
}

@Composable
private fun PatternGrid(
    side: androidx.compose.ui.unit.Dp,
    selected: List<Int>,
    livePoint: Offset?,
    failing: Boolean,
    theme: DisguiseTheme,
    shake: androidx.compose.ui.unit.Dp,
    interactive: Boolean,
    onStrokeStart: () -> Unit,
    onNodes: (List<Int>) -> Unit,
    onLive: (Offset) -> Unit,
    onLift: () -> Unit,
    onCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val cellPx = with(density) { side.toPx() } / 3f
    val haptics = LocalHapticFeedback.current
    // A genre label, like the calculator's element names — not UI copy.
    val gridLabel = "pattern grid"
    val nodeRest = with(density) { PatternGeometry.NODE_RESTING_DP.dp.toPx() }
    val nodeSelected = with(density) { PatternGeometry.NODE_SELECTED_DP.dp.toPx() }
    val lineWidth = with(density) { PatternGeometry.LINE_WIDTH_DP.dp.toPx() }
    val activeColor = if (failing) theme.captionError else theme.keyOp

    // A single element with a genre label. Deliberately NOT operable by
    // synthesized activation: a stroke cannot be synthesized, and the
    // consequence is disclosed on the face's carousel card (§2.4).
    Box(
        modifier = Modifier
            .size(side)
            .graphicsLayer { translationX = shake.toPx() }
            .testTag("pattern_grid")
            .semantics(mergeDescendants = true) { contentDescription = gridLabel }
            .then(
                if (interactive) {
                    Modifier.pointerInput(cellPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Touch down anywhere on the grid resets the path —
                            // this also recovers from a stroke that ended in a
                            // system cancel.
                            onStrokeStart()
                            val path = mutableListOf<Int>()
                            fun visit(position: Offset) {
                                onLive(position)
                                val node = PatternGeometry.nodeAt(position.x, position.y, cellPx)
                                    ?: return
                                val added = PatternGeometry.nodesToSelect(path, node)
                                if (added.isEmpty()) return
                                path.addAll(added)
                                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onNodes(added)
                            }
                            visit(down.position)
                            var lifted = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        lifted = true
                                        change.consume()
                                        break
                                    }
                                    visit(change.position)
                                    change.consume()
                                }
                            } finally {
                                if (lifted) onLift() else onCancel()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Connecting line: node to node, plus a live segment to the finger.
            if (selected.isNotEmpty()) {
                val lineColor = activeColor.copy(alpha = PatternGeometry.LINE_ALPHA)
                for (i in 0 until selected.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = Offset(
                            PatternGeometry.centerX(selected[i], cellPx),
                            PatternGeometry.centerY(selected[i], cellPx),
                        ),
                        end = Offset(
                            PatternGeometry.centerX(selected[i + 1], cellPx),
                            PatternGeometry.centerY(selected[i + 1], cellPx),
                        ),
                        strokeWidth = lineWidth,
                    )
                }
                livePoint?.let { point ->
                    val last = selected.last()
                    drawLine(
                        color = lineColor,
                        start = Offset(
                            PatternGeometry.centerX(last, cellPx),
                            PatternGeometry.centerY(last, cellPx),
                        ),
                        end = point,
                        strokeWidth = lineWidth,
                    )
                }
            }
            for (node in 0..8) {
                val isSelected = node in selected
                drawCircle(
                    color = if (isSelected) activeColor else theme.keyFn,
                    radius = (if (isSelected) nodeSelected else nodeRest) / 2f,
                    center = Offset(
                        PatternGeometry.centerX(node, cellPx),
                        PatternGeometry.centerY(node, cellPx),
                    ),
                )
            }
        }
    }
}

/** The resting face: static title and nine resting nodes. Used for thumbnails. */
@Composable
fun PatternCoverFace() {
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        PatternScreen(
            mode = DisguiseMode.DISGUISE,
            caption = null,
            failedAttemptToken = 0,
            events = {},
            interactive = false,
        )
    }
}
