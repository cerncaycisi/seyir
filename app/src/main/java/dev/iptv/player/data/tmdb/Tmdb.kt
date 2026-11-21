package dev.iptv.player.data.tmdb

import android.content.Context
import dev.iptv.player.BuildConfig
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What TMDB knows about a title; every field is optional and only used to fill gaps left by the panel. */
@Serializable
data class TmdbInfo(
    val tmdbId: Long = 0,
    val title: String? = null,
    val plot: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String? = null,
    val rating: String? = null,
) {
    val isEmpty get() = tmdbId == 0L
}

/**
 * Looks up missing poster / plot / backdrop on TMDB. Disabled (returns null) when no key was baked in via
 * `tmdb.apiKey` in local.properties or the user switched [Setting.TmdbEnabled] off. Results, including
 * misses, are cached on disk so each title is searched once.
 */
class Tmdb(context: Context, private val settings: SettingsStore, private val apiKey: String = BuildConfig.TMDB_API_KEY) {
    private val dir = File(context.applicationContext.cacheDir, "tmdb").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = apiKey.isNotBlank()

    suspend fun available(): Boolean = configured && settings.get(Setting.TmdbEnabled)

    suspend fun lookup(rawTitle: String, type: ContentType, yearHint: String? = null): TmdbInfo? {
        if (type == ContentType.LIVE || !available()) return null
        val cleaned = TitleCleaner.clean(rawTitle)
        if (cleaned.title.length < 2) return null
        val year = cleaned.year ?: yearHint?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val file = File(dir, hash("$type|${cleaned.title.lowercase()}|$year") + ".json")
        readCache(file)?.let { return it.takeUnless { c -> c.isEmpty } }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val tr = search(cleaned.title, year, type, "tr-TR")
                when {
                    tr == null -> search(cleaned.title, year, type, "en-US")
                    tr.plot == null -> tr.copy(plot = search(cleaned.title, year, type, "en-US")?.plot)
                    else -> tr
                }
            }
        }
        // Only cache real answers; a network failure should be retried later.
        result.getOrNull()?.let { file.writeText(json.encodeToString(it)); return it }
        if (result.isSuccess) file.writeText(json.encodeToString(TmdbInfo()))
        return null
    }

    private fun readCache(file: File): TmdbInfo? =
        if (file.exists()) runCatching { json.decodeFromString<TmdbInfo>(file.readText()) }.getOrNull() else null

    private fun search(title: String, year: String?, type: ContentType, language: String): TmdbInfo? {
        val kind = if (type == ContentType.SERIES) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/search/$kind".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", title)
            .addQueryParameter("language", language)
            .addQueryParameter("include_adult", "false")
            .apply { year?.let { addQueryParameter(if (kind == "tv") "first_air_date_year" else "year", it) } }
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("TMDB HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val results = (json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray ?: return null
        val hit = results.firstNotNullOfOrNull { it as? JsonObject } ?: return null
        fun str(key: String) = (hit[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        val id = str("id")?.toLongOrNull() ?: return null
        val rating = str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }
        return TmdbInfo(
            tmdbId = id,
            title = str("title") ?: str("name"),
            plot = str("overview"),
            poster = str("poster_path")?.let { "$IMAGE_BASE/w500$it" },
            backdrop = str("backdrop_path")?.let { "$IMAGE_BASE/w1280$it" },
            year = (str("release_date") ?: str("first_air_date"))?.take(4),
            rating = rating?.let { String.format(java.util.Locale.US, "%.1f", it) },
        )
    }

    private fun hash(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    }
}
