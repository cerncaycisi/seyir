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
import dev.iptv.player.data.localizedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
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

    fun createFile(dir: File, base: String): File {
        var suffix = 1
        while (true) {
            val file = File(dir, if (suffix == 1) "$base.ts" else "${base}_${suffix}.ts")
            if (file.createNewFile()) return file
            suffix++
        }
    }
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
    @Volatile private var languageContext: Context? = null

    private fun message(id: Int, vararg args: Any): String = (languageContext ?: this).getString(id, *args)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        scope.launch {
            languageContext = (application as App).repository.settings.localizedContext(this@RecordingService)
            if (active.isNotEmpty()) updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService, even if there turns out to be nothing to do.
        val id = intent?.getStringExtra(EXTRA_ID)
        if (!enterForeground()) {
            scope.launch {
                try {
                    val dao = RecordingDb.get(this@RecordingService).dao()
                    id?.let { dao.get(it) }?.let { fail(dao, it, message(R.string.record_service_failed)) }
                } finally {
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
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
        active.values.forEach { it.reason = message(R.string.record_timeout) }
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
        var rec: Recording? = null
        var output: File? = null
        try {
            val first = dao.get(id)?.takeIf { it.status == RecStatus.SCHEDULED } ?: return
            rec = first
            val repo = (application as App).repository
            languageContext = repo.settings.localizedContext(this)
            val playlist = repo.store.playlists.first().firstOrNull { it.id == first.playlistId }
                ?: return fail(dao, first, message(R.string.record_playlist_missing))
            val stream = repo.content(playlist, ContentType.LIVE).items.firstOrNull { it.id == first.streamId && it.url.isNotEmpty() }
                ?: return fail(dao, first, message(R.string.record_channel_missing))
            val dir = RecordingStorage.dir(this)
            if (RecordingStorage.freeBytes(dir) < RecordingMath.MIN_FREE_WHILE_RECORDING) return fail(dao, first, message(R.string.record_no_space))
            val now = System.currentTimeMillis()
            if (first.stopAt <= now) return fail(dao, first, message(R.string.record_missed))
            val base = RecordingMath.baseName(first.channelName, first.title, now)
            // Reserve the name atomically: concurrent recordings can have identical channel/title/timestamps.
            val file = RecordingStorage.createFile(dir, base)
            output = file
            val recording = first.copy(status = RecStatus.RECORDING, filePath = file.path, error = null)
            rec = recording
            if (dao.update(recording) == 0) {
                file.delete()
                return
            }
            job.name = recording.channelName
            updateNotification()

            val ua = repo.settings.get(Setting.UserAgent).ifBlank { "IPTVPlayer/0.1" }
            val recorder = StreamRecorder(StreamRecorder.newClient(ua), stream.url, stream.headers, file) { job.bytes = it }
            job.recorder = recorder
            if (job.stop) recorder.stop()
            val stopAt = recording.stopAt
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
                            job.reason = message(R.string.record_storage_full)
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
            val error = job.reason ?: failure?.let(::failureMessage)
            if (bytes <= 0) {
                file.delete()
                dao.update(recording.copy(status = RecStatus.FAILED, filePath = null, bytes = 0, error = error ?: message(R.string.record_no_data)))
            } else {
                if (dao.update(recording.copy(status = RecStatus.DONE, bytes = bytes, error = error)) == 0) file.delete()
            }
        } catch (e: CancellationException) {
            // Service destruction cancels the coroutine; preserve the flushed file before releasing ownership.
            withContext(NonCancellable) {
                rec?.let {
                    val bytes = output?.length() ?: 0
                    val finished = it.copy(status = if (bytes > 0) RecStatus.DONE else RecStatus.FAILED,
                        bytes = bytes, error = job.reason ?: message(R.string.record_interrupted))
                    if (dao.update(finished) == 0 || bytes == 0L) output?.delete()
                }
            }
            throw e
        } catch (e: Exception) {
            rec?.let { fail(dao, it, failureMessage(e)) }
        } finally {
            active.remove(id)
            if (active.isEmpty()) finishIfIdle() else updateNotification()
        }
    }

    private suspend fun fail(dao: RecordingDao, rec: Recording, message: String) {
        dao.update(rec.copy(status = RecStatus.FAILED, error = message))
    }

    private fun failureMessage(error: Throwable): String = when (error) {
        is RecorderException -> when (error.reason) {
            RecorderError.EMPTY_RESPONSE -> message(R.string.record_empty_response)
            RecorderError.INTERRUPTED -> message(R.string.record_stream_interrupted)
            RecorderError.ENCRYPTED_HLS -> message(R.string.record_encrypted_hls)
            RecorderError.UNSUPPORTED_HLS -> message(R.string.record_unsupported_hls)
            RecorderError.HTTP -> message(R.string.record_http_error, error.httpCode)
        }
        is java.io.IOException -> message(R.string.record_network_error)
        else -> message(R.string.record_failed)
    }

    // region notification

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL_ID, message(R.string.record_channel_name), NotificationManager.IMPORTANCE_LOW))
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
            jobs.isEmpty() -> message(R.string.record_preparing)
            jobs.size == 1 -> message(R.string.record_notification_single, jobs[0].name.ifEmpty { message(R.string.record_default_channel) }, RecordingMath.formatBytes(jobs[0].bytes))
            else -> message(R.string.record_notification_multiple, jobs.size, RecordingMath.formatBytes(jobs.sumOf { it.bytes }))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(message(R.string.record_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, message(R.string.record_stop), stop)
            .build()
    }

    private fun enterForeground(): Boolean {
        val n = notification()
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
            true
        } catch (_: Exception) {
            // Do not continue recording after a denied foreground promotion (Android would terminate the service).
            false
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
