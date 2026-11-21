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
    /** Catch-up archive length in days (Xtream tv_archive_duration); 0 = none. */
    val archiveDays: Int = 0,
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
)

data class VodInfo(
    val plot: String?,
    val backdrop: String?,
    val genre: String?,
    val duration: String?,
    val year: String?,
    val rating: String?,
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

fun favoriteKey(playlist: Playlist, stream: Stream) = "${playlist.id}|${stream.type}|${stream.id}"
