package dev.iptv.player.ui

import dev.iptv.player.data.Category
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentRules
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.HistoryEntry
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentVisibilityTest {
    private val playlist = Playlist("p", "Test", PlaylistSource.M3u("https://example.com/list.m3u"))

    @Test fun `flat lists hide innocently named items in adult categories`() {
        val safe = Stream("safe", "News", null, "", "news", ContentType.LIVE)
        val restricted = Stream("restricted", "Channel 1", null, "", "adult", ContentType.LIVE)
        val content = Content(listOf(Category("news", "News"), Category("adult", "18+")), listOf(safe, restricted))
        assertEquals(listOf(safe), visibleContentItems(playlist, ContentType.LIVE, content, ContentRules(hideAdult = true)))
    }

    @Test fun `episode history inherits locked parent series category`() {
        val series = Stream("series", "A Series", null, "", "locked", ContentType.SERIES)
        val content = Content(listOf(Category("locked", "Drama")), listOf(series))
        val history = entry("episode", ContentType.SERIES).copy(seriesId = series.id)
        val rules = ContentRules(lockedCategories = setOf("p|SERIES|locked"))
        val catalog = mapOf(ContentType.SERIES to content)
        assertNull(visibleHistoryStream(playlist, history, catalog, rules))
        assertEquals("locked", visibleHistoryStream(playlist, history, catalog, rules.copy(unlocked = true))?.categoryId)
    }

    @Test fun `hidden category remains hidden when its label is missing`() {
        val stream = Stream("movie", "A Movie", null, "", "hidden", ContentType.MOVIE)
        val content = Content(emptyList(), listOf(stream))
        assertEquals(emptyList<Stream>(), visibleContentItems(playlist, ContentType.MOVIE, content, ContentRules(hiddenCategories = setOf("p|MOVIE|hidden"))))
    }

    @Test fun `history cannot bring back hidden or removed movies`() {
        val movie = Stream("movie", "A Movie", null, "", "movies", ContentType.MOVIE)
        val catalog = mapOf(ContentType.MOVIE to Content(emptyList(), listOf(movie)))
        assertNull(visibleHistoryStream(playlist, entry("movie", ContentType.MOVIE), catalog, ContentRules(hiddenChannels = setOf("p|MOVIE|movie"))))
        assertNull(visibleHistoryStream(playlist, entry("removed", ContentType.MOVIE), catalog, ContentRules()))
    }

    @Test fun `offline history is retained only when category visibility can be trusted`() {
        val history = entry("movie", ContentType.MOVIE)
        assertEquals(history.toStream(), visibleHistoryStream(playlist, history, emptyMap(), ContentRules()))
        assertNull(visibleHistoryStream(playlist, history, emptyMap(), ContentRules(hideAdult = true)))
    }

    private fun entry(id: String, type: ContentType) = HistoryEntry("p|$type|$id", "p|$type|$id", "p", id, type, "Title")
}
