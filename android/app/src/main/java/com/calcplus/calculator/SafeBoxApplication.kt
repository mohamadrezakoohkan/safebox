package com.calcplus.calculator

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.calcplus.calculator.core.data.OnboardingSentinelWriter
import com.calcplus.calculator.core.data.TrashHousekeeping
import com.calcplus.calculator.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeBoxApplication : Application(), SingletonImageLoader.Factory {
    /** Lock-surviving scope: imports keep running while UI is gated. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)

        // Auto-lock (backgrounding locks immediately, always) is driven by
        // MainActivity.onStart/onStop — single-activity app, see MainActivity.

        // Expired "Recently deleted" items go at app start, BEFORE the sweep:
        // the purge removes rows and files together, and anything it leaves
        // behind (a crash between the two) is exactly what the sweep collects.
        applicationScope.launch {
            container.trashRepository.purgeExpired(System.currentTimeMillis())
            // Startup orphan sweep (backstop; needs no unlock).
            container.photoRepository.sweepOrphans()
        }

        // …and again on every transition to Unlocked (decisions §3). Observing
        // the state flow keeps AppLockManager untouched.
        applicationScope.launch {
            TrashHousekeeping.purgeExpiredOnUnlock(
                lockState = container.lockManager.lockState,
                now = { System.currentTimeMillis() },
                purge = { now -> container.trashRepository.purgeExpired(now) },
            )
        }

        // The onboarding sentinel is persisted with the FIRST envelope, not at
        // guide finish (iteration-3-decisions §4) — same observer shape as the
        // trash housekeeping above, and for the same reason.
        applicationScope.launch {
            OnboardingSentinelWriter.persistOnFirstUnlock(
                lockState = container.lockManager.lockState,
                setComplete = { container.onboardingStore.setComplete() },
            )
        }
    }

    /** Vault image bytes must live only under filesDir/vault: disk cache disabled. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache(null)
            .build()
}
