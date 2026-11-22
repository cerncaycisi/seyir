package dev.iptv.player.player

import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodesPanelLogicTest {

    private fun ep(id: String, name: String, season: Int, episode: Int) = Stream(
        id = id,
        name = name,
        logo = null,
        url = "https://example.com/$id.mp4",
        categoryId = "",
        type = ContentType.SERIES,
        seriesId = "show-1",
        season = season,
        episode = episode,
    )

    @Test
    fun `extracts sorted distinct season numbers`() {
        val items = listOf(
            ep("3", "Show · S2B1 · Two", season = 2, episode = 1),
            ep("1", "Show · S1B1 · One", season = 1, episode = 1),
            ep("2", "Show · S1B2 · Two", season = 1, episode = 2),
        )
        assertEquals(listOf(1, 2), seasonNumbersOf(items))
    }

    @Test
    fun `filters episodes by season while preserving global indices`() {
        val items = listOf(
            ep("a", "Show · S1B1 · Pilot", season = 1, episode = 1),
            ep("b", "Show · S1B2 · Next", season = 1, episode = 2),
            ep("c", "Show · S2B1 · Premiere", season = 2, episode = 1),
        )
        val s2 = episodesForSeason(items, 2)
        assertEquals(1, s2.size)
        assertEquals(2, s2[0].index)
        assertEquals("c", s2[0].value.id)
    }

    @Test
    fun `formats structured and unstructured episode labels cleanly`() {
        val structured = ep("1", "The Last of Us · S1B2 · Infected", season = 1, episode = 2)
        assertEquals("S1B2" to "Infected", formatEpisodePanelLabel(structured, fallbackNumber = 2))

        val plain = ep("2", "Pilot Episode", season = 1, episode = 4)
        assertEquals("B4" to "Pilot Episode", formatEpisodePanelLabel(plain, fallbackNumber = 1))

        val noEpisodeNum = ep("3", "Special", season = 1, episode = 0)
        assertEquals("B7" to "Special", formatEpisodePanelLabel(noEpisodeNum, fallbackNumber = 7))
    }
}
