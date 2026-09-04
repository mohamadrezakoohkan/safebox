package com.calcplus.calculator.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.disguise.CoverAliases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three cover aliases must actually exist in the merged manifest and point
 * at the one activity (§9a). The Kotlin constants and the manifest are two
 * hand-written copies of the same three names, and nothing else catches a
 * divergence: a mistyped alias makes `setComponentEnabledSetting` throw at the
 * exact moment the vault has just been re-enrolled, where the failure is
 * swallowed by design — the app simply loses its launcher icon.
 */
@RunWith(RobolectricTestRunner::class)
class CoverAliasManifestTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val aliases =
        listOf(CoverAliases.CALCULATOR, CoverAliases.NOTEPAD, CoverAliases.GALLERY)

    @Test
    fun everyCoverAliasResolves() {
        aliases.forEach { alias ->
            val info = context.packageManager.getActivityInfo(
                ComponentName(context.packageName, alias),
                // The two non-default aliases ship disabled, so they are only
                // visible with this flag.
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            assertEquals(alias, info.name)
            assertTrue("$alias must be exported", info.exported)
        }
    }

    /**
     * The calculator identity is the shipped default and the only alias enabled
     * in the manifest, so a fresh install — and any install whose component
     * settings were reset — lands on it.
     */
    @Test
    fun onlyTheCalculatorAliasIsEnabledOutOfTheBox() {
        val enabled = aliases.filter { alias ->
            context.packageManager.getActivityInfo(
                ComponentName(context.packageName, alias),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            ).enabled
        }
        assertEquals(listOf(CoverAliases.CALCULATOR), enabled)
    }
}
