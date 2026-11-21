package dev.iptv.player

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.QueueCodec
import dev.iptv.player.data.RefreshWorker
import dev.iptv.player.data.Repository
import dev.iptv.player.data.SecretBox
import dev.iptv.player.data.Setting
import dev.iptv.player.data.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class App : Application() {
    val releasePrefs by lazy { dev.iptv.player.data.ReleasePrefs(this) }
    val repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        dev.iptv.player.util.CrashReporter.install(this)
        dev.iptv.player.epg.EpgScheduler.schedule(this)
        // Keep the background refresh in step with the "auto refresh hours" setting (0 disables it).
        repository.scope.launch {
            repository.settings.flow(Setting.AutoRefreshHours).distinctUntilChanged().collect {
                runCatching { RefreshWorker.schedule(this@App, it) }
            }
        }
    }
}

object PlayerQueue {
    var playlist: Playlist? = null
    var items: List<Stream> = emptyList()
    var index = 0

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileLock = Any()
    private val saveGeneration = AtomicLong()
    private const val FILE = "player_queue.bin"

    /** Persists the current queue (encrypted) so [restore] can rebuild it after process death. Call after changing it. */
    fun save(context: Context) {
        val generation = saveGeneration.incrementAndGet()
        val playlist = playlist
        val items = items.toList()
        val index = index
        val app = context.applicationContext
        io.launch {
            runCatching {
                if (generation != saveGeneration.get()) return@runCatching
                val encoded = if (playlist == null || items.isEmpty()) null
                    else QueueCodec.encode(playlist, items, index, SecretBox.bulk(app)).toByteArray(Charsets.UTF_8)
                synchronized(fileLock) {
                    // Encoding/jobs may finish out of order during rapid channel changes.
                    if (generation != saveGeneration.get()) return@synchronized
                    val file = AtomicFile(File(app.filesDir, FILE))
                    if (encoded == null) {
                        file.delete()
                    } else {
                        val output = file.startWrite()
                        try {
                            output.write(encoded)
                            file.finishWrite(output)
                        } catch (e: Exception) {
                            file.failWrite(output)
                            throw e
                        }
                    }
                }
            }
        }
    }

    /** Rebuilds the queue from disk when the in-memory one is empty (process was killed). Returns true if a queue is available. */
    fun restore(context: Context): Boolean {
        if (items.isNotEmpty() && playlist != null) return true
        val app = context.applicationContext
        val decoded = runCatching {
            val text = synchronized(fileLock) {
                // AtomicFile restores its backup after an interrupted write; a partial snapshot is never decoded.
                AtomicFile(File(app.filesDir, FILE)).openRead().bufferedReader().use { it.readText() }
            }
            QueueCodec.decode(text, SecretBox.bulk(app))
        }.getOrNull() ?: return false
        playlist = decoded.first
        items = decoded.second
        index = decoded.third
        return true
    }
}
