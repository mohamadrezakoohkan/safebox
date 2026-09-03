package com.calcplus.calculator.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.data.PasscodeRepositoryImpl
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.feature.calculator.CalcKey
import com.calcplus.calculator.feature.onboarding.OnboardingMode
import com.calcplus.calculator.feature.onboarding.recordOnboardingCompletion
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Deterministic XOR stand-in for the Keystore wrap (same as VaultNukerTest). */
private class XorWrapper : BlobWrapper {
    override fun wrap(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray = wrapped.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

/**
 * Decisions §5: finishing or dismissing the guide in revisit mode never writes
 * the onboarding-complete flag — neither the persisted one nor the lock
 * manager's — while the first run still records both exactly as before.
 * Uses the real [OnboardingStore] on a real DataStore file (the VaultNukerTest
 * pattern) and a real [AppLockManager]; nothing is mocked.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingCompletionTest {
    private val code = listOf(CalcKey.D7, CalcKey.ADD, CalcKey.D7, CalcKey.PCT)

    private lateinit var passcodeStore: PasscodeStore
    private lateinit var onboardingStore: OnboardingStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharedDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "onboarding-prefs-${UUID.randomUUID()}.preferences_pb") }
        )
        passcodeStore = PasscodeStore(sharedDataStore, XorWrapper(), iterations = 1_000)
        onboardingStore = OnboardingStore(sharedDataStore)
    }

    /** Fresh install: no passcode, guide showing. */
    private fun firstRunManager() = AppLockManager(
        PasscodeRepositoryImpl(passcodeStore),
        hasPasscode = false,
        elapsedRealtime = { 0L },
        onboardingComplete = false,
    )

    /** A set-up vault whose owner already finished the guide once. */
    private fun setUpManager() = AppLockManager(
        PasscodeRepositoryImpl(passcodeStore),
        hasPasscode = true,
        elapsedRealtime = { 0L },
        onboardingComplete = true,
    )

    @Test
    fun revisitDoesNotAlterAnUnsetPersistedFlagNorTheLockManager() = runTest {
        // The strongest form of "does not alter": even when the flag is NOT
        // set and the manager still wants the guide, a revisit finish writes
        // nothing and reports nothing recorded.
        val lockManager = firstRunManager()
        assertFalse(onboardingStore.isCompleteBlocking())
        assertTrue(lockManager.showOnboarding.value)

        val job = recordOnboardingCompletion(OnboardingMode.REVISIT, lockManager, onboardingStore, this)

        assertNull(job)
        assertFalse(onboardingStore.isCompleteBlocking())
        assertTrue(lockManager.showOnboarding.value)
        assertEquals(LockState.NeedsSetup, lockManager.lockState.value)
    }

    @Test
    fun revisitFromTheUnlockedVaultLeavesEverythingAsItWas() = runTest {
        // The real revisit situation: flag already set, vault unlocked.
        onboardingStore.setComplete()
        passcodeStore.set(code)
        val lockManager = setUpManager()
        lockManager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, lockManager.lockState.value)
        assertFalse(lockManager.showOnboarding.value)

        val job = recordOnboardingCompletion(OnboardingMode.REVISIT, lockManager, onboardingStore, this)

        assertNull(job)
        assertTrue(onboardingStore.isCompleteBlocking())
        assertFalse(lockManager.showOnboarding.value)
        // The vault stays unlocked: the user lands back on Settings.
        assertEquals(LockState.Unlocked, lockManager.lockState.value)
    }

    @Test
    fun firstRunRecordsBothTheInMemoryAndThePersistedFlag() = runTest {
        val lockManager = firstRunManager()
        assertFalse(onboardingStore.isCompleteBlocking())
        assertTrue(lockManager.showOnboarding.value)

        val job = recordOnboardingCompletion(OnboardingMode.FIRST_RUN, lockManager, onboardingStore, this)

        // In-memory flag flips synchronously (the root switch leaves the guide
        // in the same composition) …
        assertFalse(lockManager.showOnboarding.value)
        // … and the persisted flag lands in the caller's scope.
        assertNotNull(job)
        job!!.join()
        assertTrue(onboardingStore.isCompleteBlocking())
        // Finishing the guide does not set up a passcode by itself.
        assertEquals(LockState.NeedsSetup, lockManager.lockState.value)
    }

    @Test
    fun firstRunIsIdempotent() = runTest {
        val lockManager = firstRunManager()
        recordOnboardingCompletion(OnboardingMode.FIRST_RUN, lockManager, onboardingStore, this)!!.join()
        recordOnboardingCompletion(OnboardingMode.FIRST_RUN, lockManager, onboardingStore, this)!!.join()
        assertTrue(onboardingStore.isCompleteBlocking())
        assertFalse(lockManager.showOnboarding.value)
    }
}
