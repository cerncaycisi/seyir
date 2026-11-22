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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iptv")

/** Everything this store holds, with credentials in clear text (used by backup/restore). */
data class StoreSnapshot(
    val playlists: List<Playlist>,
    val favorites: Set<String>,
    val positions: Map<String, Long>,
    val watchLater: Set<String> = emptySet(),
    val groups: List<ChannelGroup> = emptyList(),
)

class PlaylistStore(context: Context, private val codec: Codec = SecretBox) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val playlistsKey = stringPreferencesKey("playlists")
    private val activeKey = stringPreferencesKey("active")
    private fun favoritesKey(profileId: String = ProfileStore.activeProfileId) =
        stringSetPreferencesKey(ProfileStore.profileScopedKey("favorites", profileId))

    private fun watchLaterKey(profileId: String = ProfileStore.activeProfileId) =
        stringSetPreferencesKey(ProfileStore.profileScopedKey("watchlater", profileId))

    private fun groupsKey(profileId: String = ProfileStore.activeProfileId) =
        stringPreferencesKey(ProfileStore.profileScopedKey("groups", profileId))

    private fun decodeGroups(raw: String?): List<ChannelGroup> =
        if (raw.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString<List<ChannelGroup>>(raw) }.getOrDefault(emptyList())

    /** User channel groups of the active profile ("Gruplarım"). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groups: Flow<List<ChannelGroup>> = ProfileStore.activeIdFlow.flatMapLatest { id -> store.data.map { decodeGroups(it[groupsKey(id)]) } }

    suspend fun editGroups(change: (List<ChannelGroup>) -> List<ChannelGroup>) {
        store.edit {
            val k = groupsKey()
            it[k] = json.encodeToString(change(decodeGroups(it[k])))
        }
    }

    /** "Watch later" set of the active profile; same key format as [favorites] (see [favoriteKey]). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watchLater: Flow<Set<String>> = ProfileStore.activeIdFlow.flatMapLatest { id -> store.data.map { it[watchLaterKey(id)].orEmpty() } }

    /** Playlists with credentials decrypted. They are encrypted at rest (Xtream password, M3U URL). */
    val playlists: Flow<List<Playlist>> = store.data.map { prefs -> decode(prefs[playlistsKey]).map(::open) }
    val activeId: Flow<String?> = store.data.map { it[activeKey] }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favorites: Flow<Set<String>> = ProfileStore.activeIdFlow.flatMapLatest { id -> store.data.map { it[favoritesKey(id)].orEmpty() } }

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
            // Favorites of every profile (default: "favorites", others: "profile.<id>.favorites").
            for (k in it.asMap().keys.filter { k -> k.name in SET_NAMES || SET_NAMES.any { n -> k.name.endsWith(".$n") } }) {
                @Suppress("UNCHECKED_CAST")
                val fk = k as Preferences.Key<Set<String>>
                it[fk] = it[fk].orEmpty().filterNot { key -> key.startsWith("$id|") }.toSet()
            }
            // Channel groups of every profile hold the same keys.
            for (k in it.asMap().keys.filter { k -> k.name == "groups" || k.name.endsWith(".groups") }) {
                @Suppress("UNCHECKED_CAST")
                val gk = k as Preferences.Key<String>
                it[gk] = json.encodeToString(ChannelGroups.withoutPlaylist(decodeGroups(it[gk]), id))
            }
        }
    }

    suspend fun setActive(id: String) {
        store.edit { it[activeKey] = id }
    }

    suspend fun toggleFavorite(key: String) {
        store.edit {
            val fk = favoritesKey()
            val current = it[fk].orEmpty()
            it[fk] = toggled(current, key)
        }
    }

    suspend fun toggleWatchLater(key: String) {
        store.edit {
            val wk = watchLaterKey()
            it[wk] = toggled(it[wk].orEmpty(), key)
        }
    }

    suspend fun position(key: String): Long = store.data.first()[longPreferencesKey(ProfileStore.profileScopedKey("pos:$key"))] ?: 0L

    suspend fun savePosition(key: String, positionMs: Long) {
        store.edit {
            val k = longPreferencesKey(ProfileStore.profileScopedKey("pos:$key"))
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
        val scoped = ProfileStore.profileScopedKey(POSITION_PREFIX)
        val positions = prefs.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(scoped) && value is Long) key.name.removePrefix(scoped) to value else null
        }.toMap()
        return StoreSnapshot(
            playlists = decode(prefs[playlistsKey]).map(::open),
            favorites = prefs[favoritesKey()].orEmpty(),
            positions = positions,
            watchLater = prefs[watchLaterKey()].orEmpty(),
            groups = decodeGroups(prefs[groupsKey()]),
        )
    }

    /** Merges [data] into the store in one atomic edit; see [BackupManager.mergePlaylists] for the rules. */
    suspend fun merge(data: StoreSnapshot) {
        store.edit { prefs ->
            val current = decode(prefs[playlistsKey]).map(::open)
            prefs[playlistsKey] = json.encodeToString(BackupManager.mergePlaylists(current, data.playlists).map(::seal))
            prefs[favoritesKey()] = prefs[favoritesKey()].orEmpty() + data.favorites
            if (data.watchLater.isNotEmpty()) prefs[watchLaterKey()] = prefs[watchLaterKey()].orEmpty() + data.watchLater
            if (data.groups.isNotEmpty()) {
                val gk = groupsKey()
                prefs[gk] = json.encodeToString(ChannelGroups.merge(decodeGroups(prefs[gk]), data.groups))
            }
            data.positions.forEach { (key, ms) ->
                val k = longPreferencesKey(ProfileStore.profileScopedKey("$POSITION_PREFIX$key"))
                if (ms > (prefs[k] ?: 0L)) prefs[k] = ms
            }
        }
    }

    /** Resets favorites, watch-later, custom groups and saved positions while preserving playlists, then writes [newFavorites]. */
    suspend fun resetAndSetFavorites(newFavorites: Set<String>) {
        store.edit { prefs ->
            val keepKeys = setOf(playlistsKey.name, activeKey.name)
            for (k in prefs.asMap().keys.toList()) {
                if (k.name !in keepKeys) prefs.remove(k)
            }
            if (newFavorites.isNotEmpty()) {
                prefs[favoritesKey()] = newFavorites
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
        val SET_NAMES = listOf("favorites", "watchlater")
    }
}

/** Adds [key] to [set], or removes it when present (favorites and watch-later share this rule). */
fun toggled(set: Set<String>, key: String): Set<String> = if (key in set) set - key else set + key
