package com.calcplus.calculator.core.data

import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.lock.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Erase everything: all vault rows and bytes, then the passcode, then the
 * onboarding flag, then the lock state machine back to first-run.
 *
 * Ordering is deliberate. Content goes first and the passcode last, so a
 * process death mid-nuke reopens the app locked over an already-empty vault —
 * a fresh passcode can never inherit old content. Rows go before files so a
 * crash between the two leaves only orphan files, which the startup sweep
 * already removes.
 */
class VaultNuker(
    private val database: SafeBoxDatabase,
    private val fileStore: PhotoFileStore,
    private val passcodeStore: PasscodeStore,
    private val onboardingStore: OnboardingStore,
    private val lockManager: AppLockManager,
) {
    suspend fun nuke() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        fileStore.deleteAll()
        passcodeStore.clear()
        onboardingStore.reset()
        lockManager.reset()
    }
}
