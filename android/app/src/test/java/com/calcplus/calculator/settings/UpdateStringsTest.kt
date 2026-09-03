package com.calcplus.calculator.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The seven new string IDs from decisions §13 and the revised privacy body.
 * Pinned to en-rUS so the source English is what gets asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class UpdateStringsTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theSourceAndUpdateRowsResolveToTheDecidedEnglish() {
        assertEquals("Source code", context.getString(R.string.settings_source_code))
        assertEquals("View this app on GitHub", context.getString(R.string.settings_source_code_subtitle))
        assertEquals("Check for updates", context.getString(R.string.settings_check_updates))
    }

    @Test
    fun theUpdateStateSubtitlesResolveToTheDecidedEnglish() {
        assertEquals("Checking…", context.getString(R.string.settings_update_checking))
        assertEquals("Up to date", context.getString(R.string.settings_update_up_to_date))
        assertEquals("Couldn't check for updates", context.getString(R.string.settings_update_failed))
    }

    @Test
    fun theAvailableSubtitleTakesTheVersionAsItsOnePositionalArgument() {
        assertEquals("Version 1.2.0 available", context.getString(R.string.settings_update_available, "1.2.0"))
    }

    @Test
    fun thePrivacyBodyNoLongerClaimsTheAppSendsNothingAnywhere() {
        val body = context.getString(R.string.settings_privacy_body)
        assertFalse("stale claim in: $body", body.contains("sends nothing anywhere"))
        assertFalse("stale claim in: $body", body.contains("no servers"))
        assertTrue("must name the manual check: $body", body.contains("Check for updates"))
        assertTrue(body.contains("All data stays on this device"))
        assertTrue(body.contains("no accounts, no analytics, no cloud sync"))
    }
}
