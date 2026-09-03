package com.calcplus.calculator.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.core.format.MediaFormatting

/**
 * The video affordance on a grid cell (decisions §9): a white play glyph
 * centered on the poster frame, plus the duration in a dark translucent pill at
 * the **bottom-start**. Bottom-end is reserved for the P6 selection indicator,
 * so the two never collide.
 *
 * The badge text comes from [MediaFormatting.duration] — the same function the
 * Details sheet uses, so a badge can never disagree with the sheet it opens.
 * Note that rounding can carry a 59.6 s clip up to `1:00` and an almost-hour
 * clip up to `1:00:00`, so the pill must tolerate the `h:mm:ss` form.
 *
 * @param durationMs null renders the play glyph with no pill (a video whose
 *   duration was never read — the glyph still says "this is a video").
 */
@Composable
fun VideoCellOverlay(durationMs: Long?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(VideoCellOverlayMetrics.GLYPH_CIRCLE_DP.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = VideoCellOverlayMetrics.SCRIM_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                // The cell already publishes the photo/video distinction through
                // the duration badge; a second announcement would just be noise.
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(VideoCellOverlayMetrics.GLYPH_DP.dp),
            )
        }
        if (durationMs != null) {
            Text(
                text = MediaFormatting.duration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(VideoCellOverlayMetrics.BADGE_INSET_DP.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = VideoCellOverlayMetrics.SCRIM_ALPHA))
                    .padding(
                        horizontal = VideoCellOverlayMetrics.BADGE_H_PADDING_DP.dp,
                        vertical = VideoCellOverlayMetrics.BADGE_V_PADDING_DP.dp,
                    ),
            )
        }
    }
}

/** The overlay's metrics, mirroring the iOS `VideoCellOverlay.Metrics`. */
object VideoCellOverlayMetrics {
    const val GLYPH_CIRCLE_DP = 26
    const val GLYPH_DP = 16
    const val BADGE_INSET_DP = 6
    const val BADGE_H_PADDING_DP = 6
    const val BADGE_V_PADDING_DP = 2
    const val SCRIM_ALPHA = 0.55f
}
