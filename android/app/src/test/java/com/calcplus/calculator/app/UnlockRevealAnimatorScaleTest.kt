package com.calcplus.calculator.app

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.app.UnlockReveal.Kind
import com.calcplus.calculator.core.lock.LockState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The reduced-motion signal on Android is `Settings.Global.ANIMATOR_DURATION_SCALE`
 * (decisions §1). Proves the reader sees the live setting — unset, "Remove
 * animations" (0), slowed — and that the value feeds the decision as decided.
 */
@RunWith(RobolectricTestRunner::class)
class UnlockRevealAnimatorScaleTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun setScale(value: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)
    }

    @Test
    fun unsetScaleReadsAsThePlatformDefault() {
        assertEquals(1f, UnlockReveal.animatorDurationScale(context), 0f)
        assertEquals(Kind.REVEAL, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, UnlockReveal.animatorDurationScale(context)))
    }

    @Test
    fun removeAnimationsReadsAsZeroAndCutsTheReveal() {
        setScale(0f)
        val scale = UnlockReveal.animatorDurationScale(context)
        assertEquals(0f, scale, 0f)
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, scale))
        assertEquals(Kind.CUT, UnlockReveal.kind(LockState.NeedsSetup, LockState.Unlocked, scale))
    }

    @Test
    fun aSlowedScaleStillReveals() {
        setScale(5f)
        val scale = UnlockReveal.animatorDurationScale(context)
        assertEquals(5f, scale, 0f)
        assertEquals(Kind.REVEAL, UnlockReveal.kind(LockState.Locked, LockState.Unlocked, scale))
    }

    @Test
    fun theSettingIsReadFreshEveryTime() {
        setScale(0f)
        assertEquals(0f, UnlockReveal.animatorDurationScale(context), 0f)
        setScale(1f)
        assertEquals(1f, UnlockReveal.animatorDurationScale(context), 0f)
        setScale(0f)
        assertEquals(0f, UnlockReveal.animatorDurationScale(context), 0f)
    }
}
