package dev.iptv.player.epg

import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Stream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * M3U catch-up description of a channel (`catchup`, `catchup-source` attributes).
 * [mode] is one of "default", "append", "shift"; [source] is the URL template / suffix.
 */
data class CatchupSpec(val mode: String, val source: String?)

object Catchup {
    /**
     * HOOK (M3U catch-up): the data engineer parses `catchup`, `catchup-source` and `catchup-days`
     * from #EXTINF. Map whatever fields they add onto Stream here, e.g.
     *   stream.catchupMode?.let { CatchupSpec(it, stream.catchupSource) }
     * Until then M3U channels have no catch-up, and Xtream channels use [Stream.archiveDays].
     */
    fun specOf(stream: Stream): CatchupSpec? = null // TODO(merge): wire to Stream.catchup* fields

    fun available(playlist: Playlist, stream: Stream): Boolean =
        stream.archiveDays > 0 && playlist.source is PlaylistSource.Xtream || specOf(stream) != null

    /** Days of archive for [stream]; M3U feeds with a spec but no day count default to 1. */
    fun days(stream: Stream): Int = if (stream.archiveDays > 0) stream.archiveDays else if (specOf(stream) != null) 1 else 0

    /** Can [p] be replayed now? It must be over (or nearly so) and still inside the archive window. */
    fun playable(playlist: Playlist, stream: Stream, p: Programme, nowSec: Long): Boolean =
        available(playlist, stream) && p.start <= nowSec && p.start >= nowSec - days(stream) * 86400L

    /** A copy of [stream] whose url plays [p] from the archive, or null if the playlist can't do that. */
    fun streamFor(playlist: Playlist, stream: Stream, p: Programme, nowSec: Long = System.currentTimeMillis() / 1000): Stream? {
        val url = urlFor(playlist, stream, p.start, p.stop, nowSec) ?: return null
        // Same id keeps focus restoration working; type stays LIVE so the player treats it like a channel.
        return stream.copy(url = url, name = "${stream.name} · ${p.title}")
    }

    fun urlFor(playlist: Playlist, stream: Stream, startSec: Long, endSec: Long, nowSec: Long): String? {
        val spec = specOf(stream)
        if (spec != null) return m3uUrl(stream.url, spec, startSec, endSec, nowSec)
        val src = playlist.source as? PlaylistSource.Xtream ?: return null
        if (stream.archiveDays <= 0) return null
        val minutes = ((endSec - startSec + 59) / 60).toInt().coerceAtLeast(1)
        return xtreamTimeshiftUrl(src.server, src.username, src.password, stream.id, startSec, minutes)
    }

    /**
     * Xtream timeshift: `{base}/timeshift/{user}/{pass}/{durationMinutes}/{yyyy-MM-dd:HH-mm}/{streamId}.ts`.
     *
     * Timezone assumption: panels interpret the start stamp in their own server timezone, which is
     * almost always the same zone their XMLTV feed is expressed in. We format in [zone], defaulting to
     * the device timezone. TODO: use `server_info.timezone` from the login response when available.
     */
    fun xtreamTimeshiftUrl(
        server: String,
        user: String,
        pass: String,
        streamId: String,
        startSec: Long,
        durationMinutes: Int,
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val base = server.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it" }
        val stamp = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }.format(Date(startSec * 1000))
        return "$base/timeshift/${user.trim()}/${pass.trim()}/$durationMinutes/$stamp/$streamId.ts"
    }

    fun m3uUrl(streamUrl: String, spec: CatchupSpec, startSec: Long, endSec: Long, nowSec: Long): String? {
        val source = spec.source?.takeIf { it.isNotBlank() }
        return when (spec.mode.lowercase()) {
            "default" -> source?.let { expandTemplate(it, startSec, endSec, nowSec) }
            "append" -> source?.let { streamUrl + expandTemplate(it, startSec, endSec, nowSec) }
            "shift", "timeshift" -> streamUrl + (if ('?' in streamUrl) "&" else "?") + "utc=$startSec&lutc=$nowSec"
            else -> source?.let { expandTemplate(it, startSec, endSec, nowSec) }
        }
    }

    private val token = Regex("""\$?\{([A-Za-z]+)(?::(\d+))?}""")

    /**
     * Expands catch-up placeholders. Epoch tokens: {utc} {start} {timestamp}(start) ${start} ;
     * {utcend} {end} ${end}; {lutc} {now} (current time); {duration} (seconds, `{duration:60}` = minutes);
     * {offset} (seconds since start); {Y}{m}{d}{H}{M}{S} = start time fields in UTC.
     */
    fun expandTemplate(template: String, startSec: Long, endSec: Long, nowSec: Long): String {
        val utc = TimeZone.getTimeZone("GMT")
        fun fmt(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = utc }.format(Date(startSec * 1000))
        return token.replace(template) { m ->
            val div = m.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: 1L
            when (m.groupValues[1]) {
                "utc", "start", "timestamp" -> startSec.toString()
                "utcend", "end" -> endSec.toString()
                "lutc", "now" -> nowSec.toString()
                "duration" -> ((endSec - startSec) / div).toString()
                "offset" -> (nowSec - startSec).toString()
                "Y" -> fmt("yyyy")
                "m" -> fmt("MM")
                "d" -> fmt("dd")
                "H" -> fmt("HH")
                "M" -> fmt("mm")
                "S" -> fmt("ss")
                else -> m.value
            }
        }
    }
}
