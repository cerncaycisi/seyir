package dev.iptv.player.epg

import android.content.Context
import android.util.LruCache
import android.util.Xml
import androidx.room.Room
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore
import dev.iptv.player.data.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object EpgSources {
    /** EpgUrlOverride if set, else the playlist's own guide. */
    fun url(playlist: Playlist, override: String): String? {
        override.trim().takeIf { it.isNotEmpty() }?.let { return it }
        return when (val s = playlist.source) {
            is PlaylistSource.Xtream -> {
                val base = s.server.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it" }
                "$base/xmltv.php?username=${URLEncoder.encode(s.username.trim(), "UTF-8")}&password=${URLEncoder.encode(s.password.trim(), "UTF-8")}"
            }
            is PlaylistSource.M3u -> m3uTvgUrl(playlist)
        }
    }

    /**
     * HOOK (M3U guide): another engineer adds `Repository.epgUrl(playlist)` that reads `url-tvg` from the
     * #EXTM3U header. Once merged, return that here instead of null.
     */
    fun m3uTvgUrl(playlist: Playlist): String? = null // TODO(merge): Repository.epgUrl(playlist)
}

/**
 * XMLTV store + query layer (own Room database `epg.db`). Programme times are stored as in the feed;
 * [EpgOffsetMinutes][dev.iptv.player.data.Setting.EpgOffsetMinutes] is applied when programmes are read.
 */
class EpgService private constructor(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    private val db = Room.databaseBuilder(appContext, EpgDatabase::class.java, "epg.db").fallbackToDestructiveMigration().build()
    private val dao = db.epg()
    val reminderDao = db.reminders()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val status = MutableStateFlow<EpgStatus>(EpgStatus.Idle)

    /** Bumped whenever stored data, matches or the offset change, so UI can reload. */
    val generation = MutableStateFlow(0)

    private val ingestLock = Mutex()
    private val lastAttempt = HashMap<String, Long>()
    @Volatile private var index: ChannelIndex? = null
    @Volatile private var overrideCache: Map<String, String>? = null
    private val matchMemo = ConcurrentHashMap<String, String>()
    private val cache = LruCache<String, List<Programme>>(80)
    @Volatile private var offsetSec = 0L

    val reminderIds: Flow<Set<String>> = reminderDao.observeIds().map { it.toSet() }

    init {
        scope.launch {
            var first = true
            settings.flow(Setting.EpgOffsetMinutes).collect { minutes ->
                offsetSec = minutes * 60L
                if (!first) {
                    cache.evictAll()
                    generation.value++
                }
                first = false
            }
        }
        scope.launch {
            runCatching {
                reminderDao.deleteBefore(nowSec() - 3600)
                ReminderScheduler.rescheduleAll(appContext, reminderDao.all())
            }
        }
    }

    // region ingestion

    /** Refresh in the background when the data is missing, stale (12h) or came from a different source. */
    fun requestFresh(playlist: Playlist) {
        scope.launch {
            val url = EpgSources.url(playlist, settings.get(Setting.EpgUrlOverride)) ?: return@launch
            val now = nowSec()
            val stale = dao.meta("source") != url || now - (dao.meta("updated")?.toLongOrNull() ?: 0L) > 12 * 3600
            val last = lastAttempt[url]
            if (stale && (last == null || now - last > 600)) {
                lastAttempt[url] = now
                refresh(playlist)
            }
        }
    }

    /** Downloads and replaces the stored guide. Returns false if it failed or another refresh was running. */
    suspend fun refresh(playlist: Playlist): Boolean {
        if (!ingestLock.tryLock()) return false
        try {
            val url = EpgSources.url(playlist, settings.get(Setting.EpgUrlOverride))
            if (url == null) {
                status.value = EpgStatus.Error("Bu playlist için EPG adresi yok. Ayarlardan bir XMLTV adresi girin.")
                return false
            }
            status.value = EpgStatus.Refreshing(0)
            withContext(Dispatchers.IO) { ingest(url) }
            index = null
            matchMemo.clear()
            cache.evictAll()
            generation.value++
            status.value = EpgStatus.Idle
            return true
        } catch (e: CancellationException) {
            status.value = EpgStatus.Idle
            throw e
        } catch (e: Exception) {
            status.value = EpgStatus.Error(e.message ?: e.javaClass.simpleName)
            return false
        } finally {
            ingestLock.unlock()
        }
    }

    private suspend fun ingest(url: String) {
        val job: Job? = currentCoroutineContext()[Job]
        val request = Request.Builder().url(url).header("User-Agent", "IPTVPlayer/0.1").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("EPG indirilemedi: HTTP ${response.code}")
            val body = response.body ?: throw IOException("EPG yanıtı boş")
            val input = maybeGunzip(body.byteStream())
            val now = nowSec()
            val lo = now - WINDOW_PAST_DAYS * 86400
            val hi = now + WINDOW_FUTURE_DAYS * 86400
            val parser = Xml.newPullParser()
            // One transaction: readers keep seeing the old guide (WAL) and a parse failure rolls everything back.
            db.runInTransaction {
                dao.clearProgrammes()
                dao.clearChannels()
                val batch = ArrayList<Programme>(BATCH)
                val channels = ArrayList<EpgChannel>()
                var count = 0
                XmltvParser.parse(
                    parser, input,
                    onChannel = { c -> channels += EpgChannel(c.id, c.names.firstOrNull() ?: c.id, c.names.joinToString("\n")) },
                    onProgramme = { p ->
                        job?.ensureActive()
                        if (p.stop > lo && p.start < hi) {
                            batch += p
                            count++
                            if (batch.size >= BATCH) {
                                dao.insertProgrammes(ArrayList(batch))
                                batch.clear()
                                status.value = EpgStatus.Refreshing(count)
                            }
                        }
                    },
                )
                if (batch.isNotEmpty()) dao.insertProgrammes(batch)
                channels.chunked(500).forEach { dao.insertChannels(it) }
                if (count == 0 && channels.isEmpty()) throw IOException("EPG dosyası okunamadı veya boş")
                dao.putMeta(EpgMeta("source", url))
                dao.putMeta(EpgMeta("updated", now.toString()))
            }
        }
    }

    private fun maybeGunzip(raw: InputStream): InputStream {
        val buffered = BufferedInputStream(raw, 64 * 1024)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        return if (b0 == 0x1f && b1 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }

    suspend fun lastUpdated(): Long? = dao.meta("updated")?.toLongOrNull()

    // endregion

    // region matching

    private suspend fun channelIndex(): ChannelIndex = index ?: ChannelIndex(dao.channels()).also { index = it }

    private suspend fun overrides(): Map<String, String> = overrideCache ?: settings.get(Setting.EpgChannelOverrides)
        .mapNotNull { e -> e.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap().also { overrideCache = it }

    /** XMLTV channel id for [stream]: manual override, else tvg-id, else normalized name. */
    suspend fun resolve(playlistId: String, stream: Stream): String? {
        val key = "$playlistId|${stream.id}"
        matchMemo[key]?.let { return it.ifEmpty { null } }
        val result = overrides()[key] ?: channelIndex().match(stream.epgId, stream.name)
        matchMemo[key] = result.orEmpty()
        return result
    }

    suspend fun currentMatch(playlistId: String, stream: Stream): EpgChannel? {
        val id = resolve(playlistId, stream) ?: return null
        return channelIndex().channels.firstOrNull { it.id == id }
    }

    suspend fun isManual(playlistId: String, stream: Stream): Boolean = "$playlistId|${stream.id}" in overrides()

    suspend fun searchChannels(query: String): List<EpgChannel> = channelIndex().search(query)

    /** Pass null to go back to automatic matching. */
    suspend fun setOverride(playlistId: String, streamId: String, channelId: String?) {
        val key = "$playlistId|$streamId"
        val current = settings.get(Setting.EpgChannelOverrides).filterNot { it.startsWith("$key\t") }.toMutableSet()
        if (channelId != null) current += "$key\t$channelId"
        settings.set(Setting.EpgChannelOverrides, current)
        overrideCache = null
        matchMemo.clear()
        generation.value++
    }

    // endregion

    // region queries (all times shifted by the user's EPG offset)

    private fun shifted(p: Programme) = if (offsetSec == 0L) p else p.copy(start = p.start + offsetSec, stop = p.stop + offsetSec)

    /** All stored programmes for an XMLTV channel (offset applied), sorted by start. Cached. */
    suspend fun programmes(channelId: String): List<Programme> {
        cache.get(channelId)?.let { return it }
        val list = dao.range(channelId, Long.MIN_VALUE / 2, Long.MAX_VALUE / 2).map(::shifted)
        cache.put(channelId, list)
        return list
    }

    suspend fun programmes(playlistId: String, stream: Stream): List<Programme> =
        resolve(playlistId, stream)?.let { programmes(it) }.orEmpty()

    suspend fun nowPlaying(playlistId: String, stream: Stream, nowSec: Long = nowSec()): Programme? {
        val id = resolve(playlistId, stream) ?: return null
        return dao.at(id, nowSec - offsetSec)?.let(::shifted)
    }

    /** The programme airing now followed by the next ones, [count] in total. */
    suspend fun nowAndNext(playlistId: String, stream: Stream, count: Int = 2, nowSec: Long = nowSec()): List<Programme> {
        val id = resolve(playlistId, stream) ?: return emptyList()
        return dao.range(id, nowSec - offsetSec, nowSec - offsetSec + 12 * 3600).map(::shifted).filter { it.stop > nowSec }.take(count)
    }

    /** Finished programmes inside the archive window, newest first. */
    suspend fun past(playlistId: String, stream: Stream, days: Int, nowSec: Long = nowSec()): List<Programme> =
        programmes(playlistId, stream).filter { it.stop <= nowSec && it.start >= nowSec - days * 86400L }.sortedByDescending { it.start }

    // endregion

    // region reminders

    /** Toggles a reminder; returns true when it is now set. */
    suspend fun toggleReminder(playlist: Playlist, stream: Stream, p: Programme): Boolean {
        val id = Reminder.idOf(playlist.id, stream.id, p.start)
        if (reminderDao.get(id) != null) {
            reminderDao.delete(id)
            ReminderScheduler.cancel(appContext, id)
            return false
        }
        val r = Reminder(id, playlist.id, stream.id, stream.name, p.title, p.start, p.stop)
        reminderDao.put(r)
        ReminderScheduler.schedule(appContext, r)
        return true
    }

    // endregion

    companion object {
        const val WINDOW_PAST_DAYS = 2L
        const val WINDOW_FUTURE_DAYS = 7L
        private const val BATCH = 1000

        @Volatile private var instance: EpgService? = null

        fun get(context: Context): EpgService = instance ?: synchronized(this) {
            instance ?: EpgService(context).also { instance = it }
        }

        fun nowSec(): Long = System.currentTimeMillis() / 1000
    }
}
