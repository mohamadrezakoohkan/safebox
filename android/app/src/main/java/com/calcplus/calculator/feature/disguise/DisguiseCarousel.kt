package com.calcplus.calculator.feature.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.disguise.IdentityGrade
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** The three jobs one carousel does (decisions §6). */
enum class CarouselMode { FIRST_RUN, REVISIT, PICK }

private val CARD_WIDTH = 256.dp

/**
 * 424, not the 340 originally pinned in decisions §8. A fixed-size Column clips
 * rather than grows, so this must clear the WORST case: the pattern card while
 * it is the current face, i.e. 16 padding + 224 thumbnail + 10 spacer + name +
 * "Current" badge + a three-line tagline + the identity grade + the
 * screen-reader note + 16 padding. Measured from a device dump those five text
 * rows are 63 + 63 + 135 + 63 + 63 px at density 2.625, so the card needs
 * 250 + 147 + 16 ≈ 413dp; 424 leaves a real margin without going gangly.
 * The tagline is additionally capped at three lines as a backstop.
 */
private val CARD_HEIGHT = 424.dp
private val CARD_SPACING = 12.dp
private val THUMB_WIDTH = 126.dp
private val THUMB_HEIGHT = 224.dp
private const val THUMB_SCALE = 0.35f
private val VIRTUAL_CANVAS_WIDTH = 360.dp
private val VIRTUAL_CANVAS_HEIGHT = 640.dp

/**
 * The disguise picker (decisions §6): one snapping card per registry entry, in
 * registry order. **The centered (snapped) card is the selection** — no tap
 * required.
 *
 * @param current the enrolled face; badged in [CarouselMode.REVISIT] and
 *   [CarouselMode.PICK], and the one the revisit mode is locked on.
 */
@Composable
fun DisguiseCarousel(
    registry: DisguiseRegistry,
    mode: CarouselMode,
    current: DisguiseProvider,
    selectedId: String,
    onSelectedChange: (String) -> Unit,
    theme: DisguiseTheme,
    modifier: Modifier = Modifier,
) {
    val faces = registry.faces
    val startIndex = faces.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val scrollable = mode != CarouselMode.REVISIT

    // The carousel must never hand leftover horizontal scroll to the outer
    // onboarding pager: overscrolling the last card would otherwise flip the
    // page. The sink swallows whatever the LazyRow did not consume; page swipes
    // come from the rest of the page and the Next button.
    val horizontalSink = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sidePadding = ((maxWidth - CARD_WIDTH) / 2).coerceAtLeast(0.dp)

        // The snapped card is whichever one's center sits closest to the
        // viewport center.
        val centeredIndex by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { item ->
                    val center = item.offset + item.size / 2
                    if (center > viewportCenter) center - viewportCenter else viewportCenter - center
                }?.index
            }
        }
        LaunchedEffect(listState, faces) {
            snapshotFlow { centeredIndex }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { index -> faces.getOrNull(index)?.let { onSelectedChange(it.id) } }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.nestedScroll(horizontalSink)) {
                LazyRow(
                    state = listState,
                    userScrollEnabled = scrollable,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                ) {
                    itemsIndexed(faces) { index, face ->
                        DisguiseCard(
                            face = face,
                            isCurrent = mode != CarouselMode.FIRST_RUN && face.id == current.id,
                            // Revisit is locked on the current face: the
                            // neighbours are visible but dimmed.
                            dimmed = mode == CarouselMode.REVISIT && face.id != current.id,
                            theme = theme,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.disguise_identity_disclosure),
                color = theme.caption,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (mode == CarouselMode.REVISIT) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.onboarding_disguise_revisit_hint),
                    color = theme.caption,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun DisguiseCard(
    face: DisguiseProvider,
    isCurrent: Boolean,
    dimmed: Boolean,
    theme: DisguiseTheme,
) {
    Column(
        modifier = Modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .alpha(if (dimmed) 0.45f else 1f)
            .background(theme.keyDigit, RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FaceThumbnail(face)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(face.displayName),
            color = theme.keyLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (isCurrent) {
            Text(
                stringResource(R.string.disguise_current_badge),
                color = theme.keyOp,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            stringResource(face.tagline),
            color = theme.caption,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            // Backstop: at very large font scales even 380 runs out, and a
            // half-clipped glyph looks broken. Ellipsize instead.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                when (face.guide.identityGrade) {
                    IdentityGrade.NATIVE -> R.string.disguise_grade_native
                    IdentityGrade.PLAUSIBLE, IdentityGrade.INCOHERENT ->
                        R.string.disguise_grade_incoherent
                }
            ),
            color = theme.caption,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        face.guide.a11yNote?.let { note ->
            Text(
                stringResource(note),
                color = theme.caption,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The face's own resting screen, rendered into a 360×640 virtual canvas and
 * scaled into the card frame. Completely inert: no pointer input reaches it and
 * it is invisible to accessibility (the card, not the miniature, is the target).
 */
@Composable
private fun FaceThumbnail(face: DisguiseProvider) {
    Box(
        modifier = Modifier
            .size(THUMB_WIDTH, THUMB_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {}
            .clearAndSetSemantics {},
    ) {
        key(face.id) {
            Box(
                modifier = Modifier
                    // requiredSize, NOT size: a plain `size` is only a
                    // preference and stays bounded by the 126×224 parent, so
                    // the "virtual canvas" would silently measure at thumbnail
                    // size and the face would lay itself out for a 126dp-wide
                    // screen (which crashed the calculator keypad outright).
                    .requiredSize(VIRTUAL_CANVAS_WIDTH, VIRTUAL_CANVAS_HEIGHT)
                    .graphicsLayer {
                        scaleX = THUMB_SCALE
                        scaleY = THUMB_SCALE
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                face.CoverFace()
            }
        }
    }
}
