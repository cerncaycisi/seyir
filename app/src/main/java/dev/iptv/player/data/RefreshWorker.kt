package dev.iptv.player.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.workDataOf
import dev.iptv.player.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Periodically re-downloads every playlist into the Room cache so the app opens with fresh data. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // Bulk work runs on low-priority threads so browsing stays smooth on weak TVs.
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(dev.iptv.player.util.BackgroundWork.dispatcher) { work() }

    private suspend fun work(): Result {
        val repo = (applicationContext as App).repository
        val requestedId = inputData.getString("playlist")
        val playlists = repo.store.playlists.first().filter { requestedId == null || it.id == requestedId }
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
        val output = workDataOf("finished" to System.currentTimeMillis())
        return if (playlists.isNotEmpty() && failed == playlists.size) {
            if (requestedId != null) Result.failure(output) else Result.retry()
        } else Result.success(output)
    }

    companion object {
        private const val UNIQUE_NAME = "content-refresh"

        fun request(context: Context, playlistId: String) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf("playlist" to playlistId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("playlist-refresh-$playlistId", ExistingWorkPolicy.KEEP, request)
        }

        fun observe(context: Context, playlistId: String) = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("playlist-refresh-$playlistId").map { jobs ->
                jobs.firstOrNull { !it.state.isFinished }
                    ?: jobs.maxByOrNull { it.outputData.getLong("finished", 0) }
            }

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
