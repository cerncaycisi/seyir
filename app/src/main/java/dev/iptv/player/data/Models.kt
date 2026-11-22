package dev.iptv.player.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlaylistSource {
    @Serializable
    @SerialName("m3u")
    data class M3u(val url: String) : PlaylistSource

    @Serializable
    @SerialName("xtream")
    data class Xtream(val server: String, val username: String, val password: String) : PlaylistSource
}

@Serializable
data class Playlist(val id: String, val name: String, val source: PlaylistSource)

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Stream(
    val id: String,
    val name: String,
    val logo: String?,
    val url: String,
    val categoryId: String,
    val type: ContentType,
    val epgId: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val plot: String? = null,
    val backdrop: String? = null,
    val genre: String? = null,
    /** Epoch seconds the item was added to the panel (Xtream `added`); null for M3U (list order is used). */
    val added: Long? = null,
    /** Set on episode streams so history and deep links can find their series. */
    val seriesId: String? = null,
    /** Extra HTTP request headers (User-Agent, Referer…) the player should send for this stream. */
    val headers: Map<String, String> = emptyMap(),
    /** Catch-up mode as given by the playlist ("default", "append", "shift", "flussonic", "xc"…), null when unsupported. */
    val catchup: String? = null,
    val catchupDays: Int = 0,
    /** Catch-up URL template (M3U `catchup-source`), null when the mode implies a default. */
    val catchupSource: String? = null,
    /** Catch-up archive length in days (Xtream tv_archive_duration); 0 = none. */
    val archiveDays: Int = 0,
    /** Episode streams only: season and episode number (0 = unknown). */
    val season: Int = 0,
    val episode: Int = 0,
    val tmdbId: Long? = null,
)

data class Content(val categories: List<Category>, val items: List<Stream>)

data class Episode(val id: String, val title: String, val season: Int, val number: Int, val url: String)

data class SeriesInfo(
    val name: String,
    val cover: String?,
    val plot: String?,
    val episodes: List<Episode>,
    val backdrop: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val plotLanguage: String? = null,
    val tmdbId: Long? = null,
)

data class VodInfo(
    val plot: String?,
    val backdrop: String?,
    val genre: String?,
    val duration: String?,
    val year: String?,
    val rating: String?,
    val cast: String? = null,
    val director: String? = null,
    /** YouTube video id of the trailer, when the panel provides one. */
    val trailer: String? = null,
    val poster: String? = null,
    val durationSecs: Int? = null,
    /** True when some fields were filled in from TMDB. */
    val fromTmdb: Boolean = false,
    val plotLanguage: String? = null,
    val tmdbId: Long? = null,
)

/** Xtream `user_info` subset. [expiresAt] is epoch seconds, null for unlimited accounts. */
data class AccountInfo(
    val status: String?,
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val trial: Boolean = false,
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

fun favoriteKey(playlist: Playlist, stream: Stream) = "${playlist.id}|${stream.type}|${stream.id}"
