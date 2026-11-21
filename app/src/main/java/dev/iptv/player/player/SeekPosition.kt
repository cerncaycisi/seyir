package dev.iptv.player.player

internal fun seekPosition(position: Long, delta: Long, duration: Long): Long =
    (position + delta).coerceIn(0L, duration.coerceAtLeast(0L))
