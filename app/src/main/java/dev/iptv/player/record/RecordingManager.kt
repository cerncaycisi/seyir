package dev.iptv.player.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.iptv.player.MainActivity
import dev.iptv.player.R
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Result of a user-visible recording action; [message] is ready to show. */
data class RecordOutcome(val ok: Boolean, val message: String, val requiresExactAlarmPermission: Boolean = false)

/** Entry point for starting, scheduling, stopping and deleting recordings (metadata in recordings.db, bytes written by [RecordingService]). */
object RecordingManager {
    private const val ACTION_ALARM = "dev.iptv.player.REC_ALARM"

    private fun dao(context: Context) = RecordingDb.get(context).dao()

    /** Records [stream] now, until the current programme ends ([programmeEndSec]) or 3 hours. */
    suspend fun startNow(context: Context, playlist: Playlist, stream: Stream, title: String, programmeEndSec: Long?): RecordOutcome {
        val now = System.currentTimeMillis()
        val rows = dao(context).all()
        if (rows.any { it.status in setOf(RecStatus.RECORDING, RecStatus.SCHEDULED) && it.startAt <= now && it.playlistId == playlist.id && it.streamId == stream.id }) {
            return RecordOutcome(false, context.getString(R.string.record_already_recording))
        }
        return begin(context, Recording(Recording.idOf(playlist.id, stream.id, now), playlist.id, stream.id, stream.name, title, now, RecordingMath.instantStopMs(now, programmeEndSec), RecStatus.SCHEDULED))
    }

    /** Plans a recording of a future programme; one that is already airing starts immediately. */
    suspend fun schedule(context: Context, playlist: Playlist, stream: Stream, title: String, startSec: Long, stopSec: Long): RecordOutcome {
        val now = System.currentTimeMillis()
        val w = RecordingMath.window(startSec, stopSec, now) ?: return RecordOutcome(false, context.getString(R.string.record_programme_ended))
        val rec = Recording(Recording.idOf(playlist.id, stream.id, startSec * 1000), playlist.id, stream.id, stream.name, title, w.startMs, w.stopMs, RecStatus.SCHEDULED)
        if (dao(context).get(rec.id) != null) return RecordOutcome(false, context.getString(R.string.record_already_scheduled))
        if (w.startMs <= now + 5_000) return begin(context, rec)
        if (!canSchedule(context)) return permissionRequired(context)
        spaceError(context, w.durationMs)?.let { return it }
        dao(context).put(rec)
        if (!armAlarm(context, rec)) {
            dao(context).delete(rec.id)
            return permissionRequired(context)
        }
        return RecordOutcome(true, context.getString(R.string.record_scheduled, title))
    }

    private suspend fun begin(context: Context, rec: Recording): RecordOutcome {
        spaceError(context, rec.stopAt - rec.startAt)?.let { return it }
        dao(context).put(rec)
        return if (launchService(context, rec.id)) {
            RecordOutcome(true, context.getString(R.string.record_started, rec.channelName))
        } else {
            val message = context.getString(R.string.record_service_failed)
            dao(context).update(rec.copy(status = RecStatus.FAILED, error = message))
            RecordOutcome(false, message)
        }
    }

    private fun spaceError(context: Context, durationMs: Long): RecordOutcome? {
        val free = RecordingStorage.freeBytes(RecordingStorage.dir(context))
        return if (RecordingMath.hasSpace(free, durationMs)) null
        else RecordOutcome(false, context.getString(R.string.record_space_free, RecordingMath.formatBytes(free)))
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
                if (RecordingService.running) stop(context, rec.id)
                cancelAlarm(context, rec.id)
                dao(context).delete(rec.id)
            }
            RecStatus.RECORDING -> stop(context, rec.id)
        }
    }

    /** Removes the metadata and the file; a running recording is stopped first. */
    suspend fun delete(context: Context, rec: Recording) {
        // The UI snapshot may still say SCHEDULED while the service is already starting it.
        if (RecordingService.running) stop(context, rec.id)
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
                    val message = context.getString(R.string.record_interrupted)
                    d.update(if (size > 0) r.copy(status = RecStatus.DONE, bytes = size, error = message) else r.copy(status = RecStatus.FAILED, error = message))
                }
                r.status == RecStatus.SCHEDULED && r.startAt <= now -> d.update(r.copy(status = RecStatus.FAILED, error = context.getString(R.string.record_missed)))
            }
        }
    }

    /** Re-arms alarms for planned recordings (alarms do not survive a reboot). */
    suspend fun rescheduleAll(context: Context) {
        recoverStale(context)
        val now = System.currentTimeMillis()
        val d = dao(context)
        d.all().filter { it.status == RecStatus.SCHEDULED && it.startAt > now }.forEach {
            if (!armAlarm(context, it)) d.update(it.copy(status = RecStatus.FAILED, error = context.getString(R.string.record_exact_alarm_required)))
        }
    }

    // region alarms

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_ALARM)
            .setData(Uri.Builder().scheme("iptv-recording").authority("schedule").appendPath(id).build())
            .putExtra(RecordingService.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun canSchedule(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    }

    private fun permissionRequired(context: Context) = RecordOutcome(false, context.getString(R.string.record_exact_alarm_required), true)

    /** Called only after the user chooses the permission action in the scheduling UI. */
    fun requestExactAlarmPermission(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 31) return@runCatching false
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun armAlarm(context: Context, rec: Recording): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        if (!canSchedule(context)) return false
        val pi = pending(context, rec.id)
        return try {
            // Exact-alarm access is required on Android 12+. An inexact fallback cannot reliably start a foreground service.
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(rec.startAt, show), pi)
            true
        } catch (_: SecurityException) {
            false
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
                d.get(id)?.let { d.update(it.copy(status = RecStatus.FAILED, error = context.getString(R.string.record_service_failed))) }
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
