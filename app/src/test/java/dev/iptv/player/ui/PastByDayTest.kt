package dev.iptv.player.ui

import dev.iptv.player.epg.Programme
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class PastByDayTest {
    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul") // UTC+3
    private fun p(epoch: Long) = Programme("c", epoch, epoch + 1800, "t$epoch")

    // 2026-11-21 20:59 UTC = 23:59 Istanbul (Nov 21); 21:01 UTC = 00:01 Istanbul (Nov 22).
    private val beforeMidnight = 1_795_294_740L
    private val afterMidnight = 1_795_294_860L

    @Test fun splitsAtLocalMidnightNotUtc() {
        val days = pastByDay(listOf(p(beforeMidnight), p(afterMidnight)), istanbul)
        assertEquals(2, days.size)
        assertEquals(1, days[0].first - days[1].first)
    }

    @Test fun newestDayAndProgrammeFirst() {
        val days = pastByDay(listOf(p(beforeMidnight - 7200), p(afterMidnight), p(beforeMidnight), p(afterMidnight + 3600)), istanbul)
        assertEquals(listOf(afterMidnight + 3600, afterMidnight), days[0].second.map { it.start })
        assertEquals(listOf(beforeMidnight, beforeMidnight - 7200), days[1].second.map { it.start })
    }
}
