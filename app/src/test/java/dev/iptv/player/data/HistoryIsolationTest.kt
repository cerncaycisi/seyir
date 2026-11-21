package dev.iptv.player.data

import org.junit.Assert.*
import org.junit.Test

class HistoryIsolationTest {
    private val playlist = Playlist("p", "P", PlaylistSource.M3u("u"))
    private fun entry(profile: String, id: String, type: ContentType = ContentType.MOVIE, series: String? = null, at: Long = 1): HistoryEntry {
        val fav = "p|$type|$id"
        return HistoryEntry(profile + fav, fav, "p", id, type, id, seriesId = series,
            positionMs = 30_000, durationMs = 300_000, updatedAt = at)
    }

    @Test fun defaultProfileDoesNotOwnNamedProfiles() {
        assertTrue(historyBelongsTo("p|MOVIE|1", ""))
        assertFalse(historyBelongsTo("profile.kids.p|MOVIE|1", ""))
        assertFalse(historyBelongsTo("profile.kids2.p|MOVIE|1", "profile.kids."))
    }

    @Test fun clearingMoviesPreservesOtherProfilesAndTypes() {
        val own = entry("", "1")
        val child = entry("profile.kids.", "1")
        val series = entry("", "2", ContentType.SERIES, "s")
        assertEquals(listOf(child, series), historyWithoutType(listOf(own, child, series), "", ContentType.MOVIE))
        assertEquals(listOf(own, series), historyWithoutType(listOf(own, child, series), "profile.kids.", ContentType.MOVIE))
    }

    @Test fun removingSeriesKeepsOtherViewersEpisodes() {
        val own = entry("", "e1", ContentType.SERIES, "s")
        val child = entry("profile.kids.", "e2", ContentType.SERIES, "s")
        val other = entry("", "e3", ContentType.SERIES, "other")
        val stream = Stream("s", "Series", null, "", "", ContentType.SERIES)
        assertEquals(listOf(child, other), historyWithoutItem(listOf(own, child, other), "", playlist, stream))
    }

    @Test fun watchNextKeepsMovieAndSeriesWithSameProviderId() {
        val movie = entry("", "42", at = 3)
        val episode = entry("", "e", ContentType.SERIES, "42", at = 2)
        val olderEpisode = entry("", "old", ContentType.SERIES, "42", at = 1)
        assertEquals(listOf(movie, episode), watchNextItems(listOf(olderEpisode, movie, episode), 20))
    }
}
