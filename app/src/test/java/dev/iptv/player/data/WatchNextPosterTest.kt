package dev.iptv.player.data

import androidx.tvprovider.media.tv.TvContractCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNextPosterTest {

    @Test
    fun `uses remote logo URL with 2_3 aspect ratio when present`() {
        val (uri, aspect) = resolveWatchNextPoster(
            logo = " https://image.tmdb.org/t/p/w500/poster.jpg ",
            packageName = "dev.iptv.player",
            fallbackDrawableId = 42,
        )
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", uri)
        assertEquals(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3, aspect)
    }

    @Test
    fun `falls back to app banner resource with 16_9 aspect ratio when logo missing or blank`() {
        val (nullUri, nullAspect) = resolveWatchNextPoster(
            logo = null,
            packageName = "dev.iptv.player",
            fallbackDrawableId = 42,
        )
        assertEquals("android.resource://dev.iptv.player/42", nullUri)
        assertEquals(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9, nullAspect)

        val (blankUri, blankAspect) = resolveWatchNextPoster(
            logo = "   ",
            packageName = "dev.iptv.player",
            fallbackDrawableId = 99,
        )
        assertEquals("android.resource://dev.iptv.player/99", blankUri)
        assertEquals(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9, blankAspect)
    }
}
