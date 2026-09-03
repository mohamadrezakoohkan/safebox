package com.calcplus.calculator.feature.onboarding

import androidx.annotation.StringRes
import com.calcplus.calculator.R

/**
 * The context the guide is shown in (decisions §5). The screen renders the same
 * four pages either way; the mode decides the button labels, whether the
 * top-right button survives the last page, and — the part that matters —
 * whether finishing may touch first-run state at all. Mirrors iOS
 * `OnboardingMode`.
 */
enum class OnboardingMode {
    /**
     * Fresh install / post-erase: the guide precedes the setup calculator.
     * Finishing (or skipping) records "onboarding complete".
     */
    FIRST_RUN,

    /**
     * Re-launched from Settings inside the unlocked vault. Finishing or
     * dismissing only returns to Settings; neither the persisted flag nor the
     * lock manager's in-memory flag is ever written.
     */
    REVISIT;

    /**
     * Whether finishing the guide records onboarding as complete. Only the
     * first run does — a revisit happens after the flag is already set and
     * must leave it (and `AppLockManager.completeOnboarding()`) alone.
     * Enforced by [recordOnboardingCompletion], the single writer.
     */
    val recordsCompletion: Boolean
        get() = this == FIRST_RUN

    /**
     * Whether the top-right button stays on the final page. The first run
     * hides Skip there so the CTA ("Set my code") is the only way forward; a
     * revisit keeps Done on every page because the guide is dismissible at any
     * point.
     */
    val showsTrailingButtonOnLastPage: Boolean
        get() = this == REVISIT

    /** Top-right button: Skip on the first run, Done on a revisit. */
    @get:StringRes
    val trailingButtonLabel: Int
        get() = when (this) {
            FIRST_RUN -> R.string.onboarding_skip
            REVISIT -> R.string.onboarding_done
        }

    /**
     * Final-page CTA: "Set my code" leads into setup on the first run; a
     * revisit has nothing to set up, so it reads Done like the top-right button.
     */
    @get:StringRes
    val finalCtaLabel: Int
        get() = when (this) {
            FIRST_RUN -> R.string.onboarding_start
            REVISIT -> R.string.onboarding_done
        }
}
