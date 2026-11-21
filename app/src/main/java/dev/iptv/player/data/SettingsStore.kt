package dev.iptv.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Typed setting with its default; the single place every feature reads and writes preferences. */
sealed class Setting<T>(val key: Preferences.Key<T>, val default: T) {
    // General
    data object Language : Setting<String>(stringPreferencesKey("language"), "system")
    data object Clock24h : Setting<Boolean>(booleanPreferencesKey("clock_24h"), true)
    data object StartWithLastChannel : Setting<Boolean>(booleanPreferencesKey("start_last_channel"), false)
    data object DefaultSection : Setting<String>(stringPreferencesKey("default_section"), "HOME")
    data object AutoRefreshHours : Setting<Int>(intPreferencesKey("auto_refresh_hours"), 24)

    // Parental control
    data object ParentalPin : Setting<String>(stringPreferencesKey("parental_pin"), "")
    data object LockedCategories : Setting<Set<String>>(stringSetPreferencesKey("locked_categories"), emptySet())
    data object HideAdult : Setting<Boolean>(booleanPreferencesKey("hide_adult"), false)

    // Channel editing
    data object HiddenChannels : Setting<Set<String>>(stringSetPreferencesKey("hidden_channels"), emptySet())
    data object HiddenCategories : Setting<Set<String>>(stringSetPreferencesKey("hidden_categories"), emptySet())

    // EPG
    data object EpgUrlOverride : Setting<String>(stringPreferencesKey("epg_url_override"), "")
    data object EpgOffsetMinutes : Setting<Int>(intPreferencesKey("epg_offset_minutes"), 0)

    // Player
    data object AspectMode : Setting<String>(stringPreferencesKey("aspect_mode"), "FIT")
    data object PreferredAudioLanguage : Setting<String>(stringPreferencesKey("audio_lang"), "tr")
    data object PreferredSubtitleLanguage : Setting<String>(stringPreferencesKey("subtitle_lang"), "tr")
    data object SubtitleScale : Setting<Int>(intPreferencesKey("subtitle_scale"), 100)
    data object BufferSeconds : Setting<Int>(intPreferencesKey("buffer_seconds"), 15)
    data object UserAgent : Setting<String>(stringPreferencesKey("user_agent"), "IPTVPlayer/0.1")
    data object AutoReconnect : Setting<Boolean>(booleanPreferencesKey("auto_reconnect"), true)
    data object ExternalPlayer : Setting<String>(stringPreferencesKey("external_player"), "")
    data object MatchFrameRate : Setting<Boolean>(booleanPreferencesKey("match_frame_rate"), false)

    // Metadata
    data object TmdbEnabled : Setting<Boolean>(booleanPreferencesKey("tmdb_enabled"), true)
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    fun <T> flow(setting: Setting<T>): Flow<T> = store.data.map { it[setting.key] ?: setting.default }

    suspend fun <T> get(setting: Setting<T>): T = flow(setting).first()

    suspend fun <T> set(setting: Setting<T>, value: T) {
        store.edit { it[setting.key] = value }
    }
}
