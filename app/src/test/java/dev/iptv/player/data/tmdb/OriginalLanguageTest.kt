package dev.iptv.player.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalLanguageTest {
    @Test
    fun spokenLanguagesCorrectWrongOriginal() {
        // TMDB 957152: Turkish documentary listed with original_language "en", spoken ["tr"].
        assertEquals("tr", pickOriginalLanguage("en", listOf("tr")))
        assertEquals("en", pickOriginalLanguage("en", listOf("en", "fr")))
        assertEquals("fr", pickOriginalLanguage("fr", emptyList()))
        assertEquals("de", pickOriginalLanguage(null, listOf("de")))
        assertNull(pickOriginalLanguage(null, emptyList()))
    }
}
