package dev.iptv.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.iptv.player.data.tmdb.Tmdb
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class Repository(context: Context) {
    val store = PlaylistStore(context)
    val settings = SettingsStore(context)
    val prefs = ContentPrefs(settings)
    val profiles = ProfileRepository(settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val history = WatchHistory(context, scope)
    val tmdb = Tmdb(context, settings)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val lock = Mutex()
    private val m3uCache = HashMap<String, List<Stream>>()
    private val contentCache = HashMap<String, Content>()

    suspend fun verify(playlist: Playlist) {
        when (val source = playlist.source) {
            is PlaylistSource.M3u -> if (fetchM3u(source.url).isEmpty()) throw IOException("Listede içerik bulunamadı")
            is PlaylistSource.Xtream -> if (!XtreamApi(source, http).login()) {
                throw IOException("Giriş başarısız: kullanıcı adı veya şifre hatalı")
            }
        }
    }

    suspend fun content(playlist: Playlist, type: ContentType): Content = lock.withLock {
        contentCache.getOrPut("${playlist.id}|$type") { load(playlist, type) }
    }

    suspend fun seriesInfo(playlist: Playlist, seriesId: String): SeriesInfo {
        val source = playlist.source as? PlaylistSource.Xtream
            ?: throw IOException("Bu playlist türü dizi detayını desteklemiyor")
        return XtreamApi(source, http).seriesInfo(seriesId)
    }

    private val vodInfoCache = HashMap<String, VodInfo?>()

    suspend fun vodInfo(playlist: Playlist, stream: Stream): VodInfo? {
        val source = playlist.source as? PlaylistSource.Xtream ?: return null
        val key = "${playlist.id}|${stream.id}"
        if (key in vodInfoCache) return vodInfoCache[key]
        return XtreamApi(source, http).vodInfo(stream.id).also { vodInfoCache[key] = it }
    }

    suspend fun shortEpg(playlist: Playlist, stream: Stream): List<EpgEntry> {
        val source = playlist.source as? PlaylistSource.Xtream ?: return emptyList()
        return XtreamApi(source, http).shortEpg(stream.id)
    }

    suspend fun refresh(playlistId: String) = lock.withLock {
        m3uCache.remove(playlistId)
        contentCache.keys.removeAll { it.startsWith("$playlistId|") }
    }

    private suspend fun load(playlist: Playlist, type: ContentType): Content = when (val source = playlist.source) {
        is PlaylistSource.M3u -> {
            val items = m3uCache.getOrPut(playlist.id) { fetchM3u(source.url) }.filter { it.type == type }
            Content(items.map { it.categoryId }.distinct().map { Category(it, it) }, items)
        }
        is PlaylistSource.Xtream -> coroutineScope {
            val api = XtreamApi(source, http)
            val categories = async { api.categories(type) }
            val streams = async { api.streams(type) }
            Content(categories.await(), streams.await())
        }
    }

    private suspend fun fetchM3u(url: String): List<Stream> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url.trim()).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Liste indirilemedi: HTTP ${response.code}")
            M3uParser.parse(response.body?.string().orEmpty())
        }
    }
}

/**
 * Everything the movie detail screen shows: the panel's own info with gaps (poster, plot, backdrop, year, rating)
 * filled from TMDB. Never null so M3U playlists still get a page.
 */
suspend fun Repository.movieDetails(playlist: Playlist, stream: Stream): VodInfo {
    val panel = runCatching { vodInfo(playlist, stream) }.getOrNull()
    var info = VodInfo(
        plot = panel?.plot ?: stream.plot,
        backdrop = panel?.backdrop ?: stream.backdrop,
        genre = panel?.genre ?: stream.genre,
        duration = panel?.duration,
        year = panel?.year ?: stream.year,
        rating = panel?.rating ?: stream.rating,
        cast = panel?.cast,
        director = panel?.director,
        trailer = panel?.trailer,
        poster = panel?.poster ?: stream.logo,
        durationSecs = panel?.durationSecs,
    )
    if (info.plot == null || info.backdrop == null || info.poster == null) {
        tmdb.lookup(stream.name, ContentType.MOVIE, info.year)?.let { t ->
            info = info.copy(
                plot = info.plot ?: t.plot,
                backdrop = info.backdrop ?: t.backdrop,
                poster = info.poster ?: t.poster,
                year = info.year ?: t.year,
                rating = info.rating ?: t.rating,
                fromTmdb = true,
            )
        }
    }
    return info
}

/** Series info with missing plot / cover / backdrop filled from TMDB. */
suspend fun Repository.seriesDetails(playlist: Playlist, seriesId: String): SeriesInfo {
    val info = seriesInfo(playlist, seriesId)
    if (info.plot != null && info.backdrop != null && info.cover != null) return info
    val t = tmdb.lookup(info.name, ContentType.SERIES, info.year) ?: return info
    return info.copy(
        plot = info.plot ?: t.plot,
        backdrop = info.backdrop ?: t.backdrop,
        cover = info.cover ?: t.poster,
        year = info.year ?: t.year,
        rating = info.rating ?: t.rating,
    )
}
