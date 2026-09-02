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
import com.calcplus.calculator.feature.calculator.CalcKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned envelope — parity with iOS: {algo, version, iterations, salt, hash}.
 * Never the passcode itself, never a reversible form of it.
 */
@Serializable
data class PasscodeBlob(
    val algo: String,
    val version: Int,
    val iterations: Int,
    val salt: String, // Base64
    val hash: String, // Base64
)

/**
 * Preferences-DataStore-backed passcode storage, Keystore-wrapped when the
 * Keystore is available and stored unwrapped otherwise (documented fallback,
 * android-plan §3.4). Verification runs off the UI path on Dispatchers.Default.
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
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Synchronous existence check — tests only; startup reads the shared prefs once. */
    fun hasPasscodeBlocking(): Boolean = runBlocking {
        dataStore.data.first()[KEY_BLOB] != null
    }

    suspend fun set(sequence: List<CalcKey>): Unit = withContext(Dispatchers.Default) {
        val salt = Pbkdf2.randomSalt()
        val hash = Pbkdf2.derive(CalcKey.serialize(sequence), salt, iterations)
        val blob = PasscodeBlob(
            algo = "PBKDF2-HMAC-SHA256",
            version = 1,
            iterations = iterations,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            hash = Base64.encodeToString(hash, Base64.NO_WRAP),
        )
        val plainBytes = json.encodeToString(blob).encodeToByteArray()
        // Opportunistic wrap; fall back to unwrapped when Keystore is unavailable.
        val wrapped = wrapper.wrap(plainBytes)
        val stored = wrapped ?: plainBytes
        dataStore.edit { prefs ->
            prefs[KEY_BLOB] = Base64.encodeToString(stored, Base64.NO_WRAP)
            prefs[KEY_WRAPPED] = wrapped != null
            prefs[KEY_CREATED_AT] = System.currentTimeMillis()
        }
        Unit
    }

    suspend fun matches(sequence: List<CalcKey>): Boolean = withContext(Dispatchers.Default) {
        val prefs = dataStore.data.first()
        val encoded = prefs[KEY_BLOB] ?: return@withContext false
        val isWrapped = prefs[KEY_WRAPPED] ?: false
        val storedBytes = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return@withContext false
        }
        val plainBytes = if (isWrapped) {
            wrapper.unwrap(storedBytes) ?: return@withContext false
        } else {
            storedBytes
        }
        val blob = try {
            json.decodeFromString<PasscodeBlob>(plainBytes.decodeToString())
        } catch (_: Exception) {
            return@withContext false
        }
        val salt = Base64.decode(blob.salt, Base64.NO_WRAP)
        val expected = Base64.decode(blob.hash, Base64.NO_WRAP)
        val derived = Pbkdf2.derive(CalcKey.serialize(sequence), salt, blob.iterations)
        Pbkdf2.constantTimeEquals(derived, expected)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BLOB)
            prefs.remove(KEY_WRAPPED)
            prefs.remove(KEY_CREATED_AT)
        }
    }
}
