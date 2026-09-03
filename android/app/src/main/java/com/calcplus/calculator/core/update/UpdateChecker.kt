package com.calcplus.calculator.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app's ONLY outbound network request (decisions §13).
 *
 * Posture, all of it non-negotiable:
 * - **Manual only.** Nothing here runs at launch, on unlock, or on a timer.
 *   The single caller is the "Check for updates" row's tap handler, which lives
 *   inside the unlocked vault; a fresh install that never opens that row never
 *   touches the network.
 * - Bare `GET`: no query string, no custom headers, no credentials, no cookies.
 *   Nothing about the device or the vault is transmitted.
 * - [HttpURLConnection.setUseCaches] is `false`: an HTTP cache entry would leave
 *   the request URL — which names the `safebox` repo — inside the app container,
 *   which is a forensic tell.
 * - Neither the URL nor the response body is ever logged (no-logging rule), and
 *   no exception message carries either.
 * - Cancellation propagates: locking the vault cancels `viewModelScope` and the
 *   in-flight request is abandoned.
 *
 * The [fetch] lambda is injectable so unit tests never touch the network.
 */
class UpdateChecker(
    private val fetch: suspend () -> String = { fetchVersionManifest() },
) {
    /**
     * Fetches and interprets `version.json` against [currentVersion].
     * Never throws (except [CancellationException], which is re-thrown so the
     * vault teardown stays cooperative): any transport, HTTP or parse problem
     * becomes [UpdateCheckResult.Failed].
     */
    suspend fun check(currentVersion: String): UpdateCheckResult =
        try {
            val manifest = json.decodeFromString(VersionManifest.serializer(), fetch())
            val latest = manifest.latestVersion
            if (AppVersion.isNewer(latest, currentVersion)) {
                UpdateCheckResult.Available(latest, safeReleasesUrl(manifest.releasesUrl))
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Deliberately swallowed and never logged: the message could carry
            // the URL or the response body.
            UpdateCheckResult.Failed
        }

    /**
     * The fetched `releasesUrl` ends up in an `ACTION_VIEW` intent, so only a
     * plain `http`/`https` URL is honored. A missing, blank or other-scheme
     * value (`intent:`, `file:`, `market:` …) falls back to the compiled-in
     * Releases page, so whoever edits `version.json` can change the destination
     * page but cannot redirect the tap at an arbitrary component.
     */
    private fun safeReleasesUrl(candidate: String?): String {
        val trimmed = candidate?.trim().orEmpty()
        val allowed = trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        return if (allowed) trimmed else UpdateEndpoints.RELEASES_URL
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Connect and read timeout for the one request, milliseconds. */
        const val TIMEOUT_MS: Int = 10_000

        /** Hard cap on the body we are willing to read (`version.json` is ~100 bytes). */
        private const val MAX_BODY_CHARS = 8 * 1024

        /**
         * The real fetch: a bare cache-less `GET` on [Dispatchers.IO].
         * Blocking calls are confined here; the caller only has to be a
         * cancellable coroutine.
         */
        private suspend fun fetchVersionManifest(): String = withContext(Dispatchers.IO) {
            val connection = URL(UpdateEndpoints.VERSION_MANIFEST_URL)
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.doInput = true
                connection.useCaches = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    // No URL and no body in the message — nothing loggable leaks.
                    throw IOException("update check HTTP $code")
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(MAX_BODY_CHARS)
                    var filled = 0
                    while (filled < MAX_BODY_CHARS) {
                        val read = reader.read(buffer, filled, MAX_BODY_CHARS - filled)
                        if (read <= 0) break
                        filled += read
                    }
                    String(buffer, 0, filled)
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

/** Outcome of one manual check. Carries no diagnostics by design. */
sealed interface UpdateCheckResult {
    /** The running build is the latest, or the manifest advertises an older one. */
    data object UpToDate : UpdateCheckResult

    /** A strictly newer [version] exists; [releasesUrl] is a vetted http(s) URL. */
    data class Available(val version: String, val releasesUrl: String) : UpdateCheckResult

    /** Offline, non-200, or an unreadable/malformed manifest — indistinguishable on purpose. */
    data object Failed : UpdateCheckResult
}

/**
 * `version.json` at the repo root. `releasesUrl` is optional so an older or
 * hand-edited manifest still parses; `ignoreUnknownKeys` lets the file grow
 * fields this build does not know about.
 */
@Serializable
internal data class VersionManifest(
    val latestVersion: String,
    val releasesUrl: String? = null,
)

/** The three compiled-in URLs (decisions §13). No other host is ever contacted. */
object UpdateEndpoints {
    /** Settings → About → "Source code". */
    const val SOURCE_URL: String = "https://github.com/mohamadrezakoohkan/safebox"

    /** The one request the app can make, and only on an explicit tap. */
    const val VERSION_MANIFEST_URL: String =
        "https://raw.githubusercontent.com/mohamadrezakoohkan/safebox/main/version.json"

    /** Fallback destination when the manifest has no usable `releasesUrl`. */
    const val RELEASES_URL: String = "https://github.com/mohamadrezakoohkan/safebox/releases/latest"
}
