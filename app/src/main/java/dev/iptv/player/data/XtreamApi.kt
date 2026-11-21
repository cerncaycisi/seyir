package dev.iptv.player.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

class XtreamApi(source: PlaylistSource.Xtream, private val http: OkHttpClient) {
    private val base = source.server.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }
    private val user = source.username.trim()
    private val pass = source.password.trim()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun call(params: String): JsonElement = withContext(Dispatchers.IO) {
        val url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}$params"
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sunucu hatası: HTTP ${response.code}")
            json.parseToJsonElement(response.body?.string().orEmpty().ifBlank { "null" })
        }
    }

    suspend fun login(): Boolean = (call("") as? JsonObject)?.obj("user_info")?.str("auth") == "1"

    suspend fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        return call("&action=$action").objects().mapNotNull { o ->
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: id)
        }
    }

    suspend fun streams(type: ContentType): List<Stream> = when (type) {
        ContentType.LIVE -> call("&action=get_live_streams").objects().mapNotNull { o ->
            val id = o.str("stream_id") ?: return@mapNotNull null
            Stream(
                id, o.str("name").orEmpty(), o.str("stream_icon").blankToNull(), "$base/live/$user/$pass/$id.ts",
                o.str("category_id").orEmpty(), type, o.str("epg_channel_id").blankToNull(),
            )
        }
        ContentType.MOVIE -> call("&action=get_vod_streams").objects().mapNotNull { o ->
            val id = o.str("stream_id") ?: return@mapNotNull null
            val ext = o.str("container_extension").blankToNull() ?: "mp4"
            Stream(
                id = id,
                name = o.str("name").orEmpty(),
                logo = o.str("stream_icon").blankToNull(),
                url = "$base/movie/$user/$pass/$id.$ext",
                categoryId = o.str("category_id").orEmpty(),
                type = type,
                rating = o.str("rating").cleanRating(),
                year = o.str("year").blankToNull(),
                added = o.str("added")?.toLongOrNull(),
            )
        }
        ContentType.SERIES -> call("&action=get_series").objects().mapNotNull { o ->
            val id = o.str("series_id") ?: return@mapNotNull null
            Stream(
                id = id,
                name = o.str("name").orEmpty(),
                logo = o.str("cover").blankToNull(),
                url = "",
                categoryId = o.str("category_id").orEmpty(),
                type = type,
                rating = o.str("rating").cleanRating(),
                year = (o.str("releaseDate") ?: o.str("release_date"))?.take(4).blankToNull(),
                plot = o.str("plot").blankToNull(),
                backdrop = o.firstStr("backdrop_path"),
                genre = o.str("genre").blankToNull(),
                added = o.str("last_modified")?.toLongOrNull(),
            )
        }
    }

    suspend fun vodInfo(vodId: String): VodInfo? {
        val root = call("&action=get_vod_info&vod_id=${enc(vodId)}") as? JsonObject ?: return null
        val info = root.obj("info") ?: return null
        return VodInfo(
            plot = (info.str("plot") ?: info.str("description")).blankToNull(),
            backdrop = info.firstStr("backdrop_path"),
            genre = info.str("genre").blankToNull(),
            duration = info.str("duration").blankToNull(),
            year = (info.str("releasedate") ?: info.str("release_date"))?.take(4).blankToNull(),
            rating = info.str("rating").cleanRating(),
            cast = (info.str("cast") ?: info.str("actors")).blankToNull(),
            director = info.str("director").blankToNull(),
            trailer = info.str("youtube_trailer").blankToNull()?.let { youtubeId(it) },
            poster = (info.str("movie_image") ?: info.str("cover_big")).blankToNull(),
            durationSecs = info.str("duration_secs")?.toIntOrNull()?.takeIf { it > 0 },
        )
    }

    suspend fun seriesInfo(seriesId: String): SeriesInfo {
        val root = call("&action=get_series_info&series_id=${enc(seriesId)}") as? JsonObject
            ?: throw IOException("Dizi bulunamadı")
        val raw = mutableListOf<JsonObject>()
        // Panels return episodes either as {"1": [...]} or as nested arrays.
        fun collect(element: JsonElement?) {
            when (element) {
                is JsonObject -> if ("episode_num" in element) raw += element else element.values.forEach { collect(it) }
                is JsonArray -> element.forEach { collect(it) }
                else -> Unit
            }
        }
        collect(root["episodes"])
        val episodes = raw.mapNotNull { o ->
            val id = o.str("id") ?: return@mapNotNull null
            val number = o.str("episode_num")?.toIntOrNull() ?: 0
            val ext = o.str("container_extension").blankToNull() ?: "mp4"
            Episode(
                id, o.str("title").blankToNull() ?: "Bölüm $number", o.str("season")?.toIntOrNull() ?: 1, number,
                "$base/series/$user/$pass/$id.$ext",
            )
        }.sortedWith(compareBy({ it.season }, { it.number }))
        val info = root.obj("info")
        return SeriesInfo(
            name = info?.str("name").orEmpty(),
            cover = info?.str("cover").blankToNull(),
            plot = info?.str("plot").blankToNull(),
            episodes = episodes,
            backdrop = info?.firstStr("backdrop_path"),
            genre = info?.str("genre").blankToNull(),
            rating = info?.str("rating").cleanRating(),
            year = (info?.str("releaseDate") ?: info?.str("release_date"))?.take(4).blankToNull(),
        )
    }

    suspend fun shortEpg(streamId: String): List<EpgEntry> =
        (call("&action=get_short_epg&stream_id=${enc(streamId)}&limit=2") as? JsonObject)?.get("epg_listings").objects().mapNotNull { o ->
            EpgEntry(
                title = decodeBase64(o.str("title").orEmpty()),
                start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null,
                end = o.str("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null,
            )
        }

    private fun decodeBase64(value: String) =
        runCatching { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(value)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

// Panels send backdrop_path either as a string or as an array of URLs.
private fun JsonObject.firstStr(key: String): String? = when (val e = this[key]) {
    is JsonArray -> (e.firstOrNull() as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    is JsonPrimitive -> e.takeUnless { it is JsonNull }?.content
    else -> null
}.blankToNull()

private fun String?.cleanRating(): String? {
    val value = this?.trim()?.toDoubleOrNull() ?: return null
    return if (value > 0) String.format(java.util.Locale.US, "%.1f", value) else null
}

/** Panels send either a bare video id or a full YouTube URL. */
internal fun youtubeId(value: String): String? {
    val v = value.trim()
    Regex("""(?:v=|youtu\.be/|embed/)([A-Za-z0-9_-]{6,})""").find(v)?.let { return it.groupValues[1] }
    return v.takeIf { Regex("[A-Za-z0-9_-]{6,20}").matches(it) }
}
