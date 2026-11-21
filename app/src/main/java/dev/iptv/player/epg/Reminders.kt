package dev.iptv.player.epg

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.iptv.player.MainActivity
import dev.iptv.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A reminder that just fired, or a deep link asking the app to tune a channel. */
data class ReminderEvent(val playlistId: String, val streamId: String, val channelName: String, val title: String, val start: Long)

data class TuneRequest(val playlistId: String, val streamId: String)

/**
 * Hand-off between the alarm receiver / notification deep link and the UI:
 *  - [fired]: reminder due while the app is in the foreground -> in-app banner (ReminderHost)
 *  - [tune]:  user chose "İzle" or tapped the notification -> AppNav starts the live channel
 */
object ReminderBus {
    val fired = MutableSharedFlow<ReminderEvent>(extraBufferCapacity = 8)
    val tune = MutableStateFlow<TuneRequest?>(null)

    const val EXTRA_PLAYLIST = "epg_playlist_id"
    const val EXTRA_STREAM = "epg_stream_id"

    /** Call from MainActivity.onCreate / onNewIntent. */
    fun handleIntent(intent: Intent?) {
        val playlist = intent?.getStringExtra(EXTRA_PLAYLIST) ?: return
        val stream = intent.getStringExtra(EXTRA_STREAM) ?: return
        intent.removeExtra(EXTRA_PLAYLIST)
        intent.removeExtra(EXTRA_STREAM)
        tune.value = TuneRequest(playlist, stream)
    }
}

object ReminderScheduler {
    private const val ACTION = "dev.iptv.player.EPG_REMINDER"
    const val CHANNEL_ID = "epg_reminders"

    private fun pending(context: Context, r: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
            .setData(android.net.Uri.parse("iptv-reminder://${r.id.hashCode()}"))
            .putExtra("id", r.id)
            .putExtra(ReminderBus.EXTRA_PLAYLIST, r.playlistId)
            .putExtra(ReminderBus.EXTRA_STREAM, r.streamId)
            .putExtra("channel", r.channelName)
            .putExtra("title", r.title)
            .putExtra("start", r.start)
        return PendingIntent.getBroadcast(context, r.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, r: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val whenMs = (r.start * 1000).coerceAtLeast(System.currentTimeMillis() + 1000)
        // Inexact-while-idle needs no SCHEDULE_EXACT_ALARM permission; a reminder a minute late is fine.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending(context, r))
    }

    fun cancel(context: Context, id: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context, Reminder(id, "", "", "", "", 0, 0)))
    }

    fun rescheduleAll(context: Context, reminders: List<Reminder>) {
        val now = System.currentTimeMillis() / 1000
        reminders.filter { it.start > now - 60 }.forEach { schedule(context, it) }
    }

    /** True on API < 33 or when POST_NOTIFICATIONS is granted. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, event: ReminderEvent) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Program hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderBus.EXTRA_PLAYLIST, event.playlistId)
            .putExtra(ReminderBus.EXTRA_STREAM, event.streamId)
        val content = PendingIntent.getActivity(context, event.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, CHANNEL_ID) else android.app.Notification.Builder(context)
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(event.title)
            .setContentText("${event.channelName} şimdi başlıyor")
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        nm.notify(event.hashCode(), notification)
    }
}

/** Fires at the programme start: banner when the app is visible, notification otherwise. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = ReminderEvent(
            playlistId = intent.getStringExtra(ReminderBus.EXTRA_PLAYLIST) ?: return,
            streamId = intent.getStringExtra(ReminderBus.EXTRA_STREAM) ?: return,
            channelName = intent.getStringExtra("channel").orEmpty(),
            title = intent.getStringExtra("title").orEmpty(),
            start = intent.getLongExtra("start", 0),
        )
        val id = intent.getStringExtra("id")
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) ReminderBus.fired.tryEmit(event) else ReminderScheduler.notify(context, event)
        if (id != null) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    EpgService.get(context).reminderDao.delete(id)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

/** Alarms do not survive a reboot; re-arm them from the database. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EpgService.get(context) // its init re-schedules every stored reminder
    }
}
