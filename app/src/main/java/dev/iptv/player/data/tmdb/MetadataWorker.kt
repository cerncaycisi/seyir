package dev.iptv.player.data.tmdb

import android.content.Context
import androidx.work.*
import dev.iptv.player.App
import dev.iptv.player.data.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** Durable, quiet, bounded batches. No screen lifetime or foreground progress dialog involved. */
class MetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // Bulk work runs on low-priority threads so browsing stays smooth on weak TVs.
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(dev.iptv.player.util.BackgroundWork.dispatcher) { work() }

    private suspend fun work(): Result {
        val repo = (applicationContext as App).repository
        val playlistId = inputData.getString("playlist") ?: return Result.failure()
        val type = inputData.getString("type")?.let { runCatching { ContentType.valueOf(it) }.getOrNull() } ?: return Result.failure()
        if (!repo.tmdb.available() || repo.store.playlists.first().none { it.id == playlistId }) return Result.success()
        val dao = repo.metadata.dao
        val started = System.currentTimeMillis()
        try {
            // Bound each run well below WorkManager's time limit; persist each pair before continuing.
            repeat(80) {
                val batch = dao.pending(playlistId, type.name, 2)
                if (batch.isEmpty()) return Result.success()
                val answers = coroutineScope { batch.map { entry -> async { entry to repo.tmdb.resolve(entry.name, type, entry.year, tmdbId = entry.tmdbId) } }.awaitAll() }
                var failed = false
                for ((entry, result) in answers) {
                    if (result.completed) dao.resolve(playlistId, type.name, entry.id, entry.identity, result.info?.let { Json.encodeToString(it) })
                    else failed = true
                }
                if (failed) return Result.retry() // A network failure is not a permanent "no match".
                if (!repo.tmdb.available()) return Result.success()
                if (System.currentTimeMillis() - started > 360_000L) {
                    enqueue(applicationContext, playlistId, type)
                    return Result.success()
                }
                delay(250)
            }
            if (dao.pending(playlistId, type.name, 1).isNotEmpty()) enqueue(applicationContext, playlistId, type)
            return Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return Result.retry() }
    }

    companion object {
        private fun name(playlistId: String, type: ContentType) = "tmdb-catalog-$playlistId-${type.name}"
        fun enqueue(context: Context, playlistId: String, type: ContentType) {
            if (type == ContentType.LIVE) return
            val request = OneTimeWorkRequestBuilder<MetadataWorker>()
                .setInputData(workDataOf("playlist" to playlistId, "type" to type.name))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // Append to a running batch so a refresh cannot cancel an in-flight lookup or lose its follow-up.
            WorkManager.getInstance(context).enqueueUniqueWork(name(playlistId, type), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun cancel(context: Context, playlistId: String) {
            ContentType.entries.filter { it != ContentType.LIVE }.forEach {
                WorkManager.getInstance(context).cancelUniqueWork(name(playlistId, it))
            }
        }
    }
}
