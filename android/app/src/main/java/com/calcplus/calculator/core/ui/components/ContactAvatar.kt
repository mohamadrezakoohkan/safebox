package com.calcplus.calculator.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AvatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00),
    Color(0xFF7E57C2), Color(0xFFC2185B), Color(0xFF00838F),
)

/**
 * The contacts list's initial-in-a-circle avatar, shared so the contacts tab and
 * a global-search contact row draw the same person the same colour (the colour
 * is derived from the id, so it must be derived in exactly one place).
 */
@Composable
fun ContactAvatar(
    contactId: String,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val color = AvatarPalette[Math.floorMod(contactId.hashCode(), AvatarPalette.size)]
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.take(1).uppercase().ifEmpty { "#" },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
