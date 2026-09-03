package com.calcplus.calculator.update

import com.calcplus.calculator.core.update.UpdateCheckResult
import com.calcplus.calculator.core.update.UpdateChecker
import com.calcplus.calculator.core.update.UpdateEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * `version.json` parsing and result mapping (decisions §13). The fetch lambda is
 * injected, so nothing in this file touches the network — the real
 * `HttpURLConnection` path is exercised by the on-device manual checks only.
 */
class UpdateCheckerTest {
    private fun checker(body: String) = UpdateChecker(fetch = { body })

    @Test
    fun aNewerManifestReportsTheVersionAndItsReleasesUrl() = runTest {
        val result = checker(
            """{"latestVersion":"1.1.0","releasesUrl":"https://example.test/releases/1.1.0"}""",
        ).check(currentVersion = "1.0.0")

        assertEquals(
            UpdateCheckResult.Available("1.1.0", "https://example.test/releases/1.1.0"),
            result,
        )
    }

    @Test
    fun aManifestWithoutReleasesUrlFallsBackToTheCompiledInReleasesPage() = runTest {
        val result = checker("""{"latestVersion":"2.0.0"}""").check(currentVersion = "1.0.0")

        assertEquals(UpdateCheckResult.Available("2.0.0", UpdateEndpoints.RELEASES_URL), result)
    }

    @Test
    fun aBlankOrNullReleasesUrlFallsBackToo() = runTest {
        assertEquals(
            UpdateCheckResult.Available("2.0.0", UpdateEndpoints.RELEASES_URL),
            checker("""{"latestVersion":"2.0.0","releasesUrl":"   "}""").check("1.0.0"),
        )
        assertEquals(
            UpdateCheckResult.Available("2.0.0", UpdateEndpoints.RELEASES_URL),
            checker("""{"latestVersion":"2.0.0","releasesUrl":null}""").check("1.0.0"),
        )
    }

    @Test
    fun aNonHttpReleasesUrlIsRejectedBecauseTheValueEndsUpInAnActionViewIntent() = runTest {
        val hostile = listOf(
            "intent://evil/#Intent;scheme=http;end",
            "file:///data/data/com.calcplus.calculator/databases/safebox.db",
            "market://details?id=com.example",
            "javascript:alert(1)",
            "/relative/path",
        )
        for (url in hostile) {
            val result = checker("""{"latestVersion":"2.0.0","releasesUrl":"$url"}""").check("1.0.0")
            assertEquals(
                "must fall back for $url",
                UpdateCheckResult.Available("2.0.0", UpdateEndpoints.RELEASES_URL),
                result,
            )
        }
    }

    @Test
    fun plainHttpAndUppercaseSchemesAreAccepted() = runTest {
        assertEquals(
            UpdateCheckResult.Available("2.0.0", "http://example.test/r"),
            checker("""{"latestVersion":"2.0.0","releasesUrl":"http://example.test/r"}""").check("1.0.0"),
        )
        assertEquals(
            UpdateCheckResult.Available("2.0.0", "HTTPS://example.test/r"),
            checker("""{"latestVersion":"2.0.0","releasesUrl":"HTTPS://example.test/r"}""").check("1.0.0"),
        )
    }

    @Test
    fun theSameOrAnOlderVersionIsUpToDate() = runTest {
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker("""{"latestVersion":"1.0.0"}""").check("1.0.0"),
        )
        // The iOS-shaped manifest value against the Android version name.
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker("""{"latestVersion":"1.0"}""").check("1.0.0"),
        )
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker("""{"latestVersion":"0.9.0"}""").check("1.0.0"),
        )
    }

    @Test
    fun anUnparseableLatestVersionIsUpToDateNotAvailable() = runTest {
        // The JSON is valid, the version is not: never nag.
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker("""{"latestVersion":"2.0.0-rc1"}""").check("1.0.0"),
        )
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker("""{"latestVersion":""}""").check("1.0.0"),
        )
    }

    @Test
    fun unknownKeysAreIgnoredSoTheManifestCanGrow() = runTest {
        val result = checker(
            """{"latestVersion":"2.0.0","releasesUrl":"https://example.test/r","minOsVersion":30,"notes":["x"]}""",
        ).check("1.0.0")

        assertEquals(UpdateCheckResult.Available("2.0.0", "https://example.test/r"), result)
    }

    @Test
    fun aMalformedBodyFailsWithoutCrashing() = runTest {
        val bodies = listOf(
            "",
            "   ",
            "not json at all",
            "{",
            "<html><body>404</body></html>",
            """{"releasesUrl":"https://example.test/r"}""", // latestVersion missing
            """{"latestVersion":123}""", // wrong type
            "[]",
        )
        for (body in bodies) {
            assertEquals("body: $body", UpdateCheckResult.Failed, checker(body).check("1.0.0"))
        }
    }

    @Test
    fun aTransportErrorFails() = runTest {
        val result = UpdateChecker(fetch = { throw IOException("update check HTTP 503") })
            .check("1.0.0")

        assertEquals(UpdateCheckResult.Failed, result)
    }

    @Test
    fun cancellationPropagatesSoALockAbandonsTheRequest() = runTest {
        val checker = UpdateChecker(fetch = { throw CancellationException("vault locked") })
        try {
            checker.check("1.0.0")
            fail("cancellation must not be swallowed into Failed")
        } catch (expected: CancellationException) {
            assertTrue(true)
        }
    }
}
