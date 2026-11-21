package dev.iptv.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.releaseDataStore: DataStore<Preferences> by preferencesDataStore(name = "release_prefs")

/** Release/legal flags (kept in its own DataStore so it never collides with [SettingsStore]). */
class ReleasePrefs(context: Context) {
    private val store = context.applicationContext.releaseDataStore
    private val onboardingKey = booleanPreferencesKey("onboarding_accepted")

    val onboardingAccepted: Flow<Boolean> = store.data.map { it[onboardingKey] ?: false }

    suspend fun acceptOnboarding() {
        store.edit { it[onboardingKey] = true }
    }
}
