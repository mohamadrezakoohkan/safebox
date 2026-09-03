package com.calcplus.calculator.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4
private val SuccessGreen = Color(0xFF4ADE80)

/**
 * The guide: what the app really is and how the key-sequence passcode works.
 * [OnboardingMode.FIRST_RUN] shows it while no passcode exists (fresh install /
 * post-erase), before the calculator ever appears — once a vault is set up the
 * disguise is never preceded by an explainer. [OnboardingMode.REVISIT] re-opens
 * the same pages from Settings inside the unlocked vault; there every finish
 * path is a plain dismissal (decisions §5). This composable never touches
 * first-run state itself: [onFinish] is the caller's, and persisting completion
 * goes through [recordOnboardingCompletion], which the mode gates.
 * Styled with the disguise palette so it flows straight into the calculator.
 */
@Composable
fun OnboardingScreen(mode: OnboardingMode, onFinish: () -> Unit) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGE_COUNT - 1
    // Top-right: Skip on the first run (hidden on the last page so the CTA is
    // the only way forward), Done on every page of a revisit.
    val showsTrailingButton = !isLast || mode.showsTrailingButtonOnLastPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = showsTrailingButton, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(mode.trailingButtonLabel), color = theme.caption, fontSize = 15.sp)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> DisguisePage(pagerState, theme)
                    1 -> VaultPage(pagerState, theme)
                    2 -> CodePlaygroundPage(theme)
                    else -> EqualsPage(theme)
                }
            }

            PageDots(pagerState, theme)

            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.keyOp,
                    contentColor = theme.keyLabelOnOp,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .height(54.dp),
            ) {
                AnimatedContent(targetState = isLast, label = "ctaLabel") { last ->
                    Text(
                        stringResource(if (last) mode.finalCtaLabel else R.string.onboarding_next),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// MARK: Shared page scaffold

/** Hero drifts at a different rate than the page (parallax) while swiping. */
private fun Modifier.heroParallax(pagerState: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    translationX = offset * size.width * 0.4f
    alpha = 1f - (if (offset < 0) -offset else offset).coerceIn(0f, 1f) * 0.5f
}

@Composable
private fun PageColumn(
    theme: DisguiseTheme,
    title: String,
    body: String,
    hero: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.9f))
        hero()
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            title,
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            body,
            color = theme.caption,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: Page 1 — the disguise (flip card)

@Composable
private fun DisguisePage(pagerState: PagerState, theme: DisguiseTheme) {
    PageColumn(
        theme = theme,
        title = stringResource(R.string.onboarding_page1_title),
        body = stringResource(R.string.onboarding_page1_body),
    ) {
        val rotation = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1700)
                rotation.animateTo(
                    rotation.value + 180f,
                    tween(durationMillis = 650, easing = FastOutSlowInEasing),
                )
            }
        }
        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val showingLock = normalized > 90f && normalized < 270f
        Box(
            modifier = Modifier
                .heroParallax(pagerState, 0)
                .size(148.dp)
                .graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 14f * density
                }
                .background(
                    if (showingLock) theme.keyOp else theme.keyDigit,
                    RoundedCornerShape(34.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (showingLock) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    // Back face: mirror the content so it reads correctly.
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer { rotationY = 180f },
                )
            } else {
                Text("=", color = theme.keyLabel, fontSize = 64.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// MARK: Page 2 — what's inside (staggered feature cards)

@Composable
private fun VaultPage(pagerState: PagerState, theme: DisguiseTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.9f))
        val cards = listOf(
            Triple(Icons.Filled.Photo, R.string.onboarding_page2_photos, R.string.onboarding_page2_photos_sub),
            Triple(Icons.AutoMirrored.Filled.Note, R.string.onboarding_page2_notes, R.string.onboarding_page2_notes_sub),
            Triple(Icons.Filled.Person, R.string.onboarding_page2_contacts, R.string.onboarding_page2_contacts_sub),
        )
        val settled = pagerState.settledPage == 1
        Column(
            modifier = Modifier.heroParallax(pagerState, 1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cards.forEachIndexed { index, (icon, titleRes, subRes) ->
                AnimatedVisibility(
                    visible = settled,
                    enter = fadeIn(tween(350, delayMillis = index * 140)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(420, delayMillis = index * 140),
                        ),
                    exit = fadeOut(tween(120)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.keyDigit, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(theme.keyOp, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                stringResource(titleRes),
                                color = theme.keyLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(stringResource(subRes), color = theme.caption, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            stringResource(R.string.onboarding_page2_title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_page2_body),
            color = theme.caption,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: Page 3 — interactive code playground

/**
 * A real mini keypad the user can tap to feel how a key-sequence code works.
 * Purely illustrative: nothing leaves this composable, and the page's state is
 * discarded the moment it scrolls out of the pager viewport.
 */
@Composable
private fun CodePlaygroundPage(theme: DisguiseTheme) {
    val taps = remember { mutableStateListOf<String>() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.7f))
        Text(
            stringResource(R.string.onboarding_page3_title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_page3_body),
            color = theme.caption,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(0.5f))

        // Recorded-sequence chips (last 8 shown), each popping in with a spring.
        Row(
            modifier = Modifier.heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (taps.size > 8) {
                Text("…", color = theme.caption, fontSize = 18.sp)
            }
            taps.takeLast(8).forEachIndexed { index, label ->
                // Key on absolute position so existing chips don't re-animate.
                androidx.compose.runtime.key(taps.size - minOf(taps.size, 8) + index) {
                    TapChip(label, theme)
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // 4 progress pips → green check caption once the demo code is long enough.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i ->
                val filled = taps.size > i
                val color by animateColorAsState(
                    if (filled) SuccessGreen else theme.keyFn,
                    label = "pip$i",
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        AnimatedContent(targetState = taps.size >= 4, label = "playgroundCaption") { enough ->
            Text(
                stringResource(if (enough) R.string.onboarding_page3_ok else R.string.onboarding_page3_try),
                color = if (enough) SuccessGreen else theme.caption,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val rows = listOf(
                listOf("7" to false, "8" to false, "9" to false, "÷" to true),
                listOf("4" to false, "5" to false, "6" to false, "×" to true),
                listOf("1" to false, "2" to false, "3" to false, "+" to true),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, isOp) ->
                        DemoKey(label, isOp, theme) { taps.add(label) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = { taps.clear() }) {
            Text(stringResource(R.string.onboarding_page3_clear), color = theme.caption, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TapChip(label: String, theme: DisguiseTheme) {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(theme.keyFn, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = theme.keyLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DemoKey(label: String, isOperator: Boolean, theme: DisguiseTheme, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rest = if (isOperator) theme.keyOp else theme.keyDigit
    val pressedFill = if (isOperator) theme.keyOpPressed else theme.keyDigitPressed
    val fill by animateColorAsState(
        targetValue = if (pressed) pressedFill else rest,
        animationSpec = if (pressed) snap() else tween(180),
        label = "demoKeyFill",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(fill, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onTap()
            }
            .semantics { contentDescription = "demo key $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isOperator) theme.keyLabelOnOp else theme.keyLabel,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: Page 4 — the = ritual and no-recovery warning

@Composable
private fun EqualsPage(theme: DisguiseTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.9f))
        val infinite = rememberInfiniteTransition(label = "equalsPulse")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.09f,
            animationSpec = infiniteRepeatable(
                tween(850, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .background(theme.keyOp, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("=", color = theme.keyLabelOnOp, fontSize = 48.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            stringResource(R.string.onboarding_page4_title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_page4_body),
            color = theme.caption,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.keyDigit, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = theme.keyOpActiveRing,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.onboarding_page4_warning),
                color = theme.keyLabel,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: Indicator

@Composable
private fun PageDots(pagerState: PagerState, theme: DisguiseTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(PAGE_COUNT) { i ->
            val active = pagerState.currentPage == i
            val width by animateDpAsState(
                if (active) 26.dp else 8.dp,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth$i",
            )
            val color by animateColorAsState(
                if (active) theme.keyOp else theme.keyFn,
                label = "dotColor$i",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(width)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}
