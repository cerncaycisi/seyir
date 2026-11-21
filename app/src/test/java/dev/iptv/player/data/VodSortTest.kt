package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSortTest {
    private fun s(id: String, name: String = id, rating: String? = null, year: String? = null, added: Long? = null, genre: String? = null) =
        Stream(id, name, null, "u", "c", ContentType.MOVIE, rating = rating, year = year, added = added, genre = genre)

    @Test
    fun latestUsesAddedTimestampsOrListOrder() {
        val withAdded = listOf(s("a", added = 1), s("b", added = 3), s("c", added = 2))
        assertEquals(listOf("b", "c"), latestOf(withAdded, 2).map { it.id })
        val noAdded = listOf(s("a"), s("b"), s("c"))
        assertEquals(listOf("a", "b"), latestOf(noAdded, 2).map { it.id })
    }

    @Test
    fun sortsByRatingYearAndTurkishName() {
        val items = listOf(s("1", "Çay", rating = "5.0", year = "2001"), s("2", "Ada", rating = "8.1", year = "1999"), s("3", "Zeta"))
        assertEquals(listOf("2", "1", "3"), sortStreams(items, VodSort.RATING).map { it.id })
        assertEquals(listOf("1", "2", "3"), sortStreams(items, VodSort.YEAR).map { it.id })
        assertEquals(listOf("2", "1", "3"), sortStreams(items, VodSort.AZ).map { it.id })
    }

    @Test
    fun genreSplitAndFilter() {
        val items = listOf(s("1", genre = "Aksiyon, Dram"), s("2", genre = "Dram"), s("3"))
        assertEquals(listOf("Dram", "Aksiyon"), genresOf(items))
        assertEquals(listOf("1", "2"), filterByGenre(items, "dram").map { it.id })
    }

    @Test
    fun historyFinishedAndContinueRules() {
        fun e(id: String, pos: Long, dur: Long, series: String? = null, at: Long = 0) = HistoryEntry(
            key = id, favKey = id, playlistId = "p", id = id, type = ContentType.MOVIE, name = id,
            seriesId = series, positionMs = pos, durationMs = dur, updatedAt = at,
        )
        assertTrue(e("a", 99_000, 100_000).finished)
        assertFalse(e("a", 50_000, 100_000).finished)
        // Only the latest episode per series, and only if unfinished and watched long enough.
        val list = listOf(
            e("ep1", 30_000, 3_600_000, series = "s", at = 1),
            e("ep2", 30_000, 3_600_000, series = "s", at = 2),
            e("m", 5_000, 3_600_000, at = 3),
            e("m2", 1_000_000, 3_600_000, at = 4),
        )
        assertEquals(listOf("m2", "ep2"), continueWatching(list).map { it.id })
    }
}
