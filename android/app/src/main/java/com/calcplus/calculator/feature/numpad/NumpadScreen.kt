package com.calcplus.calculator.feature.numpad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.disguise.DisguiseMode
import com.calcplus.calculator.core.disguise.FaceCaptionStrip
import com.calcplus.calculator.core.disguise.ShakeSpec
import com.calcplus.calculator.core.disguise.rememberShakeOffset
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The PIN pad lock face (decisions §2.3): caption slot, dot row, 3×4 circular
 * keypad, vertically centered — a lock screen, not a bottom-anchored keyboard.
 *
 * It is an **overt** face: a failed attempt shakes the dot row and clears it,
 * with no text in `disguise` mode. Nothing here is ever logged — not a digit,
 * not a count, not a mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumpadScreen(
    mode: DisguiseMode,
    caption: CaptionState?,
    failedAttemptToken: Int,
    events: (DisguiseEvent) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
    var entryCount by remember { mutableIntStateOf(0) }

    // The pulse: shake the dot row, hold, then clear the entry. The host has
    // already cleared its own buffer for this (overt) face.
    val shake = rememberShakeOffset(failedAttemptToken)
    val firstToken = remember { failedAttemptToken }
    LaunchedEffect(failedAttemptToken) {
        if (failedAttemptToken == firstToken) return@LaunchedEffect
        delay(ShakeSpec.OVERT_FAIL_HOLD_MS)
        entryCount = NumpadEntry.afterClear()
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
            // Side margin matches the calculator's `m`, so a switch keeps the
            // app's look (§2.2 rule 1).
            val m = (maxWidth * 0.04f).coerceIn(12.dp, 20.dp)
            val columnWidth = min((maxWidth - m * 2).value, NumpadEntry.COLUMN_MAX_WIDTH_DP).dp
            val keyDiameter = NumpadEntry.keyDiameter(columnWidth.value).dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // In `disguise` mode the face owns this slot with its own static
                // decoy title; in the three capture modes the host caption takes it.
                FaceCaptionStrip(
                    primary = when {
                        mode == DisguiseMode.DISGUISE -> stringResource(R.string.numpad_face_title)
                        caption != null -> stringResource(NumpadDisguise.captionRes(caption.primary))
                        else -> null
                    },
                    secondary = if (mode != DisguiseMode.DISGUISE) {
                        caption?.secondary?.let { stringResource(NumpadDisguise.captionRes(it)) }
                    } else {
                        null
                    },
                    isError = mode != DisguiseMode.DISGUISE && caption?.isError == true,
                    theme = theme,
                    horizontalPadding = m,
                )

                DotRow(
                    count = entryCount,
                    columnWidth = columnWidth,
                    theme = theme,
                    shake = shake,
                )

                Spacer(modifier = Modifier.height(NumpadEntry.DOTS_TO_KEYPAD_GAP_DP.dp))

                Keypad(
                    theme = theme,
                    columnWidth = columnWidth,
                    keyDiameter = keyDiameter,
                    enabled = interactive,
                    onDigit = { digit ->
                        entryCount = NumpadEntry.afterDigit(entryCount)
                        events(DisguiseEvent.Token("D$digit"))
                    },
                    onBackspace = {
                        entryCount = NumpadEntry.afterBackspace(entryCount)
                        events(DisguiseEvent.RemoveLast)
                    },
                    onClear = {
                        entryCount = NumpadEntry.afterClear()
                        events(DisguiseEvent.Clear)
                    },
                    onEnter = {
                        entryCount = NumpadEntry.afterCommit(entryCount, mode)
                        events(DisguiseEvent.Commit)
                    },
                )
            }
        }
    }
}

@Composable
private fun DotRow(count: Int, columnWidth: Dp, theme: DisguiseTheme, shake: Dp) {
    val dots = NumpadEntry.visibleDots(count)
    val (sizeDp, gapDp) = NumpadEntry.dotMetrics(dots, columnWidth.value)
    val enteredLabel = "entered digits"
    Row(
        modifier = Modifier
            .width(columnWidth)
            .heightIn(min = NumpadEntry.DOT_DIAMETER_DP.dp)
            .graphicsLayer { translationX = shake.toPx() }
            .testTag("numpad_dots")
            // One element, genre-neutral label, the count as its value. No
            // hints, no custom actions, no announcement on commit (§2.2 rule 8).
            .semantics(mergeDescendants = true) {
                contentDescription = enteredLabel
                stateDescription = dots.toString()
            },
        horizontalArrangement = Arrangement.spacedBy(gapDp.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dots) {
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(theme.displayText, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(
    theme: DisguiseTheme,
    columnWidth: Dp,
    keyDiameter: Dp,
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEnter: () -> Unit,
) {
    val gap = NumpadEntry.KEY_GAP_DP.dp
    Column(
        modifier = Modifier.width(columnWidth),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { digit ->
                    DigitKey(digit, keyDiameter, theme, enabled) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            BackspaceKey(keyDiameter, theme, enabled, onBackspace, onClear)
            DigitKey(0, keyDiameter, theme, enabled) { onDigit(0) }
            EnterKey(keyDiameter, theme, enabled, onEnter)
        }
    }
}

/**
 * Genre labels only, exactly like the calculator's ("seven", "equals"): no
 * hints, no custom actions, and no identifier or label containing
 * `passcode|vault|unlock|secret|lock|safebox` (§2.2 rule 8). Hardcoded for the
 * same reason the calculator's are — they are element names, not UI copy.
 */
private val DIGIT_LABELS = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
)

@Composable
private fun DigitKey(
    digit: Int,
    diameter: Dp,
    theme: DisguiseTheme,
    enabled: Boolean,
    onDown: () -> Unit,
) {
    KeyShell(
        diameter = diameter,
        rest = theme.keyDigit,
        pressedFill = theme.keyDigitPressed,
        label = DIGIT_LABELS[digit],
        tag = "numpad_key_$digit",
        enabled = enabled,
        onDown = onDown,
    ) {
        Text(
            digit.toString(),
            color = theme.keyLabel,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EnterKey(diameter: Dp, theme: DisguiseTheme, enabled: Boolean, onDown: () -> Unit) {
    KeyShell(
        diameter = diameter,
        rest = theme.keyFn,
        pressedFill = theme.keyFnPressed,
        label = "enter",
        tag = "numpad_key_enter",
        enabled = enabled,
        onDown = onDown,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = theme.keyLabel,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * ⌫ is the one key that is NOT a touch-down key: tap on release removes the
 * last token, a long press clears the whole entry once and swallows the
 * release (decisions §2.3).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceKey(
    diameter: Dp,
    theme: DisguiseTheme,
    enabled: Boolean,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) theme.keyFnPressed else theme.keyFn,
        animationSpec = if (pressed) snap() else tween(180),
        label = "numpadBackspaceFill",
    )
    val haptics = LocalHapticFeedback.current
    val label = "delete"
    Box(
        modifier = Modifier
            .size(diameter)
            .background(fill, CircleShape)
            .testTag("numpad_key_delete")
            .then(
                if (enabled) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = label,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onClear()
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onBackspace()
                        },
                    )
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Backspace,
            contentDescription = null,
            tint = theme.keyLabel,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * A circular key that fires on TOUCH-DOWN, with the calculator's press
 * feedback (0 ms down, 180 ms up) and the one identical haptic per key.
 */
@Composable
private fun KeyShell(
    diameter: Dp,
    rest: androidx.compose.ui.graphics.Color,
    pressedFill: androidx.compose.ui.graphics.Color,
    label: String,
    tag: String,
    enabled: Boolean,
    onDown: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) pressedFill else rest,
        animationSpec = if (pressed) snap() else tween(180),
        label = "numpadKeyFill",
    )
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(diameter)
            .background(fill, CircleShape)
            .testTag(tag)
            .then(
                if (enabled) {
                    Modifier.pointerInput(onDown) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val press = PressInteraction.Press(down.position)
                            scope.launch { interactionSource.emit(press) }
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onDown()
                            val up = waitForUpOrCancellation()
                            scope.launch {
                                interactionSource.emit(
                                    if (up != null) {
                                        PressInteraction.Release(press)
                                    } else {
                                        PressInteraction.Cancel(press)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The resting face: static title, empty dot row, keypad. Used for thumbnails. */
@Composable
fun NumpadCoverFace() {
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        NumpadScreen(
            mode = DisguiseMode.DISGUISE,
            caption = null,
            failedAttemptToken = 0,
            events = {},
            interactive = false,
        )
    }
}
