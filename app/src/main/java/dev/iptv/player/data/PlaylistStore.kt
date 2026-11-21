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

/** Everything this store holds, with credentials in clear text (used by backup/restore). */
data class StoreSnapshot(
    val playlists: List<Playlist>,
    val favorites: Set<String>,
    val positions: Map<String, Long>,
)

class PlaylistStore(context: Context, private val codec: Codec = SecretBox) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val playlistsKey = stringPreferencesKey("playlists")
    private val activeKey = stringPreferencesKey("active")
    private val favoritesKey = stringSetPreferencesKey("favorites")

    /** Playlists with credentials decrypted. They are encrypted at rest (Xtream password, M3U URL). */
    val playlists: Flow<List<Playlist>> = store.data.map { prefs -> decode(prefs[playlistsKey]).map(::open) }
    val activeId: Flow<String?> = store.data.map { it[activeKey] }
    val favorites: Flow<Set<String>> = store.data.map { it[favoritesKey].orEmpty() }

    suspend fun add(playlist: Playlist) {
        store.edit {
            it[playlistsKey] = json.encodeToString(decode(it[playlistsKey]) + seal(playlist))
            it[activeKey] = playlist.id
        }
    }

    /** Replaces the playlist with the same id (name / credentials edit). Does nothing if it does not exist. */
    suspend fun update(playlist: Playlist) {
        store.edit {
            it[playlistsKey] = json.encodeToString(decode(it[playlistsKey]).map { p -> if (p.id == playlist.id) seal(playlist) else p })
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

    /** Rewrites legacy plain-text credentials in encrypted form. Safe to call on every start. */
    suspend fun migrateCredentials() {
        store.edit {
            val raw = it[playlistsKey] ?: return@edit
            val sealed = json.encodeToString(decode(raw).map(::seal))
            if (sealed != raw) it[playlistsKey] = sealed
        }
    }

    suspend fun snapshot(): StoreSnapshot {
        val prefs = store.data.first()
        val positions = prefs.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(POSITION_PREFIX) && value is Long) key.name.removePrefix(POSITION_PREFIX) to value else null
        }.toMap()
        return StoreSnapshot(decode(prefs[playlistsKey]).map(::open), prefs[favoritesKey].orEmpty(), positions)
    }

    /** Merges [data] into the store in one atomic edit; see [BackupManager.mergePlaylists] for the rules. */
    suspend fun merge(data: StoreSnapshot) {
        store.edit { prefs ->
            val current = decode(prefs[playlistsKey]).map(::open)
            prefs[playlistsKey] = json.encodeToString(BackupManager.mergePlaylists(current, data.playlists).map(::seal))
            prefs[favoritesKey] = prefs[favoritesKey].orEmpty() + data.favorites
            data.positions.forEach { (key, ms) ->
                val k = longPreferencesKey("$POSITION_PREFIX$key")
                if (ms > (prefs[k] ?: 0L)) prefs[k] = ms
            }
        }
    }

    private fun decode(raw: String?): List<Playlist> =
        raw?.let { runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrNull() }.orEmpty()

    private fun seal(p: Playlist) = p.mapSecrets(codec::encrypt)
    private fun open(p: Playlist) = p.mapSecrets(codec::decrypt)

    private fun Playlist.mapSecrets(f: (String) -> String): Playlist = when (val s = source) {
        is PlaylistSource.M3u -> copy(source = s.copy(url = f(s.url)))
        is PlaylistSource.Xtream -> copy(source = s.copy(password = f(s.password)))
    }

    private companion object {
        const val POSITION_PREFIX = "pos:"
    }
}
