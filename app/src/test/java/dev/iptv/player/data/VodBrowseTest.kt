package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VodBrowseTest {
    private val pl = Playlist("p", "P", PlaylistSource.M3u("u"))
    private fun m(id: String, name: String = id) = Stream(id, name, null, "u", "c", ContentType.MOVIE)
    private fun sr(id: String) = Stream(id, id, null, "", "c", ContentType.SERIES)
    private fun h(id: String, type: ContentType = ContentType.MOVIE, series: String? = null, at: Long, pos: Long = 0, dur: Long = 0) =
        HistoryEntry("k$id", "p|$type|$id", "p", id, type, id, seriesId = series, positionMs = pos, durationMs = dur, updatedAt = at)

    @Test
    fun favoritesAndWatchLaterFilterByKey() {
        val items = listOf(m("1"), m("2"), m("3"))
        val fav = setOf(favoriteKey(pl, items[1]))
        val later = setOf(favoriteKey(pl, items[2]), favoriteKey(pl, items[0]))
        assertEquals(listOf("2"), specialItems(SpecialCategory.FAVORITES, pl, items, fav, later, emptyList()).map { it.id })
        assertEquals(listOf("1", "3"), specialItems(SpecialCategory.WATCH_LATER, pl, items, fav, later, emptyList()).map { it.id })
        assertEquals(3, specialItems(SpecialCategory.ALL, pl, items, fav, later, emptyList()).size)
    }

    @Test
    fun recentIsNewestFirstAndDropsMissing() {
        val items = listOf(m("1"), m("2"))
        val hist = listOf(h("1", at = 1), h("2", at = 5), h("gone", at = 9))
        assertEquals(listOf("2", "1"), recentStreams(hist, items, "p").map { it.id })
    }

    @Test
    fun episodesCollapseToSeries() {
        val items = listOf(sr("s1"), sr("s2"))
        val hist = listOf(
            h("e1", ContentType.SERIES, "s1", at = 1, pos = 20_000, dur = 100_000),
            h("e2", ContentType.SERIES, "s1", at = 4, pos = 20_000, dur = 100_000),
            h("e9", ContentType.SERIES, "s2", at = 2, pos = 99_999, dur = 100_000),
        )
        assertEquals(listOf("s1", "s2"), recentStreams(hist, items, "p").map { it.id })
        // s2's latest episode is finished, so it is not under "continue".
        assertEquals(listOf("s1"), specialItems(SpecialCategory.CONTINUE, pl, items, emptySet(), emptySet(), hist).map { it.id })
    }

    @Test
    fun toggleAddsAndRemoves() {
        assertEquals(setOf("a"), toggled(emptySet(), "a"))
        assertEquals(emptySet<String>(), toggled(setOf("a"), "a"))
    }

    @Test
    fun channelSorts() {
        val ch = listOf(m("20", "Zeta"), m("3", "alfa"), m("x", "Beta"))
        assertEquals(listOf("3", "20", "x"), sortChannels(ch, LiveSort.NUMBER).map { it.id })
        assertEquals(listOf("alfa", "Beta", "Zeta"), sortChannels(ch, LiveSort.AZ).map { it.name })
        assertEquals(listOf("Zeta", "Beta", "alfa"), sortChannels(ch, LiveSort.ZA).map { it.name })
        assertEquals(listOf("20", "3", "x"), sortChannels(ch, LiveSort.DEFAULT).map { it.id })
    }

    @Test
    fun vodZaSort() {
        val items = listOf(m("1", "a"), m("2", "c"), m("3", "b"))
        assertEquals(listOf("2", "3", "1"), sortStreams(items, VodSort.ZA).map { it.id })
    }
}

class SeriesProgressTest {
    private fun ep(id: String, s: Int, n: Int) = Episode(id, "t", s, n, "u")
    private val eps = listOf(ep("a", 2, 1), ep("b", 1, 2), ep("c", 1, 1))

    @Test
    fun neverWatchedStartsAtFirst() {
        assertEquals(SeriesContinue(2, ContinueMode.START), seriesContinue(eps, null, false))
    }

    @Test
    fun partialResumesSameEpisode() {
        assertEquals(SeriesContinue(1, ContinueMode.RESUME), seriesContinue(eps, "b", false))
    }

    @Test
    fun finishedGoesToNextNumberThenNextSeason() {
        assertEquals(SeriesContinue(1, ContinueMode.NEXT), seriesContinue(eps, "c", true))
        assertEquals(SeriesContinue(0, ContinueMode.NEXT), seriesContinue(eps, "b", true))
    }

    @Test
    fun finishedLastRestarts() {
        assertEquals(SeriesContinue(2, ContinueMode.RESTART), seriesContinue(eps, "a", true))
    }

    @Test
    fun groupingKeepsLatestEpisodePerSeries() {
        fun h(id: String, series: String, at: Long) = HistoryEntry("k$id", "f$id", "p", id, ContentType.SERIES, id, seriesId = series, updatedAt = at)
        val m = lastEpisodePerSeries(listOf(h("1", "s", 1), h("2", "s", 5), h("3", "t", 2)))
        assertEquals("2", m["p|SERIES|s"]?.id)
        assertEquals(2, m.size)
    }
}
