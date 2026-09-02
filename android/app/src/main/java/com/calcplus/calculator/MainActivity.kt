package com.calcplus.calculator

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.calcplus.calculator.app.SafeBoxApp
import com.calcplus.calculator.core.ui.theme.SafeBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Unconditional FLAG_SECURE for the whole activity: blocks screenshots
        // and screen recording, blank recents thumbnail. Deliberate, accepted
        // divergence from iOS (android-plan §8.2) — never toggle per screen.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        val container = (application as SafeBoxApplication).container
        setContent {
            SafeBoxTheme {
                SafeBoxApp(container)
            }
        }
    }

    // Immediate lock-on-background is driven by THIS activity's lifecycle:
    // SafeBox is single-activity by design, so activity onStart/onStop map 1:1
    // to app foreground/background. (ProcessLifecycleOwner missed dispatches
    // under rapid transitions in testing; the activity callbacks are
    // deterministic, and the picker suppression flag already covers the one
    // legitimate external-activity round-trip.) Config-change stops also lock:
    // fail closed on ambiguity, per the idea plan.
    override fun onStart() {
        super.onStart()
        (application as SafeBoxApplication).container.lockManager.onAppStart()
    }

    override fun onStop() {
        super.onStop()
        (application as SafeBoxApplication).container.lockManager.onAppStop()
    }
}
