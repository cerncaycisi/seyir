package dev.iptv.player.player

/** Pure rules for the basic live pause / timeshift behaviour. */
object Timeshift {
    /** Shorter pauses just resume from ExoPlayer's buffer. */
    const val MIN_PAUSE_FOR_CATCHUP_MS = 15_000L
    const val CATCHUP_SPAN_SEC = 3 * 3600L

    /** "Canlıya dön" appears once playback is this far behind the player's normal live offset. */
    const val BEHIND_LIVE_SHOW_SEC = 8L
    private const val DEFAULT_TARGET_OFFSET_MS = 20_000L

    fun useCatchup(pausedForMs: Long, catchupAvailable: Boolean) = catchupAvailable && pausedForMs >= MIN_PAUSE_FOR_CATCHUP_MS

    /** Catch-up window (startSec, endSec) that begins at the moment playback was paused. */
    fun catchupRange(pausedAtMs: Long): Pair<Long, Long> = (pausedAtMs / 1000).let { it to it + CATCHUP_SPAN_SEC }

    /** How far behind live a catch-up stream started at [shiftStartSec] is after [positionMs] of playback. */
    fun behindSec(nowSec: Long, shiftStartSec: Long, positionMs: Long): Long = (nowSec - (shiftStartSec + positionMs / 1000)).coerceAtLeast(0)

    /** Seconds behind the usual live position, from ExoPlayer's live offset and the stream's target offset (millis, null = unknown). */
    fun behindFromLiveOffset(offsetMs: Long?, targetOffsetMs: Long?): Long =
        if (offsetMs == null || offsetMs < 0) 0
        else ((offsetMs - (targetOffsetMs?.takeIf { it > 0 } ?: DEFAULT_TARGET_OFFSET_MS)) / 1000).coerceAtLeast(0)

    /** Heuristic: is this URL already a catch-up/replay URL? Pausing one must not swap it for a fresh catch-up. */
    fun looksLikeReplay(url: String): Boolean = "/timeshift/" in url || "utc=" in url || "lutc=" in url

    fun label(behindSec: Long): String = when {
        behindSec < 60 -> "Canlının $behindSec sn gerisinde"
        else -> "Canlının ${behindSec / 60} dk ${behindSec % 60} sn gerisinde"
    }
}
