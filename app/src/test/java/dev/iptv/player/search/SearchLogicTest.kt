package dev.iptv.player.search

import dev.iptv.player.data.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {
    @Test fun changedRestrictionsApplyToPreviouslyIndexedRows() {
        val cached = SearchRow("p", "LIVE", "42", "Channel", "channel", null, null, "news", "News")
        val open = SearchVisibility(setOf("p"), emptySet(), emptySet(), emptySet(), false)
        assertTrue(open.allows(cached))
        assertFalse(open.copy(lockedCategories = setOf("p|LIVE|news")).allows(cached))
        assertFalse(open.copy(hiddenChannels = setOf("p|LIVE|42")).allows(cached))
        assertFalse(open.copy(hiddenCategories = setOf("p|LIVE|news")).allows(cached))
        assertFalse(open.copy(playlistIds = emptySet()).allows(cached))
    }

    @Test fun adultCategoryIsHiddenEvenWhenTheChannelNameIsInnocuous() {
        val cached = SearchRow("p", "LIVE", "42", "Channel", "channel", null, null, "adult", "18+ Adult")
        val visibility = SearchVisibility(setOf("p"), emptySet(), emptySet(), emptySet(), true)
        assertFalse(visibility.allows(cached))
        assertTrue(visibility.copy(hideAdult = false).allows(cached))
    }

    private fun row(type: String, name: String, id: String = name) = SearchRow("p", type, id, name, TextSearch.normalize(name), null, null)

    @Test fun parseNormalizesAndRejectsShort() {
        assertNull(SearchQuery.parse(null))
        assertNull(SearchQuery.parse(" a "))
        val q = SearchQuery.parse("  İSTANBUL   Şampiyon ")!!
        assertEquals(listOf("istanbul", "sampiyon"), q.tokens)
        assertEquals("istanbul sampiyon", q.joined)
        assertEquals(SearchQuery.MAX_LENGTH, SearchQuery.parse("x".repeat(200))!!.joined.length)
    }

    @Test fun scoreLevels() {
        val q = SearchQuery.parse("kanal")!!
        assertEquals(3, SearchLogic.score("kanal d", q))
        assertEquals(2, SearchLogic.score("ulusal kanal", q))
        assertEquals(1, SearchLogic.score("ulusalkanalx", q))
        assertEquals(0, SearchLogic.score("haber", q))
        assertEquals(0, SearchLogic.score("kanal", SearchQuery.parse("kanal haber")!!))
    }

    @Test fun rankingPrefersPrefixThenType() {
        val rows = listOf(
            row("MOVIE", "Büyük Kanal Filmi"),
            row("LIVE", "Kanal D"),
            row("SERIES", "Kanal 7 Dizisi"),
            row("MOVIE", "Kanal Sineması"),
            row("LIVE", "Spor"),
        )
        val ranked = SearchLogic.rank(rows, SearchQuery.parse("KANAL")!!, 10)
        assertEquals(listOf("Kanal D", "Kanal Sineması", "Kanal 7 Dizisi", "Büyük Kanal Filmi"), ranked.map { it.name })
        assertEquals(2, SearchLogic.rank(rows, SearchQuery.parse("kanal")!!, 2).size)
    }

    @Test fun turkishCaseInsensitiveMatch() {
        val rows = listOf(row("LIVE", "ŞAHİN TV"))
        assertTrue(SearchLogic.rank(rows, SearchQuery.parse("sahin")!!, 5).isNotEmpty())
        assertNotNull(SearchQuery.parse("şahin"))
        assertTrue(SearchLogic.rank(rows, SearchQuery.parse("şahin")!!, 5).isNotEmpty())
    }
}
