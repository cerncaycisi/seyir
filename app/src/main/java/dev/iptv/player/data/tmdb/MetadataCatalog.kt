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

/** One durable index per playlist/type. Screens only observe it; they never launch title scans. */
class MetadataCatalog(context: Context) {
    internal val dao = MetadataDb.get(context).dao()
    private val json = Json { ignoreUnknownKeys = true }

    // Diffing ~20k titles against the index: low priority, the user never waits on it.
    suspend fun reconcile(playlist: Playlist, type: ContentType, content: Content) = withContext(dev.iptv.player.util.BackgroundWork.dispatcher) {
        if (type != ContentType.LIVE) dao.reconcile(playlist.id, type.name, content.items.map {
            MetadataEntry(playlist.id, type.name, it.id, it.name, it.year, metadataIdentity(it))
        })
    }

    fun observe(playlistId: String, type: ContentType): Flow<Map<String, TmdbInfo>> {
        val decoded = HashMap<String, Pair<String, TmdbInfo>>()
        return dao.observeReady(playlistId, type.name).conflate().map { rows ->
            val ids = rows.mapTo(HashSet()) { it.id }
            decoded.keys.retainAll(ids)
            rows.mapNotNull { row ->
                val raw = row.info ?: return@mapNotNull null
                val cached = decoded[row.id]
                val info = if (cached?.first == raw) cached.second else runCatching { json.decodeFromString<TmdbInfo>(raw) }.getOrNull()
                info?.let { decoded[row.id] = raw to it; row.id to it }
            }.toMap()
        }.let { updates ->
            kotlinx.coroutines.flow.flow {
                updates.collect { emit(it); delay(2_000) }
            }
        }.flowOn(Dispatchers.Default)
    }
}
