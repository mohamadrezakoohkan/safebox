package com.calcplus.calculator.update

import com.calcplus.calculator.core.update.AppVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared dotted-numeric compare rule (decisions §13). The iOS suite tests
 * the identical table, because the two platforms ship different version-name
 * shapes (`1.0` vs `1.0.0`) against the same `version.json`.
 */
class AppVersionTest {
    @Test
    fun missingComponentsAreZeroSoOneDotZeroEqualsOneDotZeroDotZero() {
        // The reason this rule exists: iOS ships "1.0", Android "1.0.0",
        // and neither may nag against a manifest written for the other.
        assertFalse(AppVersion.isNewer("1.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0"))
        assertFalse(AppVersion.isNewer("1", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0.0", "1"))
    }

    @Test
    fun aStrictlyGreaterComponentIsNewerEvenWhenTheStringIsShorter() {
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
        assertTrue(AppVersion.isNewer("1.0.0.1", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.1", "1.0.9"))
    }

    @Test
    fun theMostSignificantDifferingComponentDecides() {
        assertTrue(AppVersion.isNewer("2.0", "1.9.9"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.99.99"))
        assertFalse(AppVersion.isNewer("1.9.9", "2.0"))
    }

    @Test
    fun anOlderManifestIsNotNewer() {
        assertFalse(AppVersion.isNewer("0.9", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.1"))
        assertFalse(AppVersion.isNewer("1.0.0", "10.0.0"))
    }

    @Test
    fun componentsCompareNumericallyNotLexicographically() {
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.0"))
        assertFalse(AppVersion.isNewer("1.9.0", "1.10.0"))
        assertFalse(AppVersion.isNewer("1.02", "1.2"))
    }

    @Test
    fun malformedLatestNeverNags() {
        // A broken or hostile version.json must not produce "update available".
        assertFalse(AppVersion.isNewer("", "1.0.0"))
        assertFalse(AppVersion.isNewer("   ", "1.0.0"))
        assertFalse(AppVersion.isNewer("banana", "1.0.0"))
        assertFalse(AppVersion.isNewer("v2.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("2.0.0-beta", "1.0.0"))
        assertFalse(AppVersion.isNewer("2..0", "1.0.0"))
        assertFalse(AppVersion.isNewer("2.0.", "1.0.0"))
        assertFalse(AppVersion.isNewer(".2.0", "1.0.0"))
        assertFalse(AppVersion.isNewer(" 2.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("2.0.0 ", "1.0.0"))
        assertFalse(AppVersion.isNewer("2,0,0", "1.0.0"))
        assertFalse(AppVersion.isNewer("-2.0.0", "1.0.0"))
    }

    @Test
    fun malformedCurrentAlsoComparesAsNotNewer() {
        assertFalse(AppVersion.isNewer("2.0.0", ""))
        assertFalse(AppVersion.isNewer("2.0.0", "1.0.0-debug"))
        assertFalse(AppVersion.isNewer("2.0.0", "nope"))
    }

    @Test
    fun theShippingPairIsUpToDate() {
        // version.json currently advertises 1.0.0; Android ships versionName 1.0.0.
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
    }
}
