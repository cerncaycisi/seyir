package dev.iptv.player.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.iptv.player.MainActivity
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Result of a user-visible recording action; [message] is ready to show. */
data class RecordOutcome(val ok: Boolean, val message: String)

/** Entry point for starting, scheduling, stopping and deleting recordings (metadata in recordings.db, bytes written by [RecordingService]). */
object RecordingManager {
    private const val ACTION_ALARM = "dev.iptv.player.REC_ALARM"

    private fun dao(context: Context) = RecordingDb.get(context).dao()

    /** Records [stream] now, until the current programme ends ([programmeEndSec]) or 3 hours. */
    suspend fun startNow(context: Context, playlist: Playlist, stream: Stream, title: String, programmeEndSec: Long?): RecordOutcome {
        val now = System.currentTimeMillis()
        val rows = dao(context).all()
        if (rows.any { it.status == RecStatus.RECORDING && it.playlistId == playlist.id && it.streamId == stream.id }) {
            return RecordOutcome(false, "Bu kanal zaten kaydediliyor")
        }
        return begin(context, Recording(Recording.idOf(playlist.id, stream.id, now), playlist.id, stream.id, stream.name, title, now, RecordingMath.instantStopMs(now, programmeEndSec), RecStatus.SCHEDULED))
    }

    /** Plans a recording of a future programme; one that is already airing starts immediately. */
    suspend fun schedule(context: Context, playlist: Playlist, stream: Stream, title: String, startSec: Long, stopSec: Long): RecordOutcome {
        val now = System.currentTimeMillis()
        val w = RecordingMath.window(startSec, stopSec, now) ?: return RecordOutcome(false, "Program zaten bitmiş")
        val rec = Recording(Recording.idOf(playlist.id, stream.id, startSec * 1000), playlist.id, stream.id, stream.name, title, w.startMs, w.stopMs, RecStatus.SCHEDULED)
        if (w.startMs <= now + 5_000) return begin(context, rec)
        spaceError(context, w.durationMs)?.let { return it }
        dao(context).put(rec)
        armAlarm(context, rec)
        return RecordOutcome(true, "Kayıt planlandı: $title")
    }

    private suspend fun begin(context: Context, rec: Recording): RecordOutcome {
        spaceError(context, rec.stopAt - rec.startAt)?.let { return it }
        dao(context).put(rec)
        return if (launchService(context, rec.id)) {
            RecordOutcome(true, "Kayıt başladı: ${rec.channelName}")
        } else {
            dao(context).put(rec.copy(status = RecStatus.FAILED, error = "Kayıt servisi başlatılamadı"))
            RecordOutcome(false, "Kayıt servisi başlatılamadı")
        }
    }

    private fun spaceError(context: Context, durationMs: Long): RecordOutcome? {
        val free = RecordingStorage.freeBytes(RecordingStorage.dir(context))
        return if (RecordingMath.hasSpace(free, durationMs)) null
        else RecordOutcome(false, "Yetersiz depolama alanı (boş: ${RecordingMath.formatBytes(free)})")
    }

    fun launchService(context: Context, id: String): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra(RecordingService.EXTRA_ID, id),
        )
        true
    } catch (_: Exception) {
        false
    }

    /** Asks the service to finish [id] (the file written so far is kept). */
    fun stop(context: Context, id: String) {
        runCatching {
            context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP).putExtra(RecordingService.EXTRA_ID, id))
        }
    }

    /** Cancels a planned recording, or stops a running one (keeping its file). */
    suspend fun cancel(context: Context, rec: Recording) {
        when (rec.status) {
            RecStatus.SCHEDULED -> {
                cancelAlarm(context, rec.id)
                dao(context).delete(rec.id)
            }
            RecStatus.RECORDING -> stop(context, rec.id)
        }
    }

    /** Removes the metadata and the file; a running recording is stopped first. */
    suspend fun delete(context: Context, rec: Recording) {
        if (rec.status == RecStatus.RECORDING) stop(context, rec.id)
        cancelAlarm(context, rec.id)
        rec.filePath?.let { runCatching { File(it).delete() } }
        dao(context).delete(rec.id)
    }

    /**
     * Fixes rows that no live service owns (process death, reboot): interrupted recordings keep what was written,
     * missed schedules are marked failed.
     */
    suspend fun recoverStale(context: Context) {
        if (RecordingService.running) return
        val now = System.currentTimeMillis()
        val d = dao(context)
        d.all().forEach { r ->
            when {
                r.status == RecStatus.RECORDING -> {
                    val size = r.filePath?.let { File(it).length() } ?: 0L
                    d.put(if (size > 0) r.copy(status = RecStatus.DONE, bytes = size, error = "Kayıt yarıda kesildi") else r.copy(status = RecStatus.FAILED, error = "Kayıt yarıda kesildi"))
                }
                r.status == RecStatus.SCHEDULED && r.startAt <= now -> d.put(r.copy(status = RecStatus.FAILED, error = "Zamanında başlatılamadı"))
            }
        }
    }

    /** Re-arms alarms for planned recordings (alarms do not survive a reboot). */
    suspend fun rescheduleAll(context: Context) {
        recoverStale(context)
        val now = System.currentTimeMillis()
        dao(context).all().filter { it.status == RecStatus.SCHEDULED && it.startAt > now }.forEach { armAlarm(context, it) }
    }

    // region alarms

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_ALARM)
            .setData(Uri.parse("iptv-recording://${id.hashCode()}"))
            .putExtra(RecordingService.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun armAlarm(context: Context, rec: Recording) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, rec.id)
        try {
            // setAlarmClock is exact, survives Doze and needs no special permission; it also lets the receiver start a foreground service.
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(rec.startAt, show), pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rec.startAt, pi)
        }
    }

    fun cancelAlarm(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, id))
    }

    // endregion
}

/** Fires at a planned recording's start time. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingService.EXTRA_ID) ?: return
        if (RecordingManager.launchService(context, id)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val d = RecordingDb.get(context).dao()
                d.get(id)?.let { d.put(it.copy(status = RecStatus.FAILED, error = "Kayıt servisi başlatılamadı")) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are lost on reboot; re-arm the planned recordings. */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RecordingManager.rescheduleAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
