package com.calcplus.calculator

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
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

        // Startup orphan sweep (backstop; needs no unlock).
        applicationScope.launch {
            container.photoRepository.sweepOrphans()
        }
    }

    /** Vault image bytes must live only under filesDir/vault: disk cache disabled. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache(null)
            .build()
}
