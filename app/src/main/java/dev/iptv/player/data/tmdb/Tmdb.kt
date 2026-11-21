package dev.iptv.player.data.tmdb

import android.content.Context
import dev.iptv.player.BuildConfig
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val plotLanguage: String? = null,
) {
    val isEmpty get() = tmdbId == 0L
}

/**
 * Looks up missing poster / plot / backdrop on TMDB. Disabled (returns null) when no key was baked in via
 * `tmdb.apiKey` in local.properties or the user switched [Setting.TmdbEnabled] off. Results, including
 * misses, are cached on disk so each title is searched once.
 */
class Tmdb(context: Context, private val settings: SettingsStore, private val apiKey: String = BuildConfig.TMDB_API_KEY) {
    private val dir = File(context.applicationContext.cacheDir, "tmdb-v3").apply { mkdirs() }
    private val memory = java.util.concurrent.ConcurrentHashMap<String, TmdbInfo>()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = apiKey.isNotBlank()

    suspend fun available(): Boolean = configured && settings.get(Setting.TmdbEnabled)

    suspend fun genres(type: ContentType): List<String> = withContext(Dispatchers.IO) {
        if (!available()) emptyList() else try { genreNames(if (type == ContentType.SERIES) "tv" else "movie").values.sorted() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun lookup(rawTitle: String, type: ContentType, yearHint: String? = null): TmdbInfo? = withContext(Dispatchers.IO) {
        if (type == ContentType.LIVE || !available()) return@withContext null
        val cleaned = TitleCleaner.clean(rawTitle)
        if (cleaned.title.length < 2) return@withContext null
        val year = cleaned.year ?: yearHint?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val file = File(dir, hash("$type|${cleaned.title.lowercase()}|$year") + ".json")
        memory[file.name]?.let { return@withContext it.takeUnless { c -> c.isEmpty } }
        readCache(file)?.let { memory[file.name] = it; return@withContext it.takeUnless { c -> c.isEmpty } }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val tr = search(cleaned.title, year, type, "tr-TR")
                if (tr == null) search(cleaned.title, year, type, "en-US")
                else if (tr.plot == null) {
                    val en = search(cleaned.title, year, type, "en-US")
                    tr.copy(plot = en?.plot, plotLanguage = if (en?.plot != null) "en" else null)
                } else tr
            }
        }
        // Only cache real answers; a network failure should be retried later.
        result.getOrNull()?.let { runCatching { file.writeText(json.encodeToString(it)) }; memory[file.name] = it; return@withContext it }
        if (result.isSuccess) { runCatching { file.writeText(json.encodeToString(TmdbInfo())) }; memory[file.name] = TmdbInfo() }
        null
    }

    /** Credits are fetched only for a detail/pause panel, never for every catalog item. */
    suspend fun details(rawTitle: String, type: ContentType, yearHint: String? = null): TmdbInfo? {
        val basic = lookup(rawTitle, type, yearHint) ?: return null
        return withContext(Dispatchers.IO) {
            val file = File(dir, "credits-$type-${basic.tmdbId}.json")
            readCache(file)?.let { return@withContext it }
            try {
                val kind = if (type == ContentType.SERIES) "tv" else "movie"
                val root = request("/$kind/${basic.tmdbId}", mapOf("language" to "tr-TR", "append_to_response" to "credits"))
                val credits = root["credits"] as? JsonObject
                val cast = (credits?.get("cast") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }.take(12)
                val director = (credits?.get("crew") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .filter { it.str("job") == "Director" }.mapNotNull { it.str("name") }.distinct()
                basic.copy(plot = root.str("overview") ?: basic.plot,
                    plotLanguage = if (root.str("overview") != null) "tr" else basic.plotLanguage,
                    cast = cast.joinToString(", ").ifBlank { null }, director = director.joinToString(", ").ifBlank { null })
                    .also { file.writeText(json.encodeToString(it)) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { basic }
        }
    }

    private fun request(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().addQueryParameter("api_key", apiKey)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "TMDB HTTP ${r.code}" }
            json.parseToJsonElement(r.body?.string().orEmpty()) as JsonObject
        }
    }

    private val genreMaps = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()
    @Synchronized
    private fun genreNames(kind: String): Map<Int, String> = genreMaps.getOrPut(kind) {
        val file = File(dir, "genres-$kind.json")
        val root = if (file.exists()) json.parseToJsonElement(file.readText()) as JsonObject
        else request("/genre/$kind/list", mapOf("language" to "tr-TR")).also { file.writeText(it.toString()) }
        (root["genres"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { g -> g.str("id")?.toIntOrNull()?.let { id -> g.str("name")?.let { id to it } } }.toMap()
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
        val hit = results.mapNotNull { it as? JsonObject }.firstOrNull { candidate ->
            listOf("title", "name", "original_title", "original_name").any { sameTitle(title, candidate.str(it)) }
        } ?: return null
        fun str(key: String) = (hit[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        val id = str("id")?.toLongOrNull() ?: return null
        val rating = str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }
        val genres = genreNames(kind)
        return TmdbInfo(
            tmdbId = id,
            title = str("title") ?: str("name"),
            plot = str("overview"),
            plotLanguage = if (str("overview") != null) language.take(2) else null,
            poster = str("poster_path")?.let { "$IMAGE_BASE/w500$it" },
            backdrop = str("backdrop_path")?.let { "$IMAGE_BASE/w1280$it" },
            year = (str("release_date") ?: str("first_air_date"))?.take(4),
            rating = rating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            genre = (hit["genre_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull()?.let(genres::get) }.joinToString(", ").ifBlank { null },
        )
    }

    private fun hash(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    }
}

private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
