package dev.iptv.player.data.tmdb

import android.content.Context
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import java.util.concurrent.ConcurrentHashMap

/** One durable index per playlist/type. Screens only observe it; they never launch title scans. */
class MetadataCatalog(context: Context) {
    internal val dao = MetadataDb.get(context).dao()
    private val json = Json { ignoreUnknownKeys = true }
    private val decodedCache = ConcurrentHashMap<MetadataCatalogKey, ConcurrentHashMap<String, Pair<String, TmdbInfo>>>()
    private val snapshots = ConcurrentHashMap<MetadataCatalogKey, Map<String, TmdbInfo>>()

    /** Latest decoded snapshot for [playlistId] + [type], so returning from a detail page renders on frame 0 without re-querying. */
    fun cached(playlistId: String, type: ContentType): Map<String, TmdbInfo>? =
        snapshots[MetadataCatalogKey(playlistId, type.name)]

    // Diffing ~20k titles against the index: low priority, the user never waits on it.
    suspend fun reconcile(playlist: Playlist, type: ContentType, content: Content) = withContext(dev.iptv.player.util.BackgroundWork.dispatcher) {
        if (type != ContentType.LIVE) dao.reconcile(playlist.id, type.name, content.items.map {
            MetadataEntry(playlist.id, type.name, it.id, it.name, it.year, metadataIdentity(it), tmdbId = it.tmdbId)
        })
    }

    fun observe(playlistId: String, type: ContentType): Flow<Map<String, TmdbInfo>> {
        val key = MetadataCatalogKey(playlistId, type.name)
        val decoded = decodedCache.getOrPut(key) { ConcurrentHashMap() }
        return dao.observeReady(playlistId, type.name).conflate().map { rows ->
            val ids = rows.mapTo(HashSet()) { it.id }
            decoded.keys.retainAll(ids)
            rows.mapNotNull { row ->
                val raw = row.info ?: return@mapNotNull null
                val cached = decoded[row.id]
                val info = if (cached?.first == raw) cached.second else runCatching { json.decodeFromString<TmdbInfo>(raw) }.getOrNull()
                info?.let { decoded[row.id] = raw to it; row.id to it }
            }.toMap().also { snapshots[key] = it }
        }.let { updates ->
            kotlinx.coroutines.flow.flow {
                updates.collect { emit(it); delay(2_000) }
            }
        }.flowOn(Dispatchers.Default)
    }
}
