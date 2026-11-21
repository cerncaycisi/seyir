package dev.iptv.player.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCleanerTest {
    private fun clean(raw: String) = TitleCleaner.clean(raw)

    @Test
    fun stripsBracketTagsAndExtractsYear() {
        assertEquals(CleanTitle("Inception", "2010"), clean("Inception (2010) [4K] TR"))
    }

    @Test
    fun stripsSquareCurlyAndRoundProviderTags() {
        assertEquals(CleanTitle("Tulsa King", null), clean("Tulsa King [HD] {provider} (Dublaj)"))
        assertEquals(CleanTitle("Tulsa King", "2022"), clean("Tulsa King [2022] {HD}"))
    }

    @Test
    fun stripsLanguagePrefixAndDubTag() {
        assertEquals(CleanTitle("The Matrix", null), clean("TR - The Matrix [Dublaj] FHD"))
    }

    @Test
    fun handlesDottedReleaseNames() {
        assertEquals(CleanTitle("Film Adi", "2019"), clean("Film.Adi.2019.1080p.BluRay.x264"))
    }

    @Test
    fun readsTrailingYear() {
        assertEquals(CleanTitle("Gladiator", "2000"), clean("Gladiator 2000"))
    }

    @Test
    fun stripsPipeTagsAndQualityWords() {
        assertEquals(CleanTitle("Yüzüklerin Efendisi", null), clean("|TR| Yüzüklerin Efendisi UHD Türkçe Dublaj"))
    }

    @Test
    fun keepsPlainTitlesUntouched() {
        assertEquals(CleanTitle("Mr. Robot", null), clean("Mr. Robot"))
        assertNull(clean("Parasite").year)
    }

    @Test
    fun doesNotTurnTitleIntoYear() {
        assertEquals(CleanTitle("1917", null), clean("1917"))
    }

    @Test
    fun neverReturnsEmpty() {
        assertEquals("4K", clean("4K").title)
    }
}
