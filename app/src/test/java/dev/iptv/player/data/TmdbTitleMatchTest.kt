package dev.iptv.player.data

import dev.iptv.player.data.tmdb.sameTitle
import org.junit.Assert.*
import org.junit.Test

class TmdbTitleMatchTest {
    @Test fun punctuationAndTurkishCharactersDoNotBlockMatching() {
        assertTrue(sameTitle("LEGO Jurassic World: Legend of Isla Nublar", "Lego Jurassic World Legend of Isla Nublar"))
        assertTrue(sameTitle("Kızılcık Şerbeti", "Kizilcik Serbeti"))
    }

    @Test fun searchResultsCannotSupplyAnUnrelatedTitlesRating() {
        assertFalse(sameTitle("Jurassic World", "Jurassic World: Fallen Kingdom"))
        assertFalse(sameTitle("Film", null))
    }
}
