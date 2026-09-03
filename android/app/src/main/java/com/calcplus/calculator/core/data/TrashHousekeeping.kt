package com.calcplus.calculator.core.data

import com.calcplus.calculator.core.lock.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Expiry housekeeping for "Recently deleted" (decisions §3): purge runs **at
 * app start** and **on every transition to Unlocked**.
 *
 * It observes the lock state instead of hooking the lock manager, so
 * `AppLockManager` stays untouched. The flow is a `StateFlow`, which never
 * re-emits the same value, so one collect per unlock is exactly one purge.
 */
object TrashHousekeeping {
    /**
     * Collects forever; call it from a lock-surviving scope. [purge] receives
     * the instant the transition happened, so a long-running process purges
     * against a fresh "now" every time rather than the app-start one.
     */
    suspend fun purgeExpiredOnUnlock(
        lockState: Flow<LockState>,
        now: () -> Long,
        purge: suspend (Long) -> Unit,
    ) {
        lockState.filter { it == LockState.Unlocked }.collect { purge(now()) }
    }
}
