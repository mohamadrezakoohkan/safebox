package com.calcplus.calculator.core.data

import com.calcplus.calculator.core.lock.LockState
import kotlinx.coroutines.flow.Flow

/**
 * Persists the onboarding-complete sentinel when the FIRST envelope lands —
 * that is, on the `NeedsSetup → Unlocked` transition (iteration-3-decisions
 * §4), not when the guide finishes.
 *
 * Why the move: with the face now chosen in the guide, a process death between
 * "guide finished" and "envelope stored" would otherwise strand the user on a
 * face they can no longer choose. Writing the sentinel with the envelope means
 * the guide simply comes back.
 *
 * Like [TrashHousekeeping] this observes the lock state instead of hooking the
 * lock manager, so `AppLockManager` stays untouched. Only the transition *out
 * of* NeedsSetup counts: an ordinary unlock from Locked writes nothing.
 */
object OnboardingSentinelWriter {
    /** Collects forever; call it from a lock-surviving scope. */
    suspend fun persistOnFirstUnlock(
        lockState: Flow<LockState>,
        setComplete: suspend () -> Unit,
    ) {
        var previous: LockState? = null
        lockState.collect { state ->
            if (previous == LockState.NeedsSetup && state == LockState.Unlocked) {
                setComplete()
            }
            previous = state
        }
    }
}
