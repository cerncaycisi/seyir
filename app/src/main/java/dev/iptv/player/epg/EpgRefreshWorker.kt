package dev.iptv.player.epg

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.iptv.player.data.PlaylistStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Daily guide refresh for the active playlist. */
class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = PlaylistStore(applicationContext)
        val activeId = store.activeId.first()
        val all = store.playlists.first()
        val playlist = all.firstOrNull { it.id == activeId } ?: all.firstOrNull() ?: return Result.success()
        val service = EpgService.get(applicationContext)
        service.refresh(playlist)
        return if (service.status.value is EpgStatus.Error) Result.retry() else Result.success()
    }
}

object EpgScheduler {
    private const val NAME = "epg-refresh"

    /** Idempotent; call from Application.onCreate. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
