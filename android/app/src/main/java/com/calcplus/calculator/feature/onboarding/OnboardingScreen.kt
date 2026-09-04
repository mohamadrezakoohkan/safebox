package com.calcplus.calculator.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.DisguiseProvider
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.lock.PasscodeRules
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import com.calcplus.calculator.feature.disguise.CarouselMode
import com.calcplus.calculator.feature.disguise.DisguiseCarousel
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4
private val SuccessGreen = Color(0xFF4ADE80)

/**
 * The guide: which disguise to wear, what the app really is, and how a code
 * works on the chosen face.
 *
 * [OnboardingMode.FIRST_RUN] shows it while no passcode exists (fresh install /
 * post-erase), before any lock face ever appears — once a vault is set up the
 * disguise is never preceded by an explainer. Page 1 is the picker (decisions
 * §6) and pages 3–4 bind live to whichever card is centered.
 * [OnboardingMode.REVISIT] re-opens the same pages from Settings inside the
 * unlocked vault, locked on the current face; there every finish path is a
 * plain dismissal and nothing is written.
 *
 * This composable never touches first-run state itself: [onFinish] is the
 * caller's, and it carries the selected face id — "Skip" passes whatever card
 * is centered at that moment (decisions §4).
 */
@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    registry: DisguiseRegistry,
    currentFace: DisguiseProvider,
    onFinish: (selectedDisguiseId: String) -> Unit,
) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGE_COUNT - 1
    val showsTrailingButton = !isLast || mode.showsTrailingButtonOnLastPage

    // A revisit is locked on the enrolled face; the first run starts on the
    // registry default and follows the carousel.
    var selectedId by remember(mode, currentFace.id) {
        mutableStateOf(
            if (mode == OnboardingMode.REVISIT) currentFace.id else registry.default.id
        )
    }
    val selectedFace = registry.resolve(selectedId)

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
                    TextButton(onClick = { onFinish(selectedId) }) {
                        Text(stringResource(mode.trailingButtonLabel), color = theme.caption, fontSize = 15.sp)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> DisguisePickerPage(
                        registry = registry,
                        mode = mode,
                        current = currentFace,
                        selectedId = selectedId,
                        onSelectedChange = { selectedId = it },
                        theme = theme,
                    )
                    1 -> VaultPage(pagerState, theme)
                    // Pages 3 and 4 belong to the selected face and are
                    // recreated whole when the selection changes, so a
                    // playground never carries state across faces.
                    2 -> key(selectedFace.id) { FaceCodePage(selectedFace, theme) }
                    else -> key(selectedFace.id) { FaceCommitPage(selectedFace, theme) }
                }
            }

            PageDots(pagerState, theme)

            Button(
                onClick = {
                    if (isLast) {
                        onFinish(selectedId)
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

// MARK: Page 1 — the disguise carousel

@Composable
private fun DisguisePickerPage(
    registry: DisguiseRegistry,
    mode: OnboardingMode,
    current: DisguiseProvider,
    selectedId: String,
    onSelectedChange: (String) -> Unit,
    theme: DisguiseTheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_disguise_title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_disguise_body),
            color = theme.caption,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        DisguiseCarousel(
            registry = registry,
            mode = if (mode == OnboardingMode.REVISIT) CarouselMode.REVISIT else CarouselMode.FIRST_RUN,
            current = current,
            selectedId = selectedId,
            onSelectedChange = onSelectedChange,
            theme = theme,
        )
        Spacer(modifier = Modifier.height(24.dp))
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

// MARK: Page 3 — the selected face's interactive code playground

@Composable
private fun FaceCodePage(face: DisguiseProvider, theme: DisguiseTheme) {
    var count by remember { mutableIntStateOf(0) }
    var resetToken by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.7f))
        Text(
            stringResource(face.guide.page3Title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(face.guide.page3Body),
            color = theme.caption,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(0.4f))

        // The shared 4 progress pips: the host's minimum, on every face.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(PasscodeRules.MIN_TOKENS) { i ->
                val filled = count > i
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
        AnimatedContent(
            targetState = count >= PasscodeRules.MIN_TOKENS,
            label = "playgroundCaption",
        ) { enough ->
            Text(
                stringResource(if (enough) face.guide.page3Ok else face.guide.page3Try),
                color = if (enough) SuccessGreen else theme.caption,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        face.guide.Playground(resetToken = resetToken, onCountChanged = { count = it })

        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = { resetToken += 1 }) {
            Text(stringResource(R.string.onboarding_page3_clear), color = theme.caption, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: Page 4 — the commit gesture and the no-recovery warning

@Composable
private fun FaceCommitPage(face: DisguiseProvider, theme: DisguiseTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.9f))
        face.guide.CommitHero()
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            stringResource(face.guide.page4Title),
            color = theme.displayText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(face.guide.page4Body),
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
