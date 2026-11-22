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
        val enriched = withCatalogMetadata(stream, TmdbInfo(tmdbId = 1, rating = "7.2", genre = "Macera", plot = "Türkçe açıklama", poster = "https://img/p.jpg", backdrop = "https://img/b.jpg", year = "2026"), true)
        assertEquals("7.2", enriched.rating)
        assertEquals("Macera", enriched.genre)
        assertEquals("Türkçe açıklama", enriched.plot)
        assertEquals("https://img/p.jpg", enriched.logo)
        assertEquals("https://img/b.jpg", enriched.backdrop)
        assertEquals("2026", enriched.year)
        assertEquals(stream.id, enriched.id)
    }

    @Test fun metadataPreservesExistingProviderPosterAndYear() {
        val withPoster = stream.copy(logo = "http://provider/poster.jpg", year = "2021")
        val enriched = withCatalogMetadata(withPoster, TmdbInfo(tmdbId = 1, poster = "https://img/tmdb.jpg", year = "2026"), false)
        assertEquals("http://provider/poster.jpg", enriched.logo)
        assertEquals("2021", enriched.year)
    }
}
