package com.calcplus.calculator.core.disguise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.core.ui.theme.DisguiseTheme

/**
 * The caption slot shared by the overt faces: design §5.5 metrics — 13 sp, min
 * height 28, centered, up to two lines.
 *
 * In `disguise` mode the host composes no caption at all; an overt face puts
 * its own static title here instead (decisions §2.2), which is why this takes
 * plain resolved strings rather than a [CaptionState].
 */
@Composable
fun FaceCaptionStrip(
    primary: String?,
    secondary: String?,
    isError: Boolean,
    theme: DisguiseTheme,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (primary != null) {
            Text(
                text = primary,
                color = if (isError) theme.captionError else theme.caption,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (secondary != null) {
            Text(
                text = secondary,
                color = theme.caption,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
