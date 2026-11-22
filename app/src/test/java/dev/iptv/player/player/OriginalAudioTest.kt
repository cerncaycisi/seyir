package dev.iptv.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalAudioTest {
    @Test
    fun normalizesIsoCodes() {
        assertEquals("tr", normalizeLanguage("tur"))
        assertEquals("en", normalizeLanguage("eng"))
        assertEquals("de", normalizeLanguage("ger"))
        assertEquals("en", normalizeLanguage("en-US"))
        assertNull(normalizeLanguage("und"))
        assertNull(normalizeLanguage(null))
    }

    @Test
    fun tmdbOriginalLanguageWins() {
        // Turkish dub first, then French and English; the film is French.
        assertEquals(1, originalAudioPick(listOf("tur", "fre", "eng"), "fr"))
        // Turkish production with an English dub: keep Turkish.
        assertEquals(1, originalAudioPick(listOf("eng", "tr"), "tr"))
        assertEquals(1, originalAudioPick(listOf("tr", "en"), "en"))
    }

    @Test
    fun originalMissingFromTracksPrefersTheNonDub() {
        // Japanese film offered as TR dub + EN: English is closer to the original.
        assertEquals(1, originalAudioPick(listOf("tr", "en"), "ja"))
        // Turkish original without a Turkish track: leave the stream default.
        assertNull(originalAudioPick(listOf("en", "de"), "tr"))
    }

    @Test
    fun fullSubtitleBeatsForced() {
        // Real-TV regression (The Last of Us S1E1): EN, EN, TR, TR forced → the full Turkish track.
        assertEquals(2, subtitlePick(listOf("en" to false, "en" to false, "tr" to false, "tr" to true), "tr"))
        assertEquals(1, subtitlePick(listOf("tr" to true, "tur" to false), "tr"))
        assertEquals(0, subtitlePick(listOf("tr" to true, "en" to false), "tr"))
        assertNull(subtitlePick(listOf("en" to false), "tr"))
        assertNull(subtitlePick(listOf("tr" to false), ""))
    }

    @Test
    fun unknownOriginalKeepsStreamDefault() {
        // Real-TV regression: a Turkish documentary unknown to TMDB must not switch to its English dub.
        assertNull(originalAudioPick(listOf("tr", "en"), null))
        assertNull(originalAudioPick(listOf("tr"), "en"))
        assertNull(originalAudioPick(listOf(null, null), "en"))
        assertNull(originalAudioPick(emptyList(), "en"))
    }
}
