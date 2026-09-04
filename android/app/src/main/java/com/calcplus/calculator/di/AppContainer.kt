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
import com.calcplus.calculator.core.data.SortPrefsStore
import com.calcplus.calculator.core.data.TrashRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.core.domain.repository.SortPreferences
import com.calcplus.calculator.core.domain.repository.TrashRepository
import com.calcplus.calculator.core.disguise.AppIconManager
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.lock.AppLockManager
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.pattern.PatternDisguise
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

    /**
     * Album/note sort choices (decisions §4) — Flow-only, so it adds no second
     * blocking read to process start.
     */
    val sortPreferences: SortPreferences = SortPrefsStore(prefsDataStore)

    // ONE synchronous read at process start serves both flags (android-plan
    // §2.3) — not one runBlocking per store.
    private val startupPrefs = runBlocking { prefsDataStore.data.first() }

    /**
     * The compiled-in lock faces, in registry order (iteration-3-decisions
     * §1.6). Append-only: a shipped face is never removed.
     */
    val disguiseRegistry = DisguiseRegistry(
        listOf(CalculatorDisguise, NumpadDisguise, PatternDisguise)
    )

    /**
     * Home-screen cover identities (decisions §9a). The alias list is derived
     * from the registry, in registry order, so the default face's alias is
     * first — which is also the one the manifest ships enabled.
     */
    val appIconManager: AppIconManager =
        AppIconManager.create(appContext, disguiseRegistry.faces.map { it.coverAlias })

    val lockManager = AppLockManager(
        passcodeRepository = passcodeRepository,
        registry = disguiseRegistry,
        hasPasscode = startupPrefs[PasscodeStore.KEY_BLOB] != null,
        elapsedRealtime = { SystemClock.elapsedRealtime() },
        onboardingComplete = startupPrefs[OnboardingStore.KEY_COMPLETE] ?: false,
        // The face comes from the SAME startup snapshot as the two flags above
        // — the mirror key, never the envelope. Unwrapping the envelope here
        // would mean Keystore work in Application.onCreate (decisions §3).
        initialActiveDisguiseId = startupPrefs[PasscodeStore.KEY_ACTIVE_DISGUISE],
        // Reconciled on background only (see AppLockManager.onAppStop), and
        // best-effort: a stale icon over a correctly re-enrolled vault is
        // cosmetic, and it can never be stale anywhere the user can see it.
        reconcileCoverIdentity = { face -> appIconManager.apply(face) },
    )

    val database: SafeBoxDatabase by lazy { SafeBoxDatabase.build(appContext) }

    val photoFileStore: PhotoFileStore by lazy { PhotoFileStore(appContext.filesDir) }

    val albumRepository: AlbumRepository by lazy { AlbumRepositoryImpl(database, photoFileStore) }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepositoryImpl(database, photoFileStore, appContext.contentResolver, applicationScope)
    }

    val noteRepository: NoteRepository by lazy { NoteRepositoryImpl(database) }

    val contactRepository: ContactRepository by lazy { ContactRepositoryImpl(database) }

    /** "Recently deleted" (decisions §3): reads trash, restores and purges. */
    val trashRepository: TrashRepository by lazy {
        TrashRepositoryImpl(
            database = database,
            albumRepository = albumRepository,
            photoRepository = photoRepository,
            noteRepository = noteRepository,
            contactRepository = contactRepository,
        )
    }

    val vaultNuker: VaultNuker by lazy {
        VaultNuker(
            database,
            photoFileStore,
            passcodeStore,
            onboardingStore,
            sortPreferences,
            lockManager,
        )
    }
}
