package dev.iptv.player

import android.app.Application
import android.content.Context
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

class App : Application() {
    val releasePrefs by lazy { dev.iptv.player.data.ReleasePrefs(this) }

    val repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        dev.iptv.player.util.CrashReporter.install(this)
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
    private const val FILE = "player_queue.bin"

    /** Persists the current queue (encrypted) so [restore] can rebuild it after process death. Call after changing it. */
    fun save(context: Context) {
        val playlist = playlist
        val items = items
        val index = index
        val app = context.applicationContext
        io.launch {
            runCatching {
                val file = File(app.filesDir, FILE)
                if (playlist == null || items.isEmpty()) {
                    file.delete()
                } else {
                    file.writeText(QueueCodec.encode(playlist, items, index, SecretBox.bulk(app)))
                }
            }
        }
    }

    /** Rebuilds the queue from disk when the in-memory one is empty (process was killed). Returns true if a queue is available. */
    fun restore(context: Context): Boolean {
        if (items.isNotEmpty() && playlist != null) return true
        val app = context.applicationContext
        val decoded = runCatching {
            val file = File(app.filesDir, FILE)
            if (file.exists()) QueueCodec.decode(file.readText(), SecretBox.bulk(app)) else null
        }.getOrNull() ?: return false
        playlist = decoded.first
        items = decoded.second
        index = decoded.third
        return true
    }
}
