package dev.iptv.player.player

import dev.iptv.player.data.EpgEntry
import dev.iptv.player.epg.Programme
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveInfoPanelTest {

    @Test
    fun `filters XMLTV programmes to recent and next 24h window`() {
        val now = 10_000L
        val all = listOf(
            Programme("ch", 1_000, 2_000, "Too old"),
            Programme("ch", 8_000, 11_000, "Now playing", description = "Live news"),
            Programme("ch", 11_000, 14_000, "Up next"),
            Programme("ch", now + 30 * 3600, now + 31 * 3600, "Beyond 24h"),
        )
        val shown = visibleGuideProgrammes(all, emptyList(), now)
        assertEquals(listOf("Now playing", "Up next"), shown.map { it.title })
        assertEquals("Live news", shown[0].description)
    }

    @Test
    fun `falls back to Xtream short EPG when XMLTV list is empty`() {
        val fallback = listOf(
            EpgEntry("Ana Haber", 1_000, 2_000, "Günün özeti"),
        )
        val shown = visibleGuideProgrammes(emptyList(), fallback, nowSec = 1_500)
        assertEquals(1, shown.size)
        assertEquals("Ana Haber", shown[0].title)
        assertEquals("Günün özeti", shown[0].description)
    }
}
