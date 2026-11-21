package dev.iptv.player.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QueueItem(
    val id: String,
    val name: String,
    val logo: String? = null,
    val url: String,
    val categoryId: String = "",
    val type: String,
    val epgId: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val catchup: String? = null,
    val catchupDays: Int = 0,
    val catchupSource: String? = null,
    val archiveDays: Int = 0,
    val seriesId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
)

@Serializable
data class QueueSnapshot(val playlist: Playlist, val index: Int, val items: List<QueueItem>)

/**
 * Serialises the playback queue for process-death restore. The whole document is encrypted in one operation
 * because stream URLs routinely embed credentials.
 */
object QueueCodec {
    /** Longest queue persisted; longer lists are cut to a window around the current item. */
    const val MAX_ITEMS = 2000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(playlist: Playlist, items: List<Stream>, index: Int, codec: Codec): String {
        val safeIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val from = if (items.size <= MAX_ITEMS) 0 else (safeIndex - MAX_ITEMS / 2).coerceIn(0, items.size - MAX_ITEMS)
        val window = items.subList(from, minOf(items.size, from + MAX_ITEMS))
        val snapshot = QueueSnapshot(playlist, safeIndex - from, window.map(::toItem))
        return codec.encrypt(json.encodeToString(QueueSnapshot.serializer(), snapshot))
    }

    /** Returns null for missing, corrupt or undecryptable data. */
    fun decode(text: String, codec: Codec): Triple<Playlist, List<Stream>, Int>? = runCatching {
        val plain = codec.decrypt(text).takeIf { it.isNotEmpty() } ?: return null
        val snapshot = json.decodeFromString(QueueSnapshot.serializer(), plain)
        if (snapshot.items.isEmpty()) return null
        Triple(snapshot.playlist, snapshot.items.map(::fromItem), snapshot.index.coerceIn(0, snapshot.items.lastIndex))
    }.getOrNull()

    private fun toItem(s: Stream) = QueueItem(
        s.id, s.name, s.logo, s.url, s.categoryId, s.type.name, s.epgId, s.rating, s.year, s.headers,
        s.catchup, s.catchupDays, s.catchupSource, s.archiveDays, s.seriesId, s.season, s.episode,
    )

    private fun fromItem(i: QueueItem) = Stream(
        id = i.id,
        name = i.name,
        logo = i.logo,
        url = i.url,
        categoryId = i.categoryId,
        type = ContentType.valueOf(i.type),
        epgId = i.epgId,
        rating = i.rating,
        year = i.year,
        headers = i.headers,
        catchup = i.catchup,
        catchupDays = i.catchupDays,
        catchupSource = i.catchupSource,
        archiveDays = i.archiveDays,
        seriesId = i.seriesId,
        season = i.season,
        episode = i.episode,
    )
}
