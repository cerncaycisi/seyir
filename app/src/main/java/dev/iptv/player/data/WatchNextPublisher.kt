package dev.iptv.player.data

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/** Deep links understood by MainActivity: iptvplayer://movie/{id}?playlist={pid} and iptvplayer://series/{id}?playlist={pid}. */
object DeepLinks {
    const val SCHEME = "iptvplayer"

    fun movie(playlistId: String, id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority("movie").appendPath(id).appendQueryParameter("playlist", playlistId).build()

    /** Opens the player on a live channel (used by system search). */
    fun live(playlistId: String, id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority("live").appendPath(id).appendQueryParameter("playlist", playlistId).build()

    fun series(playlistId: String, id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority("series").appendPath(id).appendQueryParameter("playlist", playlistId).build()
}

/**
 * Publishes in-progress movies/series to the Google TV "Watch Next" row and removes them once finished.
 * Every provider call is guarded: the row is a nicety and must never break playback.
 */
class WatchNextPublisher(private val context: Context) {
    fun sync(entries: List<HistoryEntry>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { doSync(continueWatching(entries).take(MAX_ITEMS)) }
    }

    private fun doSync(wanted: List<HistoryEntry>) {
        val resolver = context.contentResolver
        val existing = HashMap<String, Long>() // internalProviderId -> program id
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION, null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        val wantedKeys = wanted.map { it.key }.toSet()
        existing.filterKeys { it !in wantedKeys }.values.forEach { id ->
            resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
        }
        wanted.forEach { e ->
            val program = build(e)
            val id = existing[e.key]
            if (id == null) {
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            } else {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(id), program.toContentValues(), null, null)
            }
        }
    }

    private fun build(e: HistoryEntry): WatchNextProgram {
        val seriesId = e.seriesId
        val link = if (seriesId != null) DeepLinks.series(e.playlistId, seriesId) else DeepLinks.movie(e.playlistId, e.id)
        val builder = WatchNextProgram.Builder()
            .setType(
                if (seriesId != null) TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES
                else TvContractCompat.WatchNextPrograms.TYPE_MOVIE,
            )
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(e.updatedAt)
            .setTitle(e.name)
            .setLastPlaybackPositionMillis(e.positionMs.toInt())
            .setDurationMillis(e.durationMs.toInt())
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            .setIntentUri(link)
            .setInternalProviderId(e.key)
        e.logo?.let { runCatching { builder.setPosterArtUri(Uri.parse(it)) } }
        return builder.build()
    }

    private companion object {
        const val MAX_ITEMS = 10
    }
}
