package com.calcplus.calculator.core.disguise

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Shake spec, design §5.6 / decisions §8 — identical on both platforms. */
object ShakeSpec {
    const val AMPLITUDE_DP = 8f
    const val CYCLES = 3
    const val DURATION_MS = 300f

    /**
     * How long an overt face holds its failed entry before clearing it. Equal
     * to the shake duration, and unchanged under reduce-motion so the user
     * still sees *what* failed.
     */
    const val OVERT_FAIL_HOLD_MS = 300L
}

/**
 * The horizontal offset of a shake target, driven by a monotonic [token].
 *
 * Two rules make this safe to hand to a freshly composed face:
 *
 *  1. **Change only.** The last-seen token is remembered on first composition,
 *     so a surface that inherits a non-zero count (a re-used state holder, a
 *     recomposition after a configuration change) does not shake on its first
 *     frame. Only an increment observed *after* the first render animates
 *     (§1.1).
 *  2. **Real units.** The amplitude is [ShakeSpec.AMPLITUDE_DP] dp, returned as
 *     a [Dp] and converted by the caller's own density — not a raw pixel
 *     multiplier, which made the travel device-dependent.
 *
 * Under "Remove animations" there is no translation at all; the caller's hold
 * still runs for [ShakeSpec.OVERT_FAIL_HOLD_MS].
 */
@Composable
fun rememberShakeOffset(token: Int): Dp {
    var offset by remember { mutableStateOf(0.dp) }
    // Seeded with the token present at first composition: an inherited count is
    // "already seen" and therefore never animates.
    val lastSeen = remember { intArrayOf(token) }
    val context = LocalContext.current

    LaunchedEffect(token) {
        if (token == lastSeen[0]) return@LaunchedEffect
        lastSeen[0] = token
        val animationsOn = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
        if (!animationsOn) {
            offset = 0.dp
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = (now - start) / 1_000_000f
            if (elapsed >= ShakeSpec.DURATION_MS) break
            val phase = elapsed / ShakeSpec.DURATION_MS * 2f * PI.toFloat() * ShakeSpec.CYCLES
            offset = (sin(phase.toDouble()).toFloat() * ShakeSpec.AMPLITUDE_DP).dp
        }
        offset = 0.dp
    }
    return offset
}
