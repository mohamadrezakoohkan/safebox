package com.calcplus.calculator.feature.onboarding

import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one place that turns "the guide finished" into first-run state, gated by
 * [OnboardingMode.recordsCompletion] (the Android twin of iOS
 * `OnboardingSentinel.recordCompletion(for:)`).
 *
 * [OnboardingMode.FIRST_RUN]: flips the lock manager's in-memory flag at once
 * (the root switch leaves the guide for the setup calculator in the same
 * composition) and persists the flag in [scope] — the caller passes the
 * lock-surviving application scope, because the guide composable is disposed
 * the moment the switch flips.
 *
 * [OnboardingMode.REVISIT]: does nothing at all. The revisit path in
 * `VaultScaffold` never even calls this; the gate exists so that no future
 * caller can persist completion from a revisit by accident. Call this, never
 * `AppLockManager.completeOnboarding()` / `OnboardingStore.setComplete()`
 * directly, whenever the guide finishes.
 *
 * @return the persistence job when completion was recorded, `null` when the
 *   mode forbids recording (nothing was touched).
 */
fun recordOnboardingCompletion(
    mode: OnboardingMode,
    lockManager: AppLockManager,
    onboardingStore: OnboardingStore,
    scope: CoroutineScope,
): Job? {
    if (!mode.recordsCompletion) return null
    lockManager.completeOnboarding()
    return scope.launch { onboardingStore.setComplete() }
}
