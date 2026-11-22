package dev.iptv.player.util

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Two low-priority threads for bulk work the viewer is not waiting on: EPG ingestion, playlist refresh
 * parsing and TMDB catalogue enrichment. On weak TV SoCs (4 slow cores) these jobs at normal priority
 * starved the UI thread while browsing; at THREAD_PRIORITY_BACKGROUND the scheduler favours frames.
 */
object BackgroundWork {
    private val count = AtomicInteger()
    val dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2) { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "seyir-bg-${count.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
