package com.calcplus.calculator.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.abs

/**
 * Zoomable image with the shared cross-platform constants: double-tap toggles
 * 1× / 2.5×, pinch max 5×, pan clamped to bounds, zoom resets on page change
 * (the caller keys this composable by page).
 */
@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        fun clampOffsets() {
            // Pan clamped to image bounds: content larger than the viewport by
            // (scale-1) on each axis.
            val maxX = (scale - 1f) * maxWidthPx / 2f
            val maxY = (scale - 1f) * maxHeightPx / 2f
            offsetX = offsetX.coerceIn(-abs(maxX), abs(maxX))
            offsetY = offsetY.coerceIn(-abs(maxY), abs(maxY))
        }

        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                                offsetX = (maxWidthPx / 2f - tap.x) * (scale - 1f)
                                offsetY = (maxHeightPx / 2f - tap.y) * (scale - 1f)
                                clampOffsets()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets()
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
