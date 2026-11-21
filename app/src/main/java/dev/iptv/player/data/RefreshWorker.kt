package dev.iptv.player.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.iptv.player.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodically re-downloads every playlist into the Room cache so the app opens with fresh data. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repository
        val playlists = repo.store.playlists.first()
        var failed = 0
        for (playlist in playlists) {
            try {
                repo.reload(playlist)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }
        return if (playlists.isNotEmpty() && failed == playlists.size) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "content-refresh"

        /** Schedules (or reschedules) the periodic refresh; [hours] <= 0 cancels it. Needs a network connection. */
        fun schedule(context: Context, hours: Int) {
            val work = WorkManager.getInstance(context)
            if (hours <= 0) {
                work.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
