package dev.iptv.player.data

import dev.iptv.player.data.tmdb.TmdbInfo
import org.junit.Assert.*
import org.junit.Test

class CatalogMetadataTest {
    private val stream = Stream("1", "Film", null, "", "", ContentType.MOVIE, rating = "9.9", genre = "Drama")

    @Test fun tmdbSortDoesNotUseProviderScoresForUnmatchedTitles() {
        assertNull(withCatalogMetadata(stream, null, true).rating)
        assertEquals("9.9", withCatalogMetadata(stream, null, false).rating)
    }

    @Test fun metadataSuppliesMovieGenreAndLocalizedPlot() {
        val enriched = withCatalogMetadata(stream, TmdbInfo(tmdbId = 1, rating = "7.2", genre = "Macera", plot = "Türkçe açıklama"), true)
        assertEquals("7.2", enriched.rating)
        assertEquals("Macera", enriched.genre)
        assertEquals("Türkçe açıklama", enriched.plot)
        assertEquals(stream.id, enriched.id)
    }
}
