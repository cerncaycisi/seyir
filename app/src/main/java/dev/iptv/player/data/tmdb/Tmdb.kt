package dev.iptv.player.data.tmdb

import android.content.Context
import dev.iptv.player.BuildConfig
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** ISO 639-1 original language (e.g. "en"); drives the "original audio" track choice. */
    val originalLanguage: String? = null,
    val runtimeMins: Int? = null,
) {
    val isEmpty get() = tmdbId == 0L
}

/**
 * Looks up missing poster / plot / backdrop on TMDB. Disabled (returns null) when no key was baked in via
 * `tmdb.apiKey` in local.properties or the user switched [Setting.TmdbEnabled] off. Results, including
 * misses, are cached on disk so each title is searched once.
 */
data class TmdbResolution(val info: TmdbInfo?, val completed: Boolean)

private class TmdbHttpException(val code: Int) : IllegalStateException("TMDB HTTP $code")

class Tmdb internal constructor(
    private val oldDir: File?,
    private val dir: File,
    private val enabled: suspend () -> Boolean,
    private val apiKey: String = BuildConfig.TMDB_API_KEY,
    private val baseUrl: String = "https://api.themoviedb.org/3",
) {
    constructor(context: Context, settings: SettingsStore, apiKey: String = BuildConfig.TMDB_API_KEY) : this(
        oldDir = File(context.applicationContext.cacheDir, "tmdb-v3"),
        dir = File(context.applicationContext.filesDir, "tmdb-v3"),
        enabled = { settings.get(Setting.TmdbEnabled) },
        apiKey = apiKey,
    )

    init { dir.mkdirs() }

    private val lookupLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val memory = java.util.concurrent.ConcurrentHashMap<String, TmdbInfo>()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = apiKey.isNotBlank()

    suspend fun available(): Boolean = configured && enabled()

    suspend fun genres(type: ContentType): List<String> = withContext(Dispatchers.IO) {
        if (!available()) emptyList() else try { genreNames(if (type == ContentType.SERIES) "tv" else "movie").values.sorted() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun lookup(
        rawTitle: String,
        type: ContentType,
        yearHint: String? = null,
        tmdbId: Long? = null,
        durationSecs: Int? = null,
    ): TmdbInfo? = resolve(rawTitle, type, yearHint, tmdbId, durationSecs).info

    suspend fun resolve(
        rawTitle: String,
        type: ContentType,
        yearHint: String? = null,
        tmdbId: Long? = null,
        durationSecs: Int? = null,
    ): TmdbResolution = withContext(Dispatchers.IO) {
        if (type == ContentType.LIVE || !available()) return@withContext TmdbResolution(null, false)
        if (tmdbId != null && tmdbId > 0) {
            val byId = runCatching { fetchById(tmdbId, type) }
            byId.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val hit = byId.getOrNull()
            val runtimeMismatch = hit != null && type == ContentType.MOVIE && durationSecs != null && durationSecs >= 1800 &&
                !runtimeMatches(durationSecs, hit.runtimeMins)
            if (hit != null && !runtimeMismatch) return@withContext TmdbResolution(hit, true)
            // Network/5xx errors retry later; a missing/404 or runtime-contradicted provider ID falls back to search.
            if (byId.isFailure) return@withContext TmdbResolution(null, false)
        }
        val cleaned = TitleCleaner.clean(rawTitle)
        if (cleaned.title.length < 2) return@withContext TmdbResolution(null, true)
        val year = cleaned.year ?: yearHint?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val useDuration = type == ContentType.MOVIE && durationSecs != null && durationSecs >= 1800
        val cacheKey = if (useDuration) {
            "MOVIE_DUR|${cleaned.title.lowercase()}|$year|${durationSecs!! / 300}"
        } else {
            "$type|${cleaned.title.lowercase()}|$year"
        }
        val file = File(dir, hash(cacheKey) + ".json")
        lookupLocks.getOrPut(file.name) { Mutex() }.withLock {
            memory[file.name]?.let { return@withLock TmdbResolution(it.takeUnless { c -> c.isEmpty }, true) }
            readCache(file)?.let { memory[file.name] = it; return@withLock TmdbResolution(it.takeUnless { c -> c.isEmpty }, true) }
            val result = runCatching {
                if (useDuration) resolveMovieByDuration(cleaned.title, year, durationSecs!!)
                else resolveByTitle(cleaned.title, year, type)
            }
            // Only cache real answers; a network failure should be retried later.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.getOrNull()?.let { runCatching { file.writeText(json.encodeToString(it)) }; memory[file.name] = it; return@withLock TmdbResolution(it, true) }
            if (result.isSuccess) { runCatching { file.writeText(json.encodeToString(TmdbInfo())) }; memory[file.name] = TmdbInfo() }
            TmdbResolution(null, result.isSuccess)
        }
    }

    /**
     * ISO 639-1 original language of a title (e.g. "tr", "en"). Computed from the details endpoint (search hits lack
     * spoken languages) and cached once per title.
     */
    suspend fun originalLanguage(
        rawTitle: String,
        type: ContentType,
        yearHint: String? = null,
        tmdbId: Long? = null,
        durationSecs: Int? = null,
    ): String? {
        val basic = lookup(rawTitle, type, yearHint, tmdbId, durationSecs) ?: return null
        return withContext(Dispatchers.IO) {
            if (basic.originalLanguage != null && File(dir, "id-$type-${basic.tmdbId}.json").exists()) {
                return@withContext basic.originalLanguage
            }
            val file = File(dir, "lang2-$type-${basic.tmdbId}.txt")
            if (file.exists()) return@withContext runCatching { file.readText().trim().ifEmpty { null } }.getOrNull()
            try {
                val kind = if (type == ContentType.SERIES) "tv" else "movie"
                val root = request("/$kind/${basic.tmdbId}")
                val spoken = (root["spoken_languages"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("iso_639_1") }
                pickOriginalLanguage(root.str("original_language"), spoken).also { runCatching { file.writeText(it.orEmpty()) } }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    /**
     * Episode titles of one season from TMDB (episode number → name), Turkish first, English for episodes
     * TMDB only names generically ("Bölüm 3"). Used when the provider gives no real episode names. Cached.
     */
    suspend fun seasonEpisodeNames(
        seriesTitle: String,
        yearHint: String?,
        season: Int,
        tmdbId: Long? = null,
    ): Map<Int, String> {
        val basic = lookup(seriesTitle, ContentType.SERIES, yearHint, tmdbId = tmdbId) ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            val file = File(dir, "season2-${basic.tmdbId}-$season.json")
            readNames(file)?.let { return@withContext it }
            try {
                fun names(lang: String): Map<Int, String> =
                    (request("/tv/${basic.tmdbId}/season/$season", mapOf("language" to lang))["episodes"] as? JsonArray).orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .mapNotNull { e -> e.str("episode_number")?.toIntOrNull()?.let { n -> e.str("name")?.let { n to it } } }
                        .toMap()
                // "Bölüm 3", "3. Bölüm", "Episode 3": placeholders, not titles.
                val generic = Regex("""(?i)^\s*(\d+\.?\s*(bölüm|episode)|(bölüm|episode)\s*\d+)\s*$""")
                val tr = names("tr-TR")
                val en = if (tr.values.any { generic.matches(it) }) names("en-US") else emptyMap()
                val merged = tr.mapValues { (n, t) -> if (generic.matches(t)) en[n]?.takeUnless { generic.matches(it) } ?: t else t }
                    .filterValues { !generic.matches(it) }
                runCatching { file.writeText(json.encodeToString(merged.mapKeys { it.key.toString() })) }
                merged
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        }
    }

    private fun readNames(file: File): Map<Int, String>? = if (!file.exists()) null else
        runCatching { json.decodeFromString<Map<String, String>>(file.readText()).mapKeys { it.key.toInt() } }.getOrNull()

    /** Credits are fetched only for a detail/pause panel, never for every catalog item. */
    suspend fun details(
        rawTitle: String,
        type: ContentType,
        yearHint: String? = null,
        tmdbId: Long? = null,
        durationSecs: Int? = null,
    ): TmdbInfo? {
        val basic = lookup(rawTitle, type, yearHint, tmdbId, durationSecs) ?: return null
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
        val url = "$baseUrl$path".toHttpUrl().newBuilder().addQueryParameter("api_key", apiKey)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw TmdbHttpException(r.code)
            json.parseToJsonElement(r.body?.string().orEmpty()) as JsonObject
        }
    }

    private val genreMaps = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()
    @Synchronized
    private fun genreNames(kind: String): Map<Int, String> = genreMaps.getOrPut(kind) {
        val file = File(dir, "genres-$kind.json")
        migrate(file)
        val root = if (file.exists()) json.parseToJsonElement(file.readText()) as JsonObject
        else request("/genre/$kind/list", mapOf("language" to "tr-TR")).also { file.writeText(it.toString()) }
        (root["genres"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { g -> g.str("id")?.toIntOrNull()?.let { id -> g.str("name")?.let { id to it } } }.toMap()
    }

    private fun migrate(file: File) {
        val old = oldDir ?: return
        if (!file.exists()) {
            val prev = File(old, file.name)
            if (prev.exists()) runCatching { prev.copyTo(file) }
        }
    }

    private fun readCache(file: File): TmdbInfo? {
        migrate(file)
        return if (file.exists()) runCatching { json.decodeFromString<TmdbInfo>(file.readText()) }.getOrNull() else null
    }

    private suspend fun fetchById(tmdbId: Long, type: ContentType): TmdbInfo? {
        val file = File(dir, "id-$type-$tmdbId.json")
        return lookupLocks.getOrPut(file.name) { Mutex() }.withLock {
            memory[file.name]?.let { return@withLock it }
            readCache(file)?.let { memory[file.name] = it; return@withLock it }
            val kind = if (type == ContentType.SERIES) "tv" else "movie"
            val tr = try {
                request("/$kind/$tmdbId", mapOf("language" to "tr-TR"))
            } catch (e: TmdbHttpException) {
                if (e.code == 404) return@withLock null
                throw e
            }
            var title = tr.str("title") ?: tr.str("name")
            var plot = tr.str("overview")
            var plotLang = if (plot != null) "tr" else null
            if (plot == null) {
                val en = runCatching { request("/$kind/$tmdbId", mapOf("language" to "en-US")) }.getOrNull()
                if (en != null) {
                    if (title == null) title = en.str("title") ?: en.str("name")
                    en.str("overview")?.let { plot = it; plotLang = "en" }
                }
            }
            val rating = tr.str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }
            val genre = (tr["genres"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("name") }
                .joinToString(", ").ifBlank { null }
            val spoken = (tr["spoken_languages"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("iso_639_1") }
            val info = TmdbInfo(
                tmdbId = tr.str("id")?.toLongOrNull() ?: tmdbId,
                title = title,
                plot = plot,
                plotLanguage = plotLang,
                poster = tr.str("poster_path")?.let { "$IMAGE_BASE/w500$it" },
                backdrop = tr.str("backdrop_path")?.let { "$IMAGE_BASE/w1280$it" },
                year = (tr.str("release_date") ?: tr.str("first_air_date"))?.take(4),
                rating = rating?.let { String.format(java.util.Locale.US, "%.1f", it) },
                genre = genre,
                originalLanguage = pickOriginalLanguage(tr.str("original_language"), spoken),
                runtimeMins = tr.str("runtime")?.toIntOrNull()?.takeIf { it > 0 },
            )
            runCatching { file.writeText(json.encodeToString(info)) }
            memory[file.name] = info
            info
        }
    }

    private fun resolveByTitle(title: String, year: String?, type: ContentType): TmdbInfo? {
        val tr = search(title, year, type, "tr-TR")
        return if (tr == null) search(title, year, type, "en-US")
        else if (tr.plot == null) {
            val en = search(title, year, type, "en-US")
            tr.copy(plot = en?.plot, plotLanguage = if (en?.plot != null) "en" else null)
        } else tr
    }

    private suspend fun resolveMovieByDuration(title: String, year: String?, durationSecs: Int): TmdbInfo? {
        val withYear = searchCandidates(title, year, ContentType.MOVIE, "tr-TR")
            .ifEmpty { searchCandidates(title, year, ContentType.MOVIE, "en-US") }
            .mapNotNull { it.str("id")?.toLongOrNull() }
        pickBestByDuration(withYear, durationSecs)?.let { return it }
        if (year == null) return null
        val anyYear = searchCandidates(title, null, ContentType.MOVIE, "tr-TR")
            .ifEmpty { searchCandidates(title, null, ContentType.MOVIE, "en-US") }
            .mapNotNull { it.str("id")?.toLongOrNull() }
        return pickBestByDuration(anyYear, durationSecs)
    }

    private suspend fun pickBestByDuration(ids: List<Long>, durationSecs: Int): TmdbInfo? {
        val providerMins = durationSecs / 60.0
        return ids.distinct().take(5)
            .mapNotNull { fetchById(it, ContentType.MOVIE) }
            .filter { runtimeMatches(durationSecs, it.runtimeMins) }
            .minByOrNull { info -> info.runtimeMins?.let { kotlin.math.abs(providerMins - it) } ?: Double.MAX_VALUE }
    }

    private fun searchCandidates(title: String, year: String?, type: ContentType, language: String): List<JsonObject> {
        val kind = if (type == ContentType.SERIES) "tv" else "movie"
        val params = buildMap {
            put("query", title)
            put("language", language)
            put("include_adult", "false")
            year?.let { put(if (kind == "tv") "first_air_date_year" else "year", it) }
        }
        val results = request("/search/$kind", params)["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { it as? JsonObject }.filter { candidate ->
            listOf("title", "name", "original_title", "original_name").any { sameTitle(title, candidate.str(it)) }
        }
    }

    private fun search(title: String, year: String?, type: ContentType, language: String): TmdbInfo? {
        val kind = if (type == ContentType.SERIES) "tv" else "movie"
        val hit = searchCandidates(title, year, type, language).firstOrNull() ?: return null
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
            originalLanguage = str("original_language"),
        )
    }

    private fun hash(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    }
}

private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

/** True when [tmdbRuntimeMins] is close to [providerDurationSecs] (or either side has no runtime). */
internal fun runtimeMatches(providerDurationSecs: Int?, tmdbRuntimeMins: Int?): Boolean {
    if (providerDurationSecs == null || providerDurationSecs <= 0 || tmdbRuntimeMins == null || tmdbRuntimeMins <= 0) return true
    val providerMins = providerDurationSecs / 60.0
    val tolerance = maxOf(12.0, providerMins * 0.12)
    return kotlin.math.abs(providerMins - tmdbRuntimeMins) <= tolerance
}

/**
 * TMDB's `original_language` is sometimes wrong for local productions (a Turkish documentary listed as "en"
 * while its only spoken language is Turkish). Trust it only when it is among the spoken languages.
 */
internal fun pickOriginalLanguage(original: String?, spoken: List<String>): String? =
    if (spoken.isNotEmpty() && original !in spoken) spoken.first() else original ?: spoken.firstOrNull()
