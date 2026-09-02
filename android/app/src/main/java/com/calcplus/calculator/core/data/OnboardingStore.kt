package com.calcplus.calculator.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Persists whether the first-run guide was finished (or skipped). Erasing the
 * vault clears it, so the guide returns together with the fresh state.
 *
 * Shares the passcode store's DataStore file (one startup read serves both);
 * the key is namespaced so clear()/reset() never touch each other's entries.
 */
class OnboardingStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    /** Synchronous read — tests only; startup reads the shared prefs once. */
    fun isCompleteBlocking(): Boolean = runBlocking {
        dataStore.data.first()[KEY_COMPLETE] ?: false
    }

    suspend fun setComplete() {
        dataStore.edit { prefs -> prefs[KEY_COMPLETE] = true }
    }

    suspend fun reset() {
        dataStore.edit { prefs -> prefs.remove(KEY_COMPLETE) }
    }
}
