package dev.iptv.player.record

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pure helpers for recording file names, scheduling windows and storage estimates. */
object RecordingMath {
    const val PAD_AFTER_MS = 120_000L
    const val MAX_RECORD_MS = 8 * 3_600_000L
    const val DEFAULT_INSTANT_MS = 3 * 3_600_000L
    const val ASSUMED_MBPS = 6L
    const val RESERVE_BYTES = 300L * 1024 * 1024
    const val MIN_FREE_WHILE_RECORDING = 100L * 1024 * 1024

    /** Planned recording interval in epoch millis. */
    data class Window(val startMs: Long, val stopMs: Long) {
        val durationMs get() = stopMs - startMs
    }

    private val marks = Regex("[̀-ͯ]+")
    private val nonAlnum = Regex("[^A-Za-z0-9]+")

    /** ASCII-only, filesystem-safe fragment; Turkish letters are folded ("Şampiyonlar Ligi" -> "Sampiyonlar_Ligi"). */
    fun slug(text: String, max: Int = 40): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'ı' -> 'i'
                    'İ' -> 'I'
                    'ş' -> 's'
                    'Ş' -> 'S'
                    'ğ' -> 'g'
                    'Ğ' -> 'G'
                    'ü' -> 'u'
                    'Ü' -> 'U'
                    'ö' -> 'o'
                    'Ö' -> 'O'
                    'ç' -> 'c'
                    'Ç' -> 'C'
                    else -> c
                },
            )
        }
        val ascii = marks.replace(Normalizer.normalize(sb.toString(), Normalizer.Form.NFD), "")
        return nonAlnum.replace(ascii, "_").trim('_').take(max).trimEnd('_')
    }

    /** "Channel_Title_2026-11-21_20-30" (no extension). Title is omitted when blank. */
    fun baseName(channel: String, title: String, startMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).apply { timeZone = zone }.format(Date(startMs))
        return listOf(slug(channel).ifEmpty { "kanal" }, slug(title), stamp).filter { it.isNotEmpty() }.joinToString("_")
    }

    /** Appends "_2", "_3"... until [taken] is false. */
    fun uniqueFileName(base: String, ext: String, taken: (String) -> Boolean): String {
        var name = "$base.$ext"
        var n = 2
        while (taken(name)) name = "${base}_${n++}.$ext"
        return name
    }

    /** Recording window for a programme; null when it is already over. A short tail pad catches late-running shows. */
    fun window(startSec: Long, stopSec: Long, nowMs: Long): Window? {
        if (stopSec <= startSec) return null
        val stop = stopSec * 1000 + PAD_AFTER_MS
        if (stop <= nowMs) return null
        val start = startSec * 1000
        return Window(start, minOf(stop, maxOf(start, nowMs) + MAX_RECORD_MS))
    }

    fun delayUntilStart(w: Window, nowMs: Long): Long = (w.startMs - nowMs).coerceAtLeast(0)

    /** Stop time for "record now": until the current programme ends when known, otherwise [DEFAULT_INSTANT_MS]. */
    fun instantStopMs(nowMs: Long, programmeEndSec: Long?): Long =
        if (programmeEndSec != null && programmeEndSec * 1000 > nowMs + 30_000) minOf(programmeEndSec * 1000 + PAD_AFTER_MS, nowMs + MAX_RECORD_MS)
        else nowMs + DEFAULT_INSTANT_MS

    fun requiredBytes(durationMs: Long): Long = durationMs.coerceAtLeast(0) / 1000 * ASSUMED_MBPS * 1_000_000 / 8

    fun hasSpace(freeBytes: Long, durationMs: Long): Boolean = freeBytes >= requiredBytes(durationMs) + RESERVE_BYTES

    fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024 * 1024
        val mb = 1024.0 * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb).replace('.', ',')
            bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes / mb)
            else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }
}
