package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseMemoryTest {

    @Test
    fun `update and decode round trip preserves per profile playlist and type keys`() {
        val kLive = browseKey("default", "p1", ContentType.LIVE)
        val kMovie = browseKey("kids", "p1", ContentType.MOVIE)
        val kSeries = browseKey("kids", "p1", ContentType.SERIES)

        val step1 = updateBrowsePref("{}", kLive, "cat_news", "AZ")
        val step2 = updateBrowsePref(step1, kMovie, "cat_anim", "RECENT")

        val decoded = decodeBrowsePrefs(step2)
        assertEquals(BrowseChoice("cat_news", "AZ"), decoded[kLive])
        assertEquals(BrowseChoice("cat_anim", "RECENT"), decoded[kMovie])
        assertEquals(BrowseChoice("", "RECENT"), decoded[kSeries])
    }

    @Test
    fun `malformed json returns empty map gracefully`() {
        assertEquals(emptyMap<String, BrowseChoice>(), decodeBrowsePrefs("not-json"))
        assertEquals(emptyMap<String, BrowseChoice>(), decodeBrowsePrefs(""))
    }
}
