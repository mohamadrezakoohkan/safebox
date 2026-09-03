package com.calcplus.calculator.onboarding

import com.calcplus.calculator.R
import com.calcplus.calculator.feature.onboarding.OnboardingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the decisions-§5 revisit rules as pure properties of [OnboardingMode]
 * (mirrors iOS `OnboardingModeTests`). No Compose runtime, no framework — the
 * `R.string` IDs are plain constants.
 */
class OnboardingModeTest {

    // Whether finishing may touch first-run state.

    @Test
    fun firstRunRecordsCompletion() {
        assertTrue(OnboardingMode.FIRST_RUN.recordsCompletion)
    }

    @Test
    fun revisitNeverRecordsCompletion() {
        assertFalse(OnboardingMode.REVISIT.recordsCompletion)
    }

    @Test
    fun exactlyOneModeRecordsCompletion() {
        assertEquals(listOf(OnboardingMode.FIRST_RUN), OnboardingMode.entries.filter { it.recordsCompletion })
    }

    // The top-right button on the last page.

    @Test
    fun firstRunHidesTheTrailingButtonOnTheLastPage() {
        // Skip disappears so "Set my code" is the only way forward.
        assertFalse(OnboardingMode.FIRST_RUN.showsTrailingButtonOnLastPage)
    }

    @Test
    fun revisitKeepsDoneOnEveryPage() {
        assertTrue(OnboardingMode.REVISIT.showsTrailingButtonOnLastPage)
    }

    // Labels.

    @Test
    fun firstRunLabelsAreSkipAndSetMyCode() {
        assertEquals(R.string.onboarding_skip, OnboardingMode.FIRST_RUN.trailingButtonLabel)
        assertEquals(R.string.onboarding_start, OnboardingMode.FIRST_RUN.finalCtaLabel)
    }

    @Test
    fun revisitReadsDoneTopRightAndAsTheFinalCta() {
        assertEquals(R.string.onboarding_done, OnboardingMode.REVISIT.trailingButtonLabel)
        assertEquals(R.string.onboarding_done, OnboardingMode.REVISIT.finalCtaLabel)
    }

    @Test
    fun revisitNeverShowsSkipOrSetMyCode() {
        val revisitLabels = setOf(OnboardingMode.REVISIT.trailingButtonLabel, OnboardingMode.REVISIT.finalCtaLabel)
        assertFalse(R.string.onboarding_skip in revisitLabels)
        assertFalse(R.string.onboarding_start in revisitLabels)
    }
}
