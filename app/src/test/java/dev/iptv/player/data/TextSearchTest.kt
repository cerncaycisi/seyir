package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    @Test
    fun normalizeFoldsTurkishLetters() {
        assertEquals("istanbul", TextSearch.normalize("İSTANBUL"))
        assertEquals("istanbul", TextSearch.normalize("Istanbul"))
        assertEquals("istanbul", TextSearch.normalize("ıstanbul"))
        assertEquals("sagligi gucu ozet cay", TextSearch.normalize("Sağlığı Gücü ÖZET Çay"))
        assertEquals("sasirdim", TextSearch.normalize("ŞAŞIRDIM"))
    }

    @Test
    fun normalizeStripsDiacriticsAndWhitespace() {
        assertEquals("cafe creme", TextSearch.normalize("  Café   Crème "))
        assertEquals("", TextSearch.normalize("   "))
    }

    @Test
    fun matchesIgnoresCaseAndTurkishVariants() {
        assertTrue(TextSearch.matches("Yüzüklerin Efendisi", "yuzuk"))
        assertTrue(TextSearch.matches("YÜZÜKLERİN EFENDİSİ", "yüzük"))
        assertTrue(TextSearch.matches("Çocuk Kanalı", "COCUK"))
        assertTrue(TextSearch.matches("ISPARTA Haber", "ısparta"))
        assertTrue(TextSearch.matches("Ispartalı", "ispartali"))
        assertFalse(TextSearch.matches("TRT 1", "trt 2"))
        assertTrue(TextSearch.matches("Anything", ""))
    }
}
