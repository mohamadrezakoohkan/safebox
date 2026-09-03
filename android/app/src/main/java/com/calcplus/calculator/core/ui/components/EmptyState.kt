package com.calcplus.calculator.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Free vertical space above the block, relative to [EMPTY_STATE_SPACE_BELOW] (decisions §2). */
internal const val EMPTY_STATE_SPACE_ABOVE = 1f

/** Free vertical space below the block, relative to [EMPTY_STATE_SPACE_ABOVE] (decisions §2). */
internal const val EMPTY_STATE_SPACE_BELOW = 1.35f

/**
 * The decisions-§2 optical center: the block is centered horizontally and the free vertical
 * space splits [EMPTY_STATE_SPACE_ABOVE] : [EMPTY_STATE_SPACE_BELOW] (1.0 above : 1.35 below),
 * which lifts it slightly above true center against top bars and FABs. [BiasAlignment] offsets a
 * child by `free × (1 + bias) / 2`, so `bias = (above − below) / (above + below)` reproduces the
 * split exactly — the same guide the iOS `EmptyStateView` aligns on.
 */
internal val EmptyStateOpticalCenter: Alignment = BiasAlignment(
    horizontalBias = 0f,
    verticalBias = (EMPTY_STATE_SPACE_ABOVE - EMPTY_STATE_SPACE_BELOW) /
        (EMPTY_STATE_SPACE_ABOVE + EMPTY_STATE_SPACE_BELOW),
)

/**
 * The shared empty-state layout (decisions §2; dp ≡ pt with the iOS `EmptyStateView`): an 88 dp
 * `surfaceContainerHigh` circle with a 40 dp `onSurfaceVariant` glyph → 20 → `titleLarge` title →
 * 8 → `bodyMedium` secondary description → 24 → filled button, 32 dp horizontal padding, the
 * block optically centered in the visible area ([EmptyStateOpticalCenter]).
 *
 * If the block is taller than the visible area — a large font scale, or the keyboard up behind a
 * search field — the area scrolls instead of clipping the button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The centering area is exactly the visible height; a taller block extends it, and the
        // extension is what scrolls. Without a height bound (never the case for the vault's
        // screens) there is nothing to center in or scroll within, so the block simply stacks.
        val bounded = constraints.hasBoundedHeight
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bounded) {
                        Modifier
                            .verticalScroll(scrollState)
                            .heightIn(min = maxHeight)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(EmptyStateOpticalCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                description?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/**
 * [EmptyState] driven by a [VaultEmptyStates] entry: glyph, title and body come from the
 * catalog, and the action button renders only when [content] carries an action label and
 * [onAction] is supplied — so a screen can always pass its handler and let the content decide.
 */
@Composable
fun EmptyState(
    content: EmptyStateContent,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    EmptyState(
        icon = content.icon,
        title = stringResource(content.title),
        modifier = modifier,
        description = stringResource(content.body),
        actionLabel = content.action?.let { stringResource(it) },
        onAction = onAction,
    )
}
