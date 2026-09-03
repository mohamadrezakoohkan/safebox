package com.calcplus.calculator.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings "Open-source licenses" row must name every third-party runtime
 * dependency the app ships. N3 added Media3 (decisions §9), so the row lists it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class LicensesRowTest {
    private val body: String
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.settings_licenses_body)

    @Test
    fun theLicensesRowListsMedia3() {
        assertTrue("licenses row must name Media3, got: $body", body.contains("Media3"))
    }

    @Test
    fun theLicensesRowStillListsTheEarlierDependencies() {
        assertTrue(body.contains("Jetpack"))
        assertTrue(body.contains("Coil"))
        assertTrue(body.contains("Kotlin"))
        assertTrue(body.contains("Apache 2.0"))
    }
}
