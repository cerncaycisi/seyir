package dev.iptv.player.player

import dev.iptv.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SubtitleHit(val fileId: Long, val release: String, val language: String, val downloads: Int)

/** What the settings panel needs to search and attach online subtitles for the current item. */
interface SubtitleSearch {
    suspend fun search(): List<SubtitleHit>
    suspend fun download(hit: SubtitleHit): Boolean
}

/** Minimal OpenSubtitles REST API v1 client (https://opensubtitles.stoplight.io). */
object OpenSubtitles {
    private const val BASE = "https://api.opensubtitles.com/api/v1"
    // Their terms require an identifiable "AppName vX.Y" user agent.
    private val userAgent = "IPTVPlayer v${BuildConfig.VERSION_NAME}"
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val enabled: Boolean get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

    @Volatile private var token: String? = null
    @Volatile private var apiHost: String? = null

    private fun request(url: String) = Request.Builder().url(url)
        .header("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
        .header("User-Agent", userAgent)
        .header("Accept", "application/json")

    private fun execute(req: Request): JsonObject = http.newCall(req).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw IOException("OpenSubtitles HTTP ${r.code}")
        json.parseToJsonElement(body).jsonObject
    }

    suspend fun search(title: String, year: String?, languages: List<String>): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val (query, parsedYear) = cleanSubtitleQuery(title)
        // The API wants lower-case, alphabetically ordered parameters.
        val url = "$BASE/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("languages", languages.joinToString(",").lowercase())
            .addQueryParameter("query", query.lowercase())
            .apply { (year?.takeIf { it.length == 4 } ?: parsedYear)?.let { addQueryParameter("year", it) } }
            .build()
        val data = execute(request(url.toString()).get().build())["data"] as? JsonArray ?: return@withContext emptyList()
        parseHits(data)
    }

    /**
     * Exact search by TMDB id: a movie by `tmdb_id`, an episode by its show's `parent_tmdb_id` + season + episode.
     * Far more precise than a title query (episode names like "Show · S1B2 · Title" found nothing useful).
     */
    suspend fun searchByTmdb(tmdbId: Long, season: Int, episode: Int, languages: List<String>): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val episodeSearch = season > 0 && episode > 0
        // The API wants lower-case, alphabetically ordered parameters.
        val url = "$BASE/subtitles".toHttpUrl().newBuilder()
            .apply { if (episodeSearch) addQueryParameter("episode_number", episode.toString()) }
            .addQueryParameter("languages", languages.joinToString(",").lowercase())
            .apply {
                if (episodeSearch) {
                    addQueryParameter("parent_tmdb_id", tmdbId.toString())
                    addQueryParameter("season_number", season.toString())
                } else {
                    addQueryParameter("tmdb_id", tmdbId.toString())
                }
            }
            .build()
        val data = execute(request(url.toString()).get().build())["data"] as? JsonArray ?: return@withContext emptyList()
        parseHits(data)
    }

    internal fun parseHits(data: JsonArray): List<SubtitleHit> = data.mapNotNull { el ->
        val attrs = (el as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
        val fileId = (attrs["files"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.get("file_id") }
            ?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val lang = attrs["language"]?.jsonPrimitive?.contentOrNull ?: "?"
        val release = attrs["release"]?.jsonPrimitive?.contentOrNull
            ?: (attrs["feature_details"] as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        SubtitleHit(fileId, release, lang, attrs["download_count"]?.jsonPrimitive?.intOrNull ?: 0)
    }.sortedByDescending { it.downloads }.take(30)

    /** Optional login: downloads are rate-limited harder when anonymous. Credentials come from local.properties. */
    private fun ensureLogin() {
        if (token != null || BuildConfig.OPENSUBTITLES_USERNAME.isBlank()) return
        val body = buildJsonObject {
            put("username", BuildConfig.OPENSUBTITLES_USERNAME)
            put("password", BuildConfig.OPENSUBTITLES_PASSWORD)
        }.toString().toRequestBody(jsonType)
        val res = execute(request("$BASE/login").post(body).build())
        token = res["token"]?.jsonPrimitive?.contentOrNull
        apiHost = (res["base_url"] as? JsonElement)?.jsonPrimitive?.contentOrNull
    }

    /** Downloads the subtitle as SRT into [cacheDir]/subs and returns the file. */
    suspend fun download(cacheDir: File, hit: SubtitleHit): File = withContext(Dispatchers.IO) {
        runCatching { ensureLogin() }
        val body = buildJsonObject {
            put("file_id", hit.fileId)
            put("sub_format", "srt")
        }.toString().toRequestBody(jsonType)
        val base = apiHost?.let { "https://$it/api/v1" } ?: BASE
        val req = request("$base/download").post(body).header("Content-Type", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }.build()
        val link = execute(req)["link"]?.jsonPrimitive?.contentOrNull ?: throw IOException("İndirme bağlantısı yok")
        val file = File(cacheDir, "subs").apply { mkdirs() }.resolve("${hit.fileId}.srt")
        http.newCall(Request.Builder().url(link).header("User-Agent", userAgent).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("İndirilemedi: HTTP ${r.code}")
            file.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        file
    }
}

private val bracketed = Regex("""[\[(][^\])]*[\])]""")
private val prefix = Regex("""^\s*(\|[A-Za-z]{2,4}\||[A-Za-z]{2,4}\s*[:|])\s*""")
private val tags = Regex(
    """\b(2160p|1080p|1080i|720p|480p|4k|uhd|fhd|hd|hdr|web-?dl|webrip|bluray|brrip|x26[45]|h26[45]|hevc|multi|dual|dublaj|altyaz[iı]l[iı])\b""",
    RegexOption.IGNORE_CASE,
)
private val trailingYear = Regex("""\s+((?:19|20)\d{2})\s*$""")
private val episodeTag = Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE)

/** Turns an IPTV title ("TR: Film Adı (2019) 1080p") into a search query and an optional year. */
fun cleanSubtitleQuery(name: String): Pair<String, String?> {
    var s = name.replace('.', ' ').replace('_', ' ')
    s = bracketed.replace(s, " ")
    s = prefix.replace(s, "")
    s = tags.replace(s, " ")
    s = episodeTag.replace(s, " ")
    s = s.replace(Regex("\\s+"), " ").trim(' ', '-', '|')
    val year = trailingYear.find(s)?.groupValues?.get(1)
    if (year != null) s = s.removeSuffix(year).trim()
    if (year == null) {
        val inParens = Regex("""[\[(]((?:19|20)\d{2})[\])]""").find(name)?.groupValues?.get(1)
        return s to inParens
    }
    return s to year
}
