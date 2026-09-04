package com.calcplus.calculator.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.crypto.KeystoreWrapper
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** XOR "wrapper" — deterministic stand-in for the Keystore path. */
private class FakeWrapper : BlobWrapper {
    override fun wrap(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray = wrapped.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

/** Wrapper that always fails — exercises the documented unwrapped fallback. */
private class UnavailableWrapper : BlobWrapper {
    override fun wrap(plain: ByteArray): ByteArray? = null
    override fun unwrap(wrapped: ByteArray): ByteArray? = null
}

/**
 * A store whose reads work and whose writes always fail — the "persistently
 * failing store" of decisions §3. The eager v1→v2 rewrite must swallow the
 * failure, keep verifying against the v1 document, and not retry forever.
 */
private class FailingWritesDataStore(
    private val delegate: DataStore<Preferences>,
) : DataStore<Preferences> {
    var updateAttempts = 0
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        updateAttempts += 1
        throw IOException("disk full")
    }
}

@RunWith(RobolectricTestRunner::class)
class PasscodeStoreTest {
    private val code = listOf("D1", "D2", "ADD", "D3", "D4")
    private val calculator = CalculatorDisguise.alphabet
    private val numpad = NumpadDisguise.alphabet

    private fun newDataStore(): DataStore<Preferences> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "test-${UUID.randomUUID()}.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun makeStore(
        wrapper: BlobWrapper,
        dataStore: DataStore<Preferences> = newDataStore(),
    ): PasscodeStore = PasscodeStore(dataStore, wrapper, iterations = 1_000)

    /** Writes a raw envelope document exactly as an older release would have. */
    private suspend fun seedEnvelope(
        dataStore: DataStore<Preferences>,
        wrapper: BlobWrapper,
        json: String,
    ) {
        val plain = json.encodeToByteArray()
        val wrapped = wrapper.wrap(plain)
        val stored = wrapped ?: plain
        dataStore.edit { prefs ->
            prefs[PasscodeStore.KEY_BLOB] = Base64.encodeToString(stored, Base64.NO_WRAP)
            prefs[PasscodeStore.KEY_WRAPPED] = wrapped != null
        }
    }

    private suspend fun readRawEnvelope(
        dataStore: DataStore<Preferences>,
        wrapper: BlobWrapper,
    ): String {
        val prefs = dataStore.data.first()
        val bytes = Base64.decode(prefs[PasscodeStore.KEY_BLOB]!!, Base64.NO_WRAP)
        val plain = if (prefs[PasscodeStore.KEY_WRAPPED] == true) wrapper.unwrap(bytes)!! else bytes
        return plain.decodeToString()
    }

    private fun field(json: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)!!.groupValues[1]

    // MARK: v2 round trips

    @Test
    fun roundTripWithWrapper() = runTest {
        val store = makeStore(FakeWrapper())
        assertFalse(store.hasPasscodeBlocking())
        store.set(code, calculator, "calculator")
        assertTrue(store.hasPasscodeBlocking())
        assertTrue(store.matches(code))
        assertFalse(store.matches(code.dropLast(1)))
        assertFalse(store.matches(code.dropLast(1) + "D9"))
        assertEquals("calculator", store.activeDisguiseId())
    }

    @Test
    fun roundTripWithKeystoreUnavailableFallsBackUnwrapped() = runTest {
        val store = makeStore(UnavailableWrapper())
        store.set(code, calculator, "calculator")
        assertTrue(store.matches(code)) // verification transparently handles unwrapped form
        assertFalse(store.matches(List(4) { "D9" }))
    }

    @Test
    fun orderSensitivity() = runTest {
        val store = makeStore(FakeWrapper())
        store.set(listOf("D1", "D2", "D3", "D4"), calculator, "calculator")
        assertFalse(store.matches(listOf("D2", "D1", "D3", "D4")))
    }

    @Test
    fun clearRemovesPasscodeAndMirror() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        store.set(code, calculator, "calculator")
        store.clear()
        assertFalse(store.hasPasscodeBlocking())
        assertFalse(store.matches(code))
        assertNull(store.activeDisguiseId())
        assertNull(dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
    }

    @Test
    fun changeReplacesAtomically() = runTest {
        val store = makeStore(FakeWrapper())
        store.set(code, calculator, "calculator")
        val newCode = listOf("D5", "ADD", "D7", "PCT")
        store.set(newCode, calculator, "calculator")
        assertFalse(store.matches(code)) // old fails from this moment
        assertTrue(store.matches(newCode))
    }

    @Test
    fun switchingFaceWritesBlobAndMirrorTogether() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        store.set(code, calculator, "calculator")
        val pin = listOf("D1", "D2", "D3", "D4", "D5", "D6")
        store.set(pin, numpad, "numpad")
        assertFalse(store.matches(code))
        assertTrue(store.matches(pin))
        assertEquals("numpad", store.activeDisguiseId())
        assertEquals("numpad", dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
        val raw = readRawEnvelope(dataStore, FakeWrapper())
        assertEquals("numpad", field(raw, "tokenSetId"))
        assertEquals("numpad", field(raw, "activeDisguiseId"))
    }

    @Test
    fun keystoreWrapperFallsBackUnderRobolectric() {
        // AndroidKeyStore isn't available in the Robolectric JVM: the wrapper
        // must fail soft (null), never throw — that IS the fallback path.
        val wrapper = KeystoreWrapper()
        assertNull(wrapper.wrap("blob".toByteArray()))
        assertNull(wrapper.unwrap(ByteArray(32)))
    }

    // MARK: envelope v1 → v2 migration (decisions §9 step 3)

    /** A v1 envelope written by iteration 2, holding the same [code]. */
    private suspend fun seedV1(
        dataStore: DataStore<Preferences>,
        wrapper: BlobWrapper = FakeWrapper(),
    ): Pair<String, String> {
        // Derive with the production KDF so the document is genuinely valid.
        val salt = com.calcplus.calculator.core.crypto.Pbkdf2.randomSalt()
        val hash = com.calcplus.calculator.core.crypto.Pbkdf2.derive(code.joinToString("|"), salt, 1_000)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        seedEnvelope(
            dataStore,
            wrapper,
            """{"algo":"PBKDF2-HMAC-SHA256","version":1,"iterations":1000,""" +
                """"salt":"$saltB64","hash":"$hashB64"}""",
        )
        return saltB64 to hashB64
    }

    @Test
    fun v1EnvelopeVerifiesTheSameCodeBeforeAndAfterRewrite() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        seedV1(dataStore)
        // (a) the same code verifies …
        assertTrue(store.matches(code))
        // … and still does once the eager rewrite has happened.
        assertEquals(2, readRawEnvelope(dataStore, FakeWrapper()).let { field2(it, "version") })
        assertTrue(store.matches(code))
        assertFalse(store.matches(List(4) { "D9" }))
    }

    @Test
    fun rewritePreservesSaltAndHashByteForByte() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        val (salt, hash) = seedV1(dataStore)
        store.matches(code)
        val raw = readRawEnvelope(dataStore, FakeWrapper())
        assertEquals(salt, field(raw, "salt"))
        assertEquals(hash, field(raw, "hash"))
        assertEquals("calculator", field(raw, "tokenSetId"))
        assertEquals("calculator", field(raw, "activeDisguiseId"))
    }

    @Test
    fun v1EnvelopeReadsAsTheCalculatorFaceAndSeedsTheMirror() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        seedV1(dataStore)
        // Before any read the mirror does not exist at all …
        assertNull(dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
        assertEquals("calculator", store.activeDisguiseId())
        // … and the migration writes it in the same commit as the blob.
        assertEquals("calculator", dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
    }

    @Test
    fun aFailedRewriteLeavesAVerifiableV1BlobAndIsNotRetried() = runTest {
        val backing = newDataStore()
        seedV1(backing)
        val failing = FailingWritesDataStore(backing)
        val store = makeStore(FakeWrapper(), failing)

        // (c) verification still works …
        assertTrue(store.matches(code))
        assertEquals(1, failing.updateAttempts)
        // … the document on disk is untouched v1 …
        assertEquals(1, field2(readRawEnvelope(backing, FakeWrapper()), "version"))
        // … and the guard means a persistently failing store is not hammered
        // on every single verification.
        assertTrue(store.matches(code))
        assertTrue(store.matches(code))
        assertEquals(1, failing.updateAttempts)
    }

    @Test
    fun aVersionThreeEnvelopeIsRejectedWhileHasPasscodeStaysTrue() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        seedEnvelope(
            dataStore,
            FakeWrapper(),
            """{"algo":"PBKDF2-HMAC-SHA256","version":3,"iterations":1000,""" +
                """"salt":"AAAA","hash":"AAAA","tokenSetId":"pattern",""" +
                """"alphabetVersion":1,"activeDisguiseId":"pattern"}""",
        )
        // Fail closed: nothing matches, no face is reported …
        assertFalse(store.matches(code))
        assertNull(store.activeDisguiseId())
        // … but an item EXISTS, so setup can never be reached over it.
        assertTrue(store.hasPasscodeBlocking())
        // And the document is not "upgraded" or otherwise touched.
        assertEquals(3, field2(readRawEnvelope(dataStore, FakeWrapper()), "version"))
    }

    @Test
    fun aTamperedMirrorIsHealedFromTheEnvelope() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        store.set(code, numpad, "numpad")
        // Someone edits the prefs file: the mirror now lies.
        dataStore.edit { it[PasscodeStore.KEY_ACTIVE_DISGUISE] = "pattern" }
        assertNotEquals("numpad", dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
        // The envelope is authoritative and the mirror is rewritten from it.
        assertEquals("numpad", store.activeDisguiseId())
        assertEquals("numpad", dataStore.data.first()[PasscodeStore.KEY_ACTIVE_DISGUISE])
        // The code itself is unaffected either way.
        assertTrue(store.matches(code))
    }

    @Test
    fun anUndecodableEnvelopeFailsClosedButKeepsHasPasscode() = runTest {
        val dataStore = newDataStore()
        val store = makeStore(FakeWrapper(), dataStore)
        seedEnvelope(dataStore, FakeWrapper(), "not json at all")
        assertFalse(store.matches(code))
        assertNull(store.activeDisguiseId())
        assertTrue(store.hasPasscodeBlocking())
    }

    private fun field2(json: String, name: String): Int =
        Regex("\"$name\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toInt()
}
