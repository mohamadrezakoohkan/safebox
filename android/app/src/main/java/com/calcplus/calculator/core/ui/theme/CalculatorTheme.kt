package com.calcplus.calculator.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Token palette from the disguise design spec §3.1. Graphite-blue background,
 * rounded-rect keys, burnt-amber operators — deliberately neither Apple's nor
 * Google's calculator trade dress. Identical values to iOS.
 */
data class DisguiseTheme(
    val background: Color,
    val displayText: Color,
    val keyDigit: Color,
    val keyDigitPressed: Color,
    val keyFn: Color,
    val keyFnPressed: Color,
    val keyOp: Color,
    val keyOpPressed: Color,
    val keyOpActiveRing: Color,
    val keyLabel: Color,
    val keyLabelOnOp: Color,
    val caption: Color,
    val captionError: Color,
) {
    companion object {
        val Dark = DisguiseTheme(
            background = Color(0xFF17191C),
            displayText = Color(0xFFF5F6F7),
            keyDigit = Color(0xFF2A2D33),
            keyDigitPressed = Color(0xFF3A3E46),
            keyFn = Color(0xFF43484F),
            keyFnPressed = Color(0xFF565B63),
            keyOp = Color(0xFFB45309),
            keyOpPressed = Color(0xFFD97706),
            keyOpActiveRing = Color(0xFFF7C77E),
            keyLabel = Color(0xFFF5F6F7),
            keyLabelOnOp = Color.White,
            caption = Color(0xFFA9AFB8),
            captionError = Color(0xFFE5484D),
        )

        val Light = DisguiseTheme(
            background = Color(0xFFF2F3F5),
            displayText = Color(0xFF1A1C1F),
            keyDigit = Color(0xFFFFFFFF),
            keyDigitPressed = Color(0xFFE2E5EA),
            keyFn = Color(0xFFD9DDE3),
            keyFnPressed = Color(0xFFC4C9D1),
            keyOp = Color(0xFFB45309),
            keyOpPressed = Color(0xFF92400E),
            keyOpActiveRing = Color(0xFFF7C77E),
            keyLabel = Color(0xFF1A1C1F),
            keyLabelOnOp = Color.White,
            caption = Color(0xFF5A6069),
            captionError = Color(0xFFB3261E),
        )
    }
}
