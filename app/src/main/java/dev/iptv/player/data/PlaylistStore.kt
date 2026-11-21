package dev.iptv.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iptv")

class PlaylistStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val playlistsKey = stringPreferencesKey("playlists")
    private val activeKey = stringPreferencesKey("active")
    private val favoritesKey = stringSetPreferencesKey("favorites")

    val playlists: Flow<List<Playlist>> = store.data.map { decode(it[playlistsKey]) }
    val activeId: Flow<String?> = store.data.map { it[activeKey] }
    val favorites: Flow<Set<String>> = store.data.map { it[favoritesKey].orEmpty() }

    suspend fun add(playlist: Playlist) {
        store.edit {
            it[playlistsKey] = json.encodeToString(decode(it[playlistsKey]) + playlist)
            it[activeKey] = playlist.id
        }
    }

    suspend fun remove(id: String) {
        store.edit {
            it[playlistsKey] = json.encodeToString(decode(it[playlistsKey]).filterNot { p -> p.id == id })
            if (it[activeKey] == id) it.remove(activeKey)
            it[favoritesKey] = it[favoritesKey].orEmpty().filterNot { key -> key.startsWith("$id|") }.toSet()
        }
    }

    suspend fun setActive(id: String) {
        store.edit { it[activeKey] = id }
    }

    suspend fun toggleFavorite(key: String) {
        store.edit {
            val current = it[favoritesKey].orEmpty()
            it[favoritesKey] = if (key in current) current - key else current + key
        }
    }

    suspend fun position(key: String): Long = store.data.first()[longPreferencesKey("pos:$key")] ?: 0L

    suspend fun savePosition(key: String, positionMs: Long) {
        store.edit {
            val k = longPreferencesKey("pos:$key")
            if (positionMs > 0) it[k] = positionMs else it.remove(k)
        }
    }

    private fun decode(raw: String?): List<Playlist> =
        raw?.let { runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrNull() }.orEmpty()
}
