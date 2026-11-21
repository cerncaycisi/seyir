package dev.iptv.player.data

import android.content.Context
import dev.iptv.player.R
import dev.iptv.player.data.db.ContentDb
import dev.iptv.player.data.db.ContentMapper
import dev.iptv.player.data.db.SyncEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import dev.iptv.player.data.tmdb.Tmdb
import dev.iptv.player.data.tmdb.MetadataCatalog
import dev.iptv.player.data.tmdb.MetadataWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A localized message that is safe to display; never contains provider URLs, bodies or credentials. */
internal class ProviderMessage(message: String) : IOException(message)

/**
 * Content access. Layers: in-memory map -> Room (`content.db`, opens instantly) -> network.
 * Cached data is returned immediately; when it is older than [Setting.AutoRefreshHours] it is refreshed in the
 * background and the new data is served by the next [content] call. [refresh] forces the next call to hit the network.
 */
class Repository(context: Context) {
    val appContext: Context = context.applicationContext
    val store = PlaylistStore(context)
    val settings = SettingsStore(context)
    val prefs = ContentPrefs(settings)
    val profiles = ProfileRepository(settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val history = WatchHistory(context, scope)
    val tmdb = Tmdb(context, settings)
    val metadata = MetadataCatalog(context)
    private val metadataStarted = ConcurrentHashMap.newKeySet<String>()
    private val metadataLocks = ConcurrentHashMap<String, Mutex>()

    init {
        // Watch history is per profile: keep its key prefix in step with the active profile.
        scope.launch { ProfileStore.activeIdFlow.collect { history.keyPrefix = ProfileStore.profileScopedKey("", it) } }
        scope.launch {
            settings.flow(Setting.TmdbEnabled).distinctUntilChanged().collect { enabled ->
                if (enabled && tmdb.configured) metadata.dao.pendingCatalogs().forEach { catalog ->
                    MetadataWorker.enqueue(appContext, catalog.playlistId, ContentType.valueOf(catalog.type))
                }
            }
        }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val dao by lazy { ContentDb.get(appContext).dao() }
    private val bulkCodec by lazy { SecretBox.bulk(appContext) }

    private class Cached(val content: Content, val fetchedAt: Long)

    private val memory = ConcurrentHashMap<String, Cached>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    private val forceFresh = ConcurrentHashMap.newKeySet<String>()

    init {
        // Move any plain-text credentials from older versions into encrypted form.
        scope.launch { runCatching { store.migrateCredentials() } }
    }

    suspend fun verify(playlist: Playlist) {
        when (val source = playlist.source) {
            is PlaylistSource.M3u -> if (fetchM3u(source.url).streams.isEmpty()) throw providerMessage(R.string.data_playlist_empty)
            is PlaylistSource.Xtream -> if (!fromXtream(source) { it.login() }) {
                throw providerMessage(R.string.data_login_failed)
            }
        }
    }

    suspend fun content(playlist: Playlist, type: ContentType): Content {
        val key = key(playlist, type)
        memory[key]?.let {
            if (isStale(it.fetchedAt) && key !in forceFresh) refreshInBackground(playlist, type)
            return it.content
        }
        return lockFor(playlist, type).withLock {
            memory[key]?.content ?: loadMissing(playlist, type)
        }
    }

    private suspend fun loadMissing(playlist: Playlist, type: ContentType): Content {
        val key = key(playlist, type)
        val forced = key in forceFresh
        if (!forced) {
            readDisk(playlist, type)?.let { cached ->
                memory[key] = cached
                prepareMetadata(playlist, type, cached.content)
                if (isStale(cached.fetchedAt)) refreshInBackground(playlist, type)
                return cached.content
            }
        }
        try {
            fetchAndStore(playlist, type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A forced refresh that cannot reach the server still falls back to what is on disk.
            val cached = if (forced) readDisk(playlist, type) else null
            if (cached == null) throw e
            memory[key] = cached
            prepareMetadata(playlist, type, cached.content)
            return cached.content
        }
        return memory.getValue(key).content
    }

    /** Fetches every content type of [playlist] from the server and replaces the cached copy (throws on failure). */
    suspend fun reload(playlist: Playlist) {
        when (playlist.source) {
            is PlaylistSource.M3u -> fetchAndStore(playlist, ContentType.LIVE)
            is PlaylistSource.Xtream -> ContentType.entries.forEach { fetchAndStore(playlist, it) }
        }
    }

    suspend fun seriesInfo(playlist: Playlist, seriesId: String): SeriesInfo {
        val source = playlist.source as? PlaylistSource.Xtream
            ?: throw providerMessage(R.string.data_series_unsupported)
        return fromXtream(source) { it.seriesInfo(seriesId) }
    }

    private val vodInfoCache = HashMap<String, VodInfo?>()

    suspend fun vodInfo(playlist: Playlist, stream: Stream): VodInfo? {
        val source = playlist.source as? PlaylistSource.Xtream ?: return null
        val key = "${playlist.id}|${stream.id}"
        if (key in vodInfoCache) return vodInfoCache[key]
        return fromXtream(source) { it.vodInfo(stream.id) }.also { vodInfoCache[key] = it }
    }

    suspend fun shortEpg(playlist: Playlist, stream: Stream): List<EpgEntry> {
        val source = playlist.source as? PlaylistSource.Xtream ?: return emptyList()
        return fromXtream(source) { it.shortEpg(stream.id) }
    }

    /** Xtream account details (status, expiry, connections); null for M3U playlists. Throws on network errors. */
    suspend fun accountInfo(playlist: Playlist): AccountInfo? {
        val source = playlist.source as? PlaylistSource.Xtream ?: return null
        return fromXtream(source) { it.accountInfo() }
    }

    /**
     * The playlist's XMLTV guide URL: `url-tvg` / `x-tvg-url` from an M3U header (first one if several are listed),
     * or the standard `xmltv.php` endpoint for Xtream. Null when the playlist advertises none.
     */
    suspend fun epgUrl(playlist: Playlist): String? = when (val source = playlist.source) {
        is PlaylistSource.Xtream -> XtreamApi(source, http).xmltvUrl()
        is PlaylistSource.M3u -> {
            runCatching { content(playlist, ContentType.LIVE) }
            withContext(Dispatchers.IO) { runCatching { dao.epgUrl(playlist.id)?.let(bulkCodec::decrypt) }.getOrNull() }
        }
    }

    /** Marks cached data as outdated: the next [content] call refetches (and falls back to disk if offline). */
    suspend fun refresh(playlistId: String) {
        ContentType.entries.forEach {
            val key = "$playlistId|$it"
            memory.remove(key)
            forceFresh += key
        }
        vodInfoCache.keys.removeAll { it.startsWith("$playlistId|") }
    }

    /** Deletes the playlist and everything cached for it. */
    suspend fun removePlaylist(playlistId: String) {
        MetadataWorker.cancel(appContext, playlistId)
        store.remove(playlistId)
        runCatching { history.removePlaylist(playlistId) }
        ContentType.entries.forEach {
            val key = "$playlistId|$it"
            metadataLocks.getOrPut(key) { Mutex() }.withLock {
                memory.remove(key)
                forceFresh -= key
                metadataStarted -= key
            }
        }
        metadata.dao.deletePlaylist(playlistId)
        withContext(Dispatchers.IO) { runCatching { dao.clear(playlistId) } }
    }

    // ---- loading -------------------------------------------------------------------------------------

    private fun key(playlist: Playlist, type: ContentType) = "${playlist.id}|$type"

    // M3U serves every type from one download, so its first-load lock is per playlist.
    private fun lockFor(playlist: Playlist, type: ContentType): Mutex {
        val lockKey = if (playlist.source is PlaylistSource.M3u) playlist.id else key(playlist, type)
        return locks.getOrPut(lockKey) { Mutex() }
    }

    private suspend fun isStale(fetchedAt: Long): Boolean {
        val hours = settings.get(Setting.AutoRefreshHours)
        return hours > 0 && System.currentTimeMillis() - fetchedAt > hours * 3_600_000L
    }

    private fun refreshInBackground(playlist: Playlist, type: ContentType) {
        val id = if (playlist.source is PlaylistSource.M3u) playlist.id else key(playlist, type)
        if (!refreshing.add(id)) return
        scope.launch {
            try {
                fetchAndStore(playlist, type)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or provider down: keep serving the cached copy.
            } finally {
                refreshing.remove(id)
            }
        }
    }

    private suspend fun readDisk(playlist: Playlist, type: ContentType): Cached? = withContext(Dispatchers.IO) {
        try {
            val sync = dao.sync(playlist.id, type.name) ?: return@withContext null
            val mapper = ContentMapper(playlist.source, bulkCodec)
            val categories = dao.categories(playlist.id, type.name).map(mapper::toCategory)
            val items = dao.streams(playlist.id, type.name).map(mapper::toStream)
            Cached(Content(categories, items), sync.fetchedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // unreadable cache (e.g. lost encryption key) behaves like an empty one
        }
    }

    private suspend fun fetchAndStore(playlist: Playlist, type: ContentType) {
        when (val source = playlist.source) {
            is PlaylistSource.M3u -> {
                val doc = fetchM3u(source.url)
                val now = System.currentTimeMillis()
                ContentType.entries.forEach { t ->
                    val items = doc.streams.filter { it.type == t }
                    val categories = items.map { it.categoryId }.distinct().map { Category(it, it) }
                    store(playlist, t, Content(categories, items), now, doc.epgUrl)
                }
            }
            is PlaylistSource.Xtream -> {
                val content = fromXtream(source) { api ->
                    coroutineScope {
                        val categories = async { api.categories(type) }
                        val streams = async { api.streams(type) }
                        Content(categories.await(), streams.await())
                    }
                }
                store(playlist, type, content, System.currentTimeMillis(), null)
            }
        }
    }

    private suspend fun store(playlist: Playlist, type: ContentType, content: Content, now: Long, epgUrl: String?) {
        withContext(Dispatchers.IO) {
            runCatching {
                val mapper = ContentMapper(playlist.source, bulkCodec)
                dao.replace(
                    playlist.id,
                    type.name,
                    content.categories.mapIndexed { i, c -> mapper.toCategory(playlist.id, type, c, i) },
                    content.items.mapIndexed { i, s -> mapper.toEntity(playlist.id, s, i) },
                    SyncEntity(playlist.id, type.name, now, epgUrl?.let(bulkCodec::encrypt)),
                )
            }.onFailure { if (it is CancellationException) throw it } // a failing disk cache must not break playback
        }
        val key = key(playlist, type)
        memory[key] = Cached(content, now)
        forceFresh -= key
        prepareMetadata(playlist, type, content, refreshed = true)
    }

    /** Reconcile once after disk load or refresh, entirely off the foreground playlist path. */
    private fun prepareMetadata(playlist: Playlist, type: ContentType, content: Content, refreshed: Boolean = false) {
        if (type == ContentType.LIVE) return
        val id = key(playlist, type)
        if (!metadataStarted.add(id) && !refreshed) return
        scope.launch {
            try {
                metadataLocks.getOrPut(id) { Mutex() }.withLock {
                    if (memory[id]?.content !== content || store.playlists.first().none { it.id == playlist.id }) return@withLock
                    metadata.reconcile(playlist, type, content)
                    if (tmdb.available() && metadata.dao.pending(playlist.id, type.name, 1).isNotEmpty()) {
                        MetadataWorker.enqueue(appContext, playlist.id, type)
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { metadataStarted -= id }
        }
    }

    private suspend fun providerMessage(resource: Int, vararg args: Any): ProviderMessage =
        ProviderMessage(settings.localizedContext(appContext).getString(resource, *args))

    private suspend fun <T> providerRequest(request: suspend () -> T): T = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProviderMessage) {
        throw e
    } catch (e: XtreamFailure.Http) {
        throw providerMessage(R.string.data_server_http, e.status)
    } catch (_: XtreamFailure.SeriesMissing) {
        throw providerMessage(R.string.data_series_missing)
    } catch (_: SerializationException) {
        throw providerMessage(R.string.data_server_invalid)
    } catch (_: IllegalArgumentException) {
        throw providerMessage(R.string.data_url_invalid)
    } catch (_: IOException) {
        throw providerMessage(R.string.data_server_unreachable)
    }

    private suspend fun <T> fromXtream(source: PlaylistSource.Xtream, request: suspend (XtreamApi) -> T): T = providerRequest {
        val localized = settings.localizedContext(appContext)
        request(XtreamApi(source, http) { number -> localized.getString(R.string.data_episode, number) })
    }

    private suspend fun fetchM3u(url: String): M3uDocument = providerRequest {
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url.trim()).build()).execute().use { response ->
                if (!response.isSuccessful) throw providerMessage(R.string.data_playlist_http, response.code)
                M3uParser.parseDocument(response.body?.string().orEmpty())
            }
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
    if (tmdb.available()) {
        tmdb.details(stream.name, ContentType.MOVIE, info.year)?.let { t ->
            info = info.copy(
                plot = t.plot ?: info.plot,
                plotLanguage = if (t.plot != null) t.plotLanguage else info.plotLanguage,
                backdrop = info.backdrop ?: t.backdrop,
                poster = info.poster ?: t.poster,
                year = info.year ?: t.year,
                rating = t.rating ?: info.rating,
                genre = t.genre ?: info.genre,
                cast = t.cast ?: info.cast,
                director = t.director ?: info.director,
                fromTmdb = true,
            )
        }
    }
    return info
}

/** Series info with missing plot / cover / backdrop filled from TMDB. */
suspend fun Repository.seriesDetails(playlist: Playlist, seriesId: String): SeriesInfo {
    val info = seriesInfo(playlist, seriesId)
    val t = tmdb.details(info.name, ContentType.SERIES, info.year) ?: return info
    return info.copy(
        plot = t.plot ?: info.plot,
        plotLanguage = if (t.plot != null) t.plotLanguage else info.plotLanguage,
        backdrop = info.backdrop ?: t.backdrop,
        cover = info.cover ?: t.poster,
        year = info.year ?: t.year,
        rating = t.rating ?: info.rating,
        genre = t.genre ?: info.genre,
        cast = t.cast ?: info.cast,
        director = t.director ?: info.director,
    )
}
