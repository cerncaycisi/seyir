package dev.iptv.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

/** One watched movie / episode / live channel. [favKey] is the unscoped [favoriteKey] of the stream. */
@Serializable
data class HistoryEntry(
    val key: String,
    val favKey: String,
    val playlistId: String,
    val id: String,
    val type: ContentType,
    val name: String,
    val logo: String? = null,
    val url: String = "",
    val categoryId: String = "",
    val seriesId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Same rule the player uses to reset a resume point: inside the last minute (or 5%) counts as done. */
    val finished: Boolean
        get() = durationMs > 0 && positionMs > durationMs - minOf(60_000L, durationMs / 20)

    /** Worth offering under "continue watching". */
    val resumable: Boolean
        get() = type != ContentType.LIVE && !finished && positionMs >= MIN_RESUME_MS

    fun toStream() = Stream(id, name, logo, url, categoryId, type, seriesId = seriesId, season = season, episode = episode)

    companion object {
        const val MIN_RESUME_MS = 15_000L
    }
}

/**
 * Watch history kept in its own DataStore as one JSON document (small, capped list).
 * All keys pass through [scoped] so a per-profile prefix can be applied in a single place.
 */
class WatchHistory(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val store = appContext.historyDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val entriesKey = stringPreferencesKey("entries")
    private val watchNext = WatchNextPublisher(appContext)

    /** Per-profile key namespace ("" for the default profile); follows [ProfileStore.activeIdFlow]. */
    private val prefix = kotlinx.coroutines.flow.MutableStateFlow(ProfileStore.profileScopedKey(""))
    var keyPrefix: String
        get() = prefix.value
        set(v) { prefix.value = v }

    fun scoped(favKey: String) = keyPrefix + favKey

    /** Newest first, for the current key prefix. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: Flow<List<HistoryEntry>> = prefix.flatMapLatest { p ->
        store.data.map { prefs -> decode(prefs[entriesKey]).filter { historyBelongsTo(it.key, p) }.sortedByDescending { it.updatedAt } }
    }

    suspend fun entry(playlist: Playlist, stream: Stream): HistoryEntry? {
        val key = scoped(favoriteKey(playlist, stream))
        return entries.first().firstOrNull { it.key == key }
    }

    /** Fire-and-forget; safe to call from the player on any thread. Live channels store only "last watched". */
    fun record(playlist: Playlist, stream: Stream, positionMs: Long = 0, durationMs: Long = 0) {
        val fav = favoriteKey(playlist, stream)
        val entry = HistoryEntry(
            key = scoped(fav),
            favKey = fav,
            playlistId = playlist.id,
            id = stream.id,
            type = stream.type,
            name = stream.name,
            logo = stream.logo,
            url = stream.url,
            categoryId = stream.categoryId,
            seriesId = stream.seriesId,
            season = stream.season,
            episode = stream.episode,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis(),
        )
        scope.launch {
            var snapshot: List<HistoryEntry> = emptyList()
            store.edit { prefs ->
                val all = decode(prefs[entriesKey]).filterNot { it.key == entry.key } + entry
                snapshot = trim(all)
                prefs[entriesKey] = json.encodeToString(snapshot)
            }
            watchNext.sync(snapshot.filter { historyBelongsTo(it.key, keyPrefix) })
        }
    }

    suspend fun clear() {
        val p = keyPrefix
        store.edit { prefs ->
            prefs[entriesKey] = json.encodeToString(decode(prefs[entriesKey]).filterNot { historyBelongsTo(it.key, p) })
        }
        watchNext.sync(emptyList())
    }

    /** Replaces the entire watch history with [items] (newest first). */
    suspend fun replaceAll(items: List<HistoryEntry>) {
        val snapshot = trim(items)
        store.edit { prefs ->
            prefs[entriesKey] = json.encodeToString(snapshot)
        }
        watchNext.sync(snapshot.filter { historyBelongsTo(it.key, keyPrefix) })
    }

    /** Clears the active profile's history of one content type (live, movies or series). */
    suspend fun clearType(type: ContentType) {
        val p = keyPrefix
        var snapshot: List<HistoryEntry> = emptyList()
        store.edit { prefs ->
            snapshot = historyWithoutType(decode(prefs[entriesKey]), p, type)
            prefs[entriesKey] = json.encodeToString(snapshot)
        }
        watchNext.sync(snapshot.filter { historyBelongsTo(it.key, p) })
    }

    /** Removes one movie / channel, or every episode of a series, from the active profile's history. */
    suspend fun remove(playlist: Playlist, stream: Stream) {
        val p = keyPrefix
        store.edit { prefs ->
            prefs[entriesKey] = json.encodeToString(historyWithoutItem(decode(prefs[entriesKey]), p, playlist, stream))
        }
        watchNext.sync(decode(store.data.first()[entriesKey]).filter { historyBelongsTo(it.key, p) })
    }

    suspend fun removePlaylist(playlistId: String) {
        store.edit { prefs ->
            prefs[entriesKey] = json.encodeToString(decode(prefs[entriesKey]).filterNot { it.playlistId == playlistId })
        }
    }

    private fun trim(all: List<HistoryEntry>): List<HistoryEntry> {
        val sorted = all.sortedByDescending { it.updatedAt }
        val live = sorted.filter { it.type == ContentType.LIVE }.take(MAX_LIVE)
        val vod = sorted.filter { it.type != ContentType.LIVE }.take(MAX_VOD)
        return live + vod
    }

    private fun decode(raw: String?): List<HistoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val MAX_LIVE = 30
        const val MAX_VOD = 300
    }
}

/** Default-profile keys have no prefix; an empty startsWith check would match every profile. */
internal fun historyBelongsTo(key: String, prefix: String): Boolean =
    if (prefix.isEmpty()) !key.startsWith("profile.") else key.startsWith(prefix)

internal fun historyWithoutType(entries: List<HistoryEntry>, prefix: String, type: ContentType) =
    entries.filterNot { historyBelongsTo(it.key, prefix) && it.type == type }

internal fun historyWithoutItem(entries: List<HistoryEntry>, prefix: String, playlist: Playlist, stream: Stream): List<HistoryEntry> {
    val key = prefix + favoriteKey(playlist, stream)
    return entries.filterNot {
        historyBelongsTo(it.key, prefix) && (it.key == key ||
            (stream.type == ContentType.SERIES && it.playlistId == playlist.id && it.seriesId == stream.id))
    }
}

internal fun watchNextItems(entries: List<HistoryEntry>, limit: Int): List<HistoryEntry> =
    continueWatching(entries).distinctBy { Triple(it.playlistId, it.type, it.seriesId ?: it.id) }.take(limit)

/** Latest resumable movie / episode per series, newest first; the rule behind the continue-watching row. */
fun continueWatching(entries: List<HistoryEntry>): List<HistoryEntry> {
    val seen = HashSet<String>()
    return entries
        .filter { it.type != ContentType.LIVE }
        .sortedByDescending { it.updatedAt }
        .filter { seen.add(it.seriesId?.let { s -> "${it.playlistId}|series|$s" } ?: it.favKey) }
        .filter { it.resumable }
}
