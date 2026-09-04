package com.calcplus.calculator.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.crypto.BlobWrapper
import com.calcplus.calculator.core.data.OnboardingSentinelWriter
import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.data.PasscodeRepositoryImpl
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.core.lock.LockState
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.onboarding.OnboardingMode
import com.calcplus.calculator.feature.onboarding.recordOnboardingCompletion
import com.calcplus.calculator.feature.pattern.PatternDisguise
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Decisions §5: finishing or dismissing the guide in revisit mode never records
 * completion. Decisions §4: the PERSISTED sentinel now lands with the first
 * envelope, not at guide finish, so a process death between the two brings the
 * guide back rather than stranding the user on a face they can no longer choose.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingCompletionTest {
    private val code = listOf("D7", "ADD", "D7", "PCT")

    private lateinit var passcodeStore: PasscodeStore
    private lateinit var onboardingStore: OnboardingStore

    private val registry =
        DisguiseRegistry(listOf(CalculatorDisguise, NumpadDisguise, PatternDisguise))

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
        registry,
        hasPasscode = false,
        elapsedRealtime = { 0L },
        onboardingComplete = false,
    )

    /** A set-up vault whose owner already finished the guide once. */
    private fun setUpManager() = AppLockManager(
        PasscodeRepositoryImpl(passcodeStore),
        registry,
        hasPasscode = true,
        elapsedRealtime = { 0L },
        onboardingComplete = true,
    )

    @Test
    fun revisitDoesNotAlterAnUnsetPersistedFlagNorTheLockManager() = runTest {
        val lockManager = firstRunManager()
        assertFalse(onboardingStore.isCompleteBlocking())
        assertTrue(lockManager.showOnboarding.value)

        val recorded = recordOnboardingCompletion(OnboardingMode.REVISIT, lockManager, "pattern")

        assertFalse(recorded)
        assertFalse(onboardingStore.isCompleteBlocking())
        assertTrue(lockManager.showOnboarding.value)
        // A revisit cannot change the face setup would run on, either.
        assertEquals("calculator", lockManager.pendingDisguiseId.value)
        assertEquals(LockState.NeedsSetup, lockManager.lockState.value)
    }

    @Test
    fun revisitFromTheUnlockedVaultLeavesEverythingAsItWas() = runTest {
        onboardingStore.setComplete()
        passcodeStore.set(code, CalculatorDisguise.alphabet, "calculator")
        val lockManager = setUpManager()
        lockManager.commit(code, overflowed = false)
        assertEquals(LockState.Unlocked, lockManager.lockState.value)
        assertFalse(lockManager.showOnboarding.value)

        val recorded = recordOnboardingCompletion(OnboardingMode.REVISIT, lockManager, "numpad")

        assertFalse(recorded)
        assertTrue(onboardingStore.isCompleteBlocking())
        assertFalse(lockManager.showOnboarding.value)
        assertEquals(LockState.Unlocked, lockManager.lockState.value)
    }

    @Test
    fun firstRunRecordsTheInMemoryFlagAndTheChosenFaceButNotThePersistedOne() = runTest {
        val lockManager = firstRunManager()
        assertFalse(onboardingStore.isCompleteBlocking())

        val recorded = recordOnboardingCompletion(OnboardingMode.FIRST_RUN, lockManager, "numpad")

        assertTrue(recorded)
        // In-memory flag flips synchronously (the root switch leaves the guide
        // in the same composition) and the picked face is remembered …
        assertFalse(lockManager.showOnboarding.value)
        assertEquals("numpad", lockManager.pendingDisguiseId.value)
        // … but nothing is persisted yet: decisions §4 moves that to the first
        // envelope, so a crash here brings the guide back.
        assertFalse(onboardingStore.isCompleteBlocking())
        assertEquals(LockState.NeedsSetup, lockManager.lockState.value)
    }

    @Test
    fun theSentinelIsPersistedOnTheFirstUnlockOnly() = runTest {
        val lockState = MutableStateFlow<LockState>(LockState.NeedsSetup)
        var writes = 0
        val job = launch {
            OnboardingSentinelWriter.persistOnFirstUnlock(lockState) { writes += 1 }
        }

        testScheduler.advanceUntilIdle() // the collector sees NeedsSetup first

        // NeedsSetup → Unlocked: the first envelope just landed.
        lockState.value = LockState.Unlocked
        testScheduler.advanceUntilIdle()
        assertEquals(1, writes)

        // An ordinary lock/unlock cycle writes nothing more.
        lockState.value = LockState.Locked
        testScheduler.advanceUntilIdle()
        lockState.value = LockState.Unlocked
        testScheduler.advanceUntilIdle()
        assertEquals(1, writes)

        job.cancel()
    }

    @Test
    fun anErasedVaultWritesTheSentinelAgainOnTheNextSetup() = runTest {
        val lockState = MutableStateFlow<LockState>(LockState.Unlocked)
        var writes = 0
        val job = launch {
            OnboardingSentinelWriter.persistOnFirstUnlock(lockState) { writes += 1 }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(0, writes) // no NeedsSetup before it

        lockState.value = LockState.NeedsSetup // erase everything
        testScheduler.advanceUntilIdle()
        lockState.value = LockState.Unlocked // set up again
        testScheduler.advanceUntilIdle()
        assertEquals(1, writes)

        job.cancel()
    }
}
