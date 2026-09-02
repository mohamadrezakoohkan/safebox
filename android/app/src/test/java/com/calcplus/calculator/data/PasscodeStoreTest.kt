package com.calcplus.calculator.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.crypto.KeystoreWrapper
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.feature.calculator.CalcKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
class PasscodeStoreTest {
    private val code = listOf(CalcKey.D1, CalcKey.D2, CalcKey.ADD, CalcKey.D3, CalcKey.D4)

    private fun makeStore(wrapper: BlobWrapper): PasscodeStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "test-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return PasscodeStore(dataStore, wrapper, iterations = 1_000)
    }

    @Test
    fun roundTripWithWrapper() = runTest {
        val store = makeStore(FakeWrapper())
        assertFalse(store.hasPasscodeBlocking())
        store.set(code)
        assertTrue(store.hasPasscodeBlocking())
        assertTrue(store.matches(code))
        assertFalse(store.matches(code.dropLast(1)))
        assertFalse(store.matches(code.dropLast(1) + CalcKey.D9))
    }

    @Test
    fun roundTripWithKeystoreUnavailableFallsBackUnwrapped() = runTest {
        val store = makeStore(UnavailableWrapper())
        store.set(code)
        assertTrue(store.matches(code)) // verification transparently handles unwrapped form
        assertFalse(store.matches(List(4) { CalcKey.D9 }))
    }

    @Test
    fun orderSensitivity() = runTest {
        val store = makeStore(FakeWrapper())
        store.set(listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.D4))
        assertFalse(store.matches(listOf(CalcKey.D2, CalcKey.D1, CalcKey.D3, CalcKey.D4)))
    }

    @Test
    fun clearRemovesPasscode() = runTest {
        val store = makeStore(FakeWrapper())
        store.set(code)
        store.clear()
        assertFalse(store.hasPasscodeBlocking())
        assertFalse(store.matches(code))
    }

    @Test
    fun changeReplacesAtomically() = runTest {
        val store = makeStore(FakeWrapper())
        store.set(code)
        val newCode = listOf(CalcKey.D5, CalcKey.ADD, CalcKey.D7, CalcKey.PCT)
        store.set(newCode)
        assertFalse(store.matches(code)) // old fails from this moment
        assertTrue(store.matches(newCode))
    }

    @Test
    fun keystoreWrapperFallsBackUnderRobolectric() {
        // AndroidKeyStore isn't available in the Robolectric JVM: the wrapper
        // must fail soft (null), never throw — that IS the fallback path.
        val wrapper = KeystoreWrapper()
        assertNull(wrapper.wrap("blob".toByteArray()))
        assertNull(wrapper.unwrap(ByteArray(32)))
    }
}
