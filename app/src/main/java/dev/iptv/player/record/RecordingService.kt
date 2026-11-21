package dev.iptv.player.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.iptv.player.App
import dev.iptv.player.R
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Setting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object RecordingStorage {
    /** App-specific external movies dir (no permission needed, removed on uninstall); internal storage as fallback. */
    fun dir(context: Context): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, "Seyir") }
        if (external != null && (external.isDirectory || external.mkdirs())) return external
        return File(context.filesDir, "recordings").apply { mkdirs() }
    }

    fun freeBytes(dir: File): Long = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
}

/**
 * Foreground service (type dataSync: it moves bytes from the network to disk) that owns every running recording.
 * Started with ACTION_START + EXTRA_ID by the UI or the alarm receiver, stopped from the notification's "Durdur" action.
 */
class RecordingService : Service() {
    private class Active(@Volatile var recorder: StreamRecorder? = null) {
        @Volatile var stop = false
        @Volatile var bytes = 0L
        @Volatile var reason: String? = null
        @Volatile var name = ""
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, Active>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService, even if there turns out to be nothing to do.
        enterForeground()
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_START -> if (id != null && active.putIfAbsent(id, Active()) == null) {
                val job = active.getValue(id)
                scope.launch { record(id, job) }
            }
            ACTION_STOP -> if (id != null) stopOne(id) else active.keys.forEach(::stopOne)
        }
        if (active.isEmpty()) finishIfIdle()
        return START_NOT_STICKY
    }

    // Android 15 limits dataSync services to ~6h per day; end recordings cleanly instead of being killed.
    override fun onTimeout(startId: Int, fgsType: Int) {
        active.keys.forEach(::stopOne)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        active.values.forEach { it.stop = true; it.recorder?.stop() }
        scope.cancel()
        super.onDestroy()
    }

    private fun stopOne(id: String) {
        val a = active[id] ?: return
        a.stop = true
        a.recorder?.stop()
    }

    private fun finishIfIdle() {
        if (active.isNotEmpty()) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun record(id: String, job: Active) {
        val dao = RecordingDb.get(this).dao()
        val first = dao.get(id)
        try {
            first ?: return
            var rec: Recording = first
            val repo = (application as App).repository
            val playlist = repo.store.playlists.first().firstOrNull { it.id == first.playlistId }
                ?: return fail(dao, first, "Playlist bulunamadı")
            val stream = repo.content(playlist, ContentType.LIVE).items.firstOrNull { it.id == first.streamId && it.url.isNotEmpty() }
                ?: return fail(dao, first, "Kanal bulunamadı")
            val dir = RecordingStorage.dir(this)
            if (RecordingStorage.freeBytes(dir) < RecordingMath.MIN_FREE_WHILE_RECORDING) return fail(dao, first, "Yetersiz depolama alanı")
            val now = System.currentTimeMillis()
            val base = RecordingMath.baseName(rec.channelName, rec.title, now)
            val file = File(dir, RecordingMath.uniqueFileName(base, "ts") { File(dir, it).exists() })
            rec = rec.copy(status = RecStatus.RECORDING, filePath = file.path, error = null)
            dao.put(rec)
            job.name = rec.channelName
            updateNotification()

            val ua = repo.settings.get(Setting.UserAgent).ifBlank { "IPTVPlayer/0.1" }
            val recorder = StreamRecorder(StreamRecorder.newClient(ua), stream.url, stream.headers, file) { job.bytes = it }
            job.recorder = recorder
            if (job.stop) recorder.stop()
            val stopAt = rec.stopAt
            val failure = coroutineScope {
                val timer = launch {
                    delay((stopAt - System.currentTimeMillis()).coerceAtLeast(1_000))
                    recorder.stop()
                }
                val monitor = launch {
                    while (true) {
                        delay(4_000)
                        dao.setBytes(id, job.bytes)
                        updateNotification()
                        if (RecordingStorage.freeBytes(dir) < RecordingMath.MIN_FREE_WHILE_RECORDING) {
                            job.reason = "Depolama alanı doldu"
                            recorder.stop()
                        }
                    }
                }
                val err = withContext(Dispatchers.IO) { runCatching { recorder.run() }.exceptionOrNull() }
                timer.cancel()
                monitor.cancel()
                err
            }
            val bytes = file.length()
            val message = job.reason ?: failure?.message
            if (bytes <= 0) {
                file.delete()
                dao.put(rec.copy(status = RecStatus.FAILED, filePath = null, bytes = 0, error = message ?: "Veri alınamadı"))
            } else {
                dao.put(rec.copy(status = RecStatus.DONE, bytes = bytes, error = message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            first?.let { fail(dao, it, e.message ?: "Kayıt hatası") }
        } finally {
            active.remove(id)
            if (active.isEmpty()) finishIfIdle() else updateNotification()
        }
    }

    private suspend fun fail(dao: RecordingDao, rec: Recording, message: String) {
        dao.put(rec.copy(status = RecStatus.FAILED, error = message))
    }

    // region notification

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Kayıtlar", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        channel()
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, dev.iptv.player.MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val jobs = active.values.toList()
        val text = when {
            jobs.isEmpty() -> "Hazırlanıyor…"
            jobs.size == 1 -> "${jobs[0].name.ifEmpty { "Kanal" }} · ${RecordingMath.formatBytes(jobs[0].bytes)}"
            else -> "${jobs.size} kayıt · ${RecordingMath.formatBytes(jobs.sumOf { it.bytes })}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Kayıt yapılıyor")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Durdur", stop)
            .build()
    }

    private fun enterForeground() {
        val n = notification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (_: Exception) {
            // Not allowed to run in the foreground right now; the recording would be killed in the background anyway.
        }
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification()) }
    }

    // endregion

    companion object {
        const val ACTION_START = "dev.iptv.player.REC_START"
        const val ACTION_STOP = "dev.iptv.player.REC_STOP"
        const val EXTRA_ID = "rec_id"
        private const val CHANNEL_ID = "recordings"
        private const val NOTIFICATION_ID = 4101

        @Volatile var running = false
            private set
    }
}
