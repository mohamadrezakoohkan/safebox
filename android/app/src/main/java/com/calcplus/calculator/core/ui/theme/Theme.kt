package com.calcplus.calculator.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFB45309)

// Deliberately NOT dynamic color: the palette is fixed by the design spec and
// must not re-tint per user wallpaper.
private val LightColors = lightColorScheme(
    primary = Amber,
    secondary = Color(0xFF5A6069),
    tertiary = Color(0xFF43484F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD97706),
    secondary = Color(0xFFA9AFB8),
    tertiary = Color(0xFF565B63),
)

@Composable
fun SafeBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
