package dev.iptv.player.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbLogoPickTest {

    @Test
    fun `prefers Turkish raster logo over English and neutral`() {
        val url = pickTmdbLogo(
            listOf(
                TmdbLogoCandidate("/en_best.png", "en", 9.5, 800, 2.5),
                TmdbLogoCandidate("/neutral.png", null, 8.0, 900, 2.4),
                TmdbLogoCandidate("/tr_logo.png", "tr", 5.0, 400, 2.1),
            ),
        )
        assertEquals("${Tmdb.IMAGE_BASE}/w500/tr_logo.png", url)
    }

    @Test
    fun `skips svg and falls back to English then neutral`() {
        val url = pickTmdbLogo(
            listOf(
                TmdbLogoCandidate("/tr_vec.svg", "tr", 10.0, 1000, 3.0),
                TmdbLogoCandidate("/neutral.png", "", 7.0, 600, 2.0),
                TmdbLogoCandidate("/en_high.png", "en", 8.2, 500, 2.2),
            ),
        )
        assertEquals("${Tmdb.IMAGE_BASE}/w500/en_high.png", url)
    }

    @Test
    fun `prefers wide horizontal logos and breaks ties by vote then width`() {
        val url = pickTmdbLogo(
            listOf(
                TmdbLogoCandidate("/square.png", "en", 9.9, 1000, 1.0),
                TmdbLogoCandidate("/wide_low.png", "en", 6.0, 900, 2.5),
                TmdbLogoCandidate("/wide_top.png", "en", 8.5, 600, 2.1),
                TmdbLogoCandidate("/wide_top_wider.png", "en", 8.5, 850, 2.1),
            ),
        )
        assertEquals("${Tmdb.IMAGE_BASE}/w500/wide_top_wider.png", url)
    }

    @Test
    fun `returns null when empty or only svg`() {
        assertNull(pickTmdbLogo(emptyList()))
        assertNull(pickTmdbLogo(listOf(TmdbLogoCandidate("/only.svg", "tr", 9.0, 500, 2.0))))
    }
}
