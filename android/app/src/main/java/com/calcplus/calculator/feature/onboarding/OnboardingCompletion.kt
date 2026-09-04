package com.calcplus.calculator.feature.onboarding

import com.calcplus.calculator.core.lock.AppLockManager

/**
 * The one place that turns "the guide finished" into first-run state, gated by
 * [OnboardingMode.recordsCompletion] (the Android twin of iOS
 * `OnboardingSentinel.recordCompletion(for:)`).
 *
 * [OnboardingMode.FIRST_RUN]: flips the lock manager's in-memory flag at once
 * (the root switch leaves the guide for the setup face in the same
 * composition) and records the face the user picked, so setup runs on it.
 *
 * **The persisted sentinel is NOT written here** (iteration-3-decisions §4).
 * It moves to an observer of the `NeedsSetup → Unlocked` transition, so a
 * process death between finishing the guide and storing the first envelope
 * cannot strand the user on a face they can no longer choose — the guide simply
 * comes back.
 *
 * [OnboardingMode.REVISIT]: does nothing at all. The revisit path in
 * `VaultScaffold` never even calls this; the gate exists so that no future
 * caller can record completion from a revisit by accident. Call this, never
 * `AppLockManager.completeOnboarding()` directly, whenever the guide finishes.
 *
 * @return true when completion was recorded, false when the mode forbids it
 *   (nothing was touched).
 */
fun recordOnboardingCompletion(
    mode: OnboardingMode,
    lockManager: AppLockManager,
    selectedDisguiseId: String,
): Boolean {
    if (!mode.recordsCompletion) return false
    lockManager.completeOnboarding(selectedDisguiseId)
    return true
}
