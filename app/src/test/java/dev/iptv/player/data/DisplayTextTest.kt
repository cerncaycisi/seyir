package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTextTest {
    @Test
    fun placeholderEpgTitlesAreDetected() {
        assertTrue(DisplayText.isPlaceholderTitle("No Data"))
        assertTrue(DisplayText.isPlaceholderTitle(" no information. "))
        assertTrue(DisplayText.isPlaceholderTitle("-"))
        assertTrue(DisplayText.isPlaceholderTitle(null))
        assertFalse(DisplayText.isPlaceholderTitle("Gönül Dağı"))
        assertFalse(DisplayText.isPlaceholderTitle("No Country for Old Men"))
    }

    @Test
    fun seriesNameAndEpisodeCodesAreStripped() {
        assertEquals("", DisplayText.episodeTitle("KURAN-I KERIM HATM-I SERIF - S01E01 -", "KURAN-I KERIM HATM-I SERIF"))
        assertEquals("Pilot", DisplayText.episodeTitle("Tulsa King - S01E01 - Pilot", "Tulsa King"))
        assertEquals("Yeni Sezon", DisplayText.episodeTitle("Yeni Sezon", "Test Dizisi"))
        assertEquals("İlk Bölüm", DisplayText.episodeTitle("TEST DİZİSİ S1E1 İlk Bölüm", "Test Dizisi"))
        assertEquals("", DisplayText.episodeTitle("Sezon 2 Bölüm 5", "Başka"))
        assertEquals("", DisplayText.episodeTitle(null, "X"))
    }
}
