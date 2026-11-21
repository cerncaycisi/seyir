package dev.iptv.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import dev.iptv.player.data.M3uParser
import dev.iptv.player.data.ParentalFilter
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.tmdb.CleanTitle
import dev.iptv.player.data.tmdb.TitleCleaner
import dev.iptv.player.epg.Catchup
import dev.iptv.player.player.cleanSubtitleQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executes production regex initializers on Android's ICU engine. Host JVM tests accepted an
 * unescaped closing brace that crashed both TMDB title enrichment and catch-up on actual TVs.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AndroidRegexRegressionTest {
    @Test
    fun titleCleanerInitializesAndExtractsSeriesYear() {
        assertEquals(CleanTitle("Tulsa King", "2022"), TitleCleaner.clean("Tulsa King (2022)"))
    }

    @Test
    fun titleCleanerRemovesCurlySquareAndRoundProviderTags() {
        assertEquals(CleanTitle("Tulsa King", "2022"), TitleCleaner.clean("TR - Tulsa King [2022] {4K} (Dublaj)"))
        assertEquals(CleanTitle("Film Adi", "2019"), TitleCleaner.clean("Film.Adi.2019.1080p.BluRay.x264"))
    }

    @Test
    fun catchupExpandsBraceAndDollarTokensOnAndroid() {
        val start = 1_704_099_600L
        val template = "https://example.invalid/archive?start={utc}&end=\${end}&minutes={duration:60}&unknown={provider}"
        assertEquals(
            "https://example.invalid/archive?start=1704099600&end=1704103200&minutes=60&unknown={provider}",
            Catchup.expandTemplate(template, start, start + 3_600, start + 7_200),
        )
        assertEquals("2024/01/01 09:00", Catchup.expandTemplate("{Y}/{m}/{d} {H}:{M}", start, start + 3_600, start))
    }

    @Test
    fun subtitleQueryCleansYearAndEpisodeTagsOnAndroid() {
        assertEquals("Film Adi" to "2019", cleanSubtitleQuery("TR: Film Adi (2019) 1080p"))
        assertEquals("Some Show" to null, cleanSubtitleQuery("Some.Show.S01E02.WEB-DL"))
    }

    @Test
    fun parentalFilterHonorsUnicodeWordBoundaries() {
        assertTrue(ParentalFilter.isAdultName("Yetişkin HD"))
        assertTrue(ParentalFilter.isAdultName("Filmler (18+)"))
        assertFalse(ParentalFilter.isAdultName("Çocuk ve Aile"))
        assertFalse(ParentalFilter.isAdultName("Adulting"))
    }

    @Test
    fun m3uBomAttributesAndCatchupWorkTogetherOnAndroid() {
        val document = M3uParser.parseDocument(
            "\uFEFF" + """
                #EXTM3U url-tvg="https://example.invalid/epg.xml"
                #EXTINF:-1 tvg-id="news.tr" group-title="Haber" catchup="append" catchup-days="7" catchup-source="?utc={utc}&lutc={lutc}",Haber TV
                #EXTVLCOPT:http-user-agent=RegexTest/1.0
                https://example.invalid/live.ts
            """.trimIndent(),
        )
        val stream = document.streams.single()
        val playlist = Playlist("test", "Test", PlaylistSource.M3u("https://example.invalid/list.m3u"))
        assertEquals("https://example.invalid/epg.xml", document.epgUrl)
        assertEquals("Haber TV", stream.name)
        assertEquals("news.tr", stream.epgId)
        assertEquals("Haber", stream.categoryId)
        assertEquals("RegexTest/1.0", stream.headers["User-Agent"])
        assertTrue(Catchup.available(playlist, stream))
        assertEquals("https://example.invalid/live.ts?utc=100&lutc=300", Catchup.urlFor(playlist, stream, 100, 200, 300))
    }
}
