package com.calcplus.calculator.core.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.crypto.Pbkdf2
import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned envelope — parity with iOS. v1 was
 * `{algo, version, iterations, salt, hash}`; v2 (decisions §3) adds the three
 * face fields. They are nullable so a v1 document still decodes: absence means
 * calculator.v1 by definition.
 *
 * Never the passcode itself, never a reversible form of it.
 */
@Serializable
data class PasscodeBlob(
    val algo: String,
    val version: Int,
    val iterations: Int,
    val salt: String, // Base64
    val hash: String, // Base64
    val tokenSetId: String? = null,
    val alphabetVersion: Int? = null,
    val activeDisguiseId: String? = null,
)

/**
 * Preferences-DataStore-backed passcode storage, Keystore-wrapped when the
 * Keystore is available and stored unwrapped otherwise (documented fallback,
 * android-plan §3.4). Verification runs off the UI path on Dispatchers.Default.
 *
 * Two keys describe the enrollment and they are ALWAYS written in one
 * `edit {}` transaction: [KEY_BLOB] (the envelope — authoritative) and
 * [KEY_ACTIVE_DISGUISE] (the mirror, which exists solely so process start can
 * learn the face from the single existing prefs snapshot without unwrapping the
 * envelope — no Keystore work in `Application.onCreate`). Because both live in
 * one file and one transaction, a desync is unreachable through app code; the
 * fail-closed rule in [DisguiseRegistry.resolve] covers it regardless, and
 * [activeDisguiseId] heals it from the envelope.
 */
class PasscodeStore(
    private val dataStore: DataStore<Preferences>,
    private val wrapper: BlobWrapper,
    private val iterations: Int = Pbkdf2.DEFAULT_ITERATIONS,
) {
    companion object {
        val KEY_BLOB = stringPreferencesKey("blob")
        val KEY_WRAPPED = booleanPreferencesKey("wrapped")
        val KEY_CREATED_AT = longPreferencesKey("createdAt")

        /** Launch-read mirror of the envelope's `activeDisguiseId` (§3). */
        val KEY_ACTIVE_DISGUISE = stringPreferencesKey("active_disguise_id")

        /** Written envelope version, and the highest one accepted (§8). */
        const val ENVELOPE_VERSION = 2

        /** What a v1 envelope — or any missing face field — means (§3). */
        const val LEGACY_DISGUISE_ID = "calculator"
        const val LEGACY_ALPHABET_VERSION = 1

        const val ALGO = "PBKDF2-HMAC-SHA256"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One-shot guard so a persistently failing store does not attempt the v1→v2
     * rewrite on every single verification (§3). Losing it on process death is
     * the intent: the next launch may well be able to write.
     */
    @Volatile
    private var rewriteAttempted = false

    /** Synchronous existence check — tests only; startup reads the shared prefs once. */
    fun hasPasscodeBlocking(): Boolean = runBlocking {
        dataStore.data.first()[KEY_BLOB] != null
    }

    suspend fun set(
        tokens: List<String>,
        alphabet: AlphabetDescriptor,
        activeDisguiseId: String,
    ): Unit = withContext(Dispatchers.Default) {
        val salt = Pbkdf2.randomSalt()
        val hash = Pbkdf2.derive(AlphabetDescriptor.serialize(tokens), salt, iterations)
        val blob = PasscodeBlob(
            algo = ALGO,
            version = ENVELOPE_VERSION,
            iterations = iterations,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            hash = Base64.encodeToString(hash, Base64.NO_WRAP),
            tokenSetId = alphabet.tokenSetId,
            alphabetVersion = alphabet.alphabetVersion,
            activeDisguiseId = activeDisguiseId,
        )
        writeBlob(blob, activeDisguiseId, stampCreatedAt = true)
        // A fresh v2 envelope has nothing to migrate; re-arm the guard so a
        // later legacy document (there is none, but the flag is process-wide)
        // is still given one attempt.
        rewriteAttempted = false
    }

    suspend fun matches(tokens: List<String>): Boolean = withContext(Dispatchers.Default) {
        val envelope = readEnvelope() ?: return@withContext false
        val salt = Base64.decode(envelope.blob.salt, Base64.NO_WRAP)
        val expected = Base64.decode(envelope.blob.hash, Base64.NO_WRAP)
        // Serialization is the universal `|`-join, version- and set-invariant by
        // rule; `tokenSetId` is deliberately NOT compared (§3).
        val derived = Pbkdf2.derive(AlphabetDescriptor.serialize(tokens), salt, envelope.blob.iterations)
        Pbkdf2.constantTimeEquals(derived, expected)
    }

    /**
     * The enrolled face id, or null when there is no readable envelope. The
     * envelope is authoritative: when the mirror disagrees (a tampered prefs
     * file, an interrupted legacy migration) it is rewritten from the envelope
     * here, so the next launch reads the right face.
     */
    suspend fun activeDisguiseId(): String? = withContext(Dispatchers.Default) {
        val envelope = readEnvelope() ?: return@withContext null
        val mirror = dataStore.data.first()[KEY_ACTIVE_DISGUISE]
        if (mirror != envelope.activeDisguiseId) {
            runCatching {
                dataStore.edit { prefs -> prefs[KEY_ACTIVE_DISGUISE] = envelope.activeDisguiseId }
            }
        }
        envelope.activeDisguiseId
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BLOB)
            prefs.remove(KEY_WRAPPED)
            prefs.remove(KEY_CREATED_AT)
            prefs.remove(KEY_ACTIVE_DISGUISE)
        }
    }

    // MARK: Envelope reading and the v1 → v2 migration

    private class Envelope(
        val blob: PasscodeBlob,
        val activeDisguiseId: String,
    )

    /**
     * Decodes the stored envelope, interpreting a v1 document — or any v2
     * document with a missing face field — as calculator.v1, and eagerly
     * rewriting it as v2 with **salt and hash copied verbatim**.
     *
     * Returns null when there is no envelope, when it cannot be unwrapped or
     * decoded, or when its version is above [ENVELOPE_VERSION] (the forward
     * obligation from skeleton §3.4): the caller then fails closed. A rewrite
     * failure is swallowed and the legacy interpretation holds — the v1 read
     * path is never deleted.
     */
    private suspend fun readEnvelope(): Envelope? {
        val prefs = dataStore.data.first()
        val encoded = prefs[KEY_BLOB] ?: return null
        val isWrapped = prefs[KEY_WRAPPED] ?: false
        val storedBytes = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val plainBytes = if (isWrapped) {
            wrapper.unwrap(storedBytes) ?: return null
        } else {
            storedBytes
        }
        val blob = try {
            json.decodeFromString<PasscodeBlob>(plainBytes.decodeToString())
        } catch (_: Exception) {
            return null
        }
        // Version ceiling: a document written by a future release is not
        // guessed at.
        if (blob.version > ENVELOPE_VERSION) return null

        val faceId = blob.activeDisguiseId ?: LEGACY_DISGUISE_ID
        val needsRewrite = blob.version < ENVELOPE_VERSION ||
            blob.tokenSetId == null ||
            blob.alphabetVersion == null ||
            blob.activeDisguiseId == null

        if (needsRewrite && !rewriteAttempted) {
            rewriteAttempted = true
            val upgraded = blob.copy(
                version = ENVELOPE_VERSION,
                tokenSetId = blob.tokenSetId ?: LEGACY_DISGUISE_ID,
                alphabetVersion = blob.alphabetVersion ?: LEGACY_ALPHABET_VERSION,
                activeDisguiseId = faceId,
                // salt and hash are carried over byte for byte — the code the
                // user already has must keep verifying.
            )
            runCatching { writeBlob(upgraded, faceId, stampCreatedAt = false) }
        }
        return Envelope(blob, faceId)
    }

    /** Blob and mirror, always in ONE transaction (§3). */
    private suspend fun writeBlob(blob: PasscodeBlob, faceId: String, stampCreatedAt: Boolean) {
        val plainBytes = json.encodeToString(blob).encodeToByteArray()
        // Opportunistic wrap; fall back to unwrapped when Keystore is unavailable.
        val wrapped = wrapper.wrap(plainBytes)
        val stored = wrapped ?: plainBytes
        dataStore.edit { prefs ->
            prefs[KEY_BLOB] = Base64.encodeToString(stored, Base64.NO_WRAP)
            prefs[KEY_WRAPPED] = wrapped != null
            if (stampCreatedAt) prefs[KEY_CREATED_AT] = System.currentTimeMillis()
            prefs[KEY_ACTIVE_DISGUISE] = faceId
        }
    }
}
