package com.calcplus.calculator.feature.calculator

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.core.disguise.CaptionState
import com.calcplus.calculator.core.disguise.DisguiseEvent
import com.calcplus.calculator.core.disguise.rememberShakeOffset
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import kotlin.math.min

/**
 * Transient calculator state: the arithmetic engine plus the translation from
 * key presses to the host's [DisguiseEvent] stream. Recreated pristine per
 * surface epoch. It owns no buffer — the host's `TokenRecorder` does.
 */
class CalculatorSession(
    private val events: (DisguiseEvent) -> Unit,
) {
    private val engine = CalculatorEngine()

    var display by mutableStateOf("0")
        private set
    var ringOperator by mutableStateOf<CalcOperator?>(null)
        private set
    var showsAllClear by mutableStateOf(true)
        private set

    fun press(key: CalcKey) {
        // Engine FIRST, always — the arithmetic result renders regardless of
        // what the host makes of the event, and that is the whole disguise.
        engine.press(key)
        sync()
        when {
            key == CalcKey.EQUALS -> events(DisguiseEvent.Commit)
            key == CalcKey.CLEAR -> events(DisguiseEvent.Clear)
            key.isPasscodeKey -> events(DisguiseEvent.Token(key.id))
        }
    }

    private fun sync() {
        display = engine.display
        ringOperator = engine.ringOperator
        showsAllClear = engine.showsAllClear
    }
}

/**
 * The full calculator face: caption strip + display + 4×5 keypad, laid out per
 * the disguise design spec §2.2 (height-band-driven keypad metrics). One
 * component serves the lock screen, first-run setup, and the change flow.
 *
 * The semantic [caption] is resolved here, through the calculator's own pinned
 * copy ([CalculatorDisguise.captionRes]) — the host never carries strings.
 */
@Composable
fun CalculatorScreen(
    session: CalculatorSession,
    caption: CaptionState?,
    failedAttemptToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
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
            val s = maxWidth
            val h = maxHeight
            // §2.2 metrics: side margin, gutters, key size (height band driver).
            val m = (s * 0.04f).coerceIn(12.dp, 20.dp)
            val contentWidth = min((s - m * 2).value, 480f).dp
            val g = (contentWidth * 0.03f).coerceIn(8.dp, 14.dp)
            val k = (contentWidth - g * 3) / 4
            val usable = h - g
            var keyH = (usable * 0.65f - g * 4) / 5
            var gv = g
            if (keyH > k) {
                keyH = k
                val bandFloor = usable * 0.62f
                val block = keyH * 5 + gv * 4
                if (block < bandFloor) {
                    gv = min(((bandFloor - keyH * 5) / 4).value, (g * 1.5f).value).dp
                }
            }
            keyH = keyH.coerceIn(44.dp, 96.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Caption strip: only composed when a caption exists — never in
                // pure Disguise mode.
                if (caption != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp)
                            .padding(horizontal = m, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(CalculatorDisguise.captionRes(caption.primary)),
                            color = if (caption.isError) theme.captionError else theme.caption,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        caption.secondary?.let {
                            Text(
                                text = stringResource(CalculatorDisguise.captionRes(it)),
                                color = theme.caption,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                // Display readout: right-aligned, single line, auto-shrink.
                ShakableDisplay(
                    text = session.display,
                    color = theme.displayText,
                    contentWidth = contentWidth,
                    sideMargin = m,
                    gutter = g,
                    failedAttemptToken = failedAttemptToken,
                )

                // Keypad block, bottom-anchored.
                Keypad(
                    session = session,
                    theme = theme,
                    keyWidth = k,
                    keyHeight = keyH,
                    gutter = g,
                    verticalGutter = gv,
                    contentWidth = contentWidth,
                )
                Spacer(modifier = Modifier.height(g))
            }
        }
    }
}

@Composable
private fun ShakableDisplay(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentWidth: Dp,
    sideMargin: Dp,
    gutter: Dp,
    failedAttemptToken: Int,
) {
    // ±8dp, 3 cycles, 300ms (design spec §5.6). The loop, the fire-on-change
    // rule and the density conversion all live in rememberShakeOffset.
    val shake = rememberShakeOffset(failedAttemptToken)
    val fontSize = (contentWidth.value / 5.2f).sp
    Text(
        text = text,
        color = color,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            fontFeatureSettings = "tnum", // tabular digits — no jitter while typing
        ),
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier
            .width(contentWidth)
            .padding(horizontal = sideMargin, vertical = gutter)
            .graphicsLayer { translationX = shake.toPx() }
            .semantics { contentDescription = "result" },
    )
}

@Composable
private fun Keypad(
    session: CalculatorSession,
    theme: DisguiseTheme,
    keyWidth: Dp,
    keyHeight: Dp,
    gutter: Dp,
    verticalGutter: Dp,
    contentWidth: Dp,
) {
    val clearLabel = if (session.showsAllClear) "AC" else "C"
    Column(
        modifier = Modifier.width(contentWidth),
        verticalArrangement = Arrangement.spacedBy(verticalGutter),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            CalcKeyButton(clearLabel, if (session.showsAllClear) "all clear" else "clear", KeyKind.FN, keyWidth, keyHeight, theme) { session.press(CalcKey.CLEAR) }
            CalcKeyButton("±", "plus minus", KeyKind.FN, keyWidth, keyHeight, theme) { session.press(CalcKey.SIGN) }
            CalcKeyButton("%", "percent", KeyKind.FN, keyWidth, keyHeight, theme) { session.press(CalcKey.PCT) }
            CalcKeyButton("÷", "divide", KeyKind.OP, keyWidth, keyHeight, theme, showRing = session.ringOperator == CalcOperator.DIV) { session.press(CalcKey.DIV) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            CalcKeyButton("7", "seven", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D7) }
            CalcKeyButton("8", "eight", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D8) }
            CalcKeyButton("9", "nine", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D9) }
            CalcKeyButton("×", "multiply", KeyKind.OP, keyWidth, keyHeight, theme, showRing = session.ringOperator == CalcOperator.MUL) { session.press(CalcKey.MUL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            CalcKeyButton("4", "four", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D4) }
            CalcKeyButton("5", "five", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D5) }
            CalcKeyButton("6", "six", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D6) }
            CalcKeyButton("−", "minus", KeyKind.OP, keyWidth, keyHeight, theme, showRing = session.ringOperator == CalcOperator.SUB) { session.press(CalcKey.SUB) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            CalcKeyButton("1", "one", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D1) }
            CalcKeyButton("2", "two", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D2) }
            CalcKeyButton("3", "three", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.D3) }
            CalcKeyButton("+", "plus", KeyKind.OP, keyWidth, keyHeight, theme, showRing = session.ringOperator == CalcOperator.ADD) { session.press(CalcKey.ADD) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            // Double-width zero: spans 2k + g; glyph centered on the first column.
            CalcKeyButton(
                "0", "zero", KeyKind.DIGIT, keyWidth * 2 + gutter, keyHeight, theme,
                glyphCenterFraction = (keyWidth / 2) / (keyWidth * 2 + gutter),
            ) { session.press(CalcKey.D0) }
            CalcKeyButton(".", "decimal point", KeyKind.DIGIT, keyWidth, keyHeight, theme) { session.press(CalcKey.DOT) }
            CalcKeyButton("=", "equals", KeyKind.OP, keyWidth, keyHeight, theme) { session.press(CalcKey.EQUALS) }
        }
    }
}

enum class KeyKind { DIGIT, FN, OP }

@Composable
private fun CalcKeyButton(
    label: String,
    a11yLabel: String,
    kind: KeyKind,
    width: Dp,
    height: Dp,
    theme: DisguiseTheme,
    showRing: Boolean = false,
    glyphCenterFraction: Float = 0.5f,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rest: androidx.compose.ui.graphics.Color
    val pressedFill: androidx.compose.ui.graphics.Color
    val labelColor: androidx.compose.ui.graphics.Color
    when (kind) {
        KeyKind.DIGIT -> { rest = theme.keyDigit; pressedFill = theme.keyDigitPressed; labelColor = theme.keyLabel }
        KeyKind.FN -> { rest = theme.keyFn; pressedFill = theme.keyFnPressed; labelColor = theme.keyLabel }
        KeyKind.OP -> { rest = theme.keyOp; pressedFill = theme.keyOpPressed; labelColor = theme.keyLabelOnOp }
    }
    // Instant fill on press-down; 180ms ease-out on release. No scale, no shadow.
    val fill by animateColorAsState(
        targetValue = if (pressed) pressedFill else rest,
        animationSpec = if (pressed) snap() else tween(180),
        label = "keyFill",
    )
    val shape = RoundedCornerShape(height * 0.24f)
    val haptics = LocalHapticFeedback.current
    val fontSize = when (kind) {
        KeyKind.DIGIT -> 32.sp
        KeyKind.FN -> 26.sp
        KeyKind.OP -> 34.sp
    }
    Box(
        modifier = Modifier
            .size(width, height)
            .background(fill, shape)
            .then(
                // Pending-operator ring: outline only, no fill change (§4.9).
                if (showRing) Modifier.border(2.dp, theme.keyOpActiveRing, shape) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = a11yLabel,
            ) {
                // One identical haptic for every key, every mode, every outcome.
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onClick()
            }
            .semantics { contentDescription = a11yLabel },
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(
                if (glyphCenterFraction == 0.5f) Alignment.Center else Alignment.CenterStart
            ).then(
                if (glyphCenterFraction == 0.5f) Modifier
                else Modifier.padding(
                    // Clamped: Compose throws on a negative padding, and the
                    // subtraction goes negative whenever the key is narrower
                    // than ~20dp — which a small-but-legal render (a carousel
                    // thumbnail) produces. A crash in the lock face is the
                    // worst possible failure, so never let it be reachable.
                    start = (width * glyphCenterFraction - 10.dp).coerceAtLeast(0.dp),
                )
            ),
        )
    }
}
