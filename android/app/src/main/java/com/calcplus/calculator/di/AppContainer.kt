package com.calcplus.calculator.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.calcplus.calculator.core.crypto.KeystoreWrapper
import com.calcplus.calculator.core.data.AlbumRepositoryImpl
import com.calcplus.calculator.core.data.ContactRepositoryImpl
import com.calcplus.calculator.core.data.NoteRepositoryImpl
import com.calcplus.calculator.core.data.OnboardingStore
import com.calcplus.calculator.core.data.PasscodeRepositoryImpl
import com.calcplus.calculator.core.data.PasscodeStore
import com.calcplus.calculator.core.data.VaultNuker
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.data.PhotoRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.core.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manual DI: one file, zero magic. Constructed in Application.onCreate; the
 * passcode-existence read is synchronous at process start (android-plan §2.3)
 * so the first composition already renders the correct mode.
 */
class AppContainer(context: Context, val applicationScope: CoroutineScope) {
    private val appContext = context.applicationContext

    // ONE prefs file for passcode blob + onboarding flag. The file keeps its
    // historical name: renaming it would orphan existing passcodes.
    private val prefsDataStore = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("passcode_store") }
    )

    val passcodeStore = PasscodeStore(prefsDataStore, KeystoreWrapper())

    val passcodeRepository: PasscodeRepository = PasscodeRepositoryImpl(passcodeStore)

    val onboardingStore = OnboardingStore(prefsDataStore)

    // ONE synchronous read at process start serves both flags (android-plan
    // §2.3) — not one runBlocking per store.
    private val startupPrefs = runBlocking { prefsDataStore.data.first() }

    val lockManager = AppLockManager(
        passcodeRepository = passcodeRepository,
        hasPasscode = startupPrefs[PasscodeStore.KEY_BLOB] != null,
        elapsedRealtime = { SystemClock.elapsedRealtime() },
        onboardingComplete = startupPrefs[OnboardingStore.KEY_COMPLETE] ?: false,
    )

    val database: SafeBoxDatabase by lazy { SafeBoxDatabase.build(appContext) }

    val photoFileStore: PhotoFileStore by lazy { PhotoFileStore(appContext.filesDir) }

    val albumRepository: AlbumRepository by lazy { AlbumRepositoryImpl(database, photoFileStore) }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepositoryImpl(database, photoFileStore, appContext.contentResolver, applicationScope)
    }

    val noteRepository: NoteRepository by lazy { NoteRepositoryImpl(database) }

    val contactRepository: ContactRepository by lazy { ContactRepositoryImpl(database) }

    val vaultNuker: VaultNuker by lazy {
        VaultNuker(database, photoFileStore, passcodeStore, onboardingStore, lockManager)
    }
}
