package com.calcplus.calculator.data

import com.calcplus.calculator.core.data.TrashHousekeeping
import com.calcplus.calculator.core.lock.LockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Expiry purge scheduling (decisions §3): at app start (the caller's own first
 * call) and on **every transition to Unlocked**, never on a lock.
 */
class TrashHousekeepingTest {
    @Test
    fun purgesOnEveryTransitionToUnlockedAndOnNothingElse() = runTest {
        val lockState = MutableStateFlow<LockState>(LockState.Locked)
        val purgedAt = mutableListOf<Long>()
        var clock = 100L

        backgroundScope.launch {
            TrashHousekeeping.purgeExpiredOnUnlock(lockState, { clock }) { purgedAt.add(it) }
        }
        runCurrent()
        assertEquals(emptyList<Long>(), purgedAt) // starting locked purges nothing

        lockState.value = LockState.Unlocked
        runCurrent()
        assertEquals(listOf(100L), purgedAt)

        lockState.value = LockState.Locked
        runCurrent()
        assertEquals(listOf(100L), purgedAt) // locking never purges

        clock = 500L
        lockState.value = LockState.Unlocked
        runCurrent()
        assertEquals("each unlock purges against a fresh now", listOf(100L, 500L), purgedAt)

        lockState.value = LockState.NeedsSetup
        lockState.value = LockState.Locked
        runCurrent()
        assertEquals(2, purgedAt.size)
    }

    @Test
    fun aVaultThatStartsUnlockedPurgesOnce() = runTest {
        val lockState = MutableStateFlow<LockState>(LockState.Unlocked)
        var purges = 0

        backgroundScope.launch {
            TrashHousekeeping.purgeExpiredOnUnlock(lockState, { 0L }) { purges += 1 }
        }
        runCurrent()
        // A StateFlow replays its current value, and never re-emits an equal
        // one, so a re-collect can never double-purge.
        lockState.value = LockState.Unlocked
        runCurrent()
        assertEquals(1, purges)
    }
}
