package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    private val sample = """
        #EXTM3U x-tvg-url="http://epg.example/epg.xml"
        #EXTINF:-1 tvg-id="trt1.tr" tvg-name="TRT 1" tvg-logo="http://logo/trt1.png" group-title="Ulusal",TRT 1 HD
        http://example.com/live/u/p/1.ts
        #EXTINF:-1 group-title="Film",Yüzüklerin Efendisi, Kralın Dönüşü
        #EXTVLCOPT:http-user-agent=Test
        http://example.com/movie/u/p/2.mkv
        #EXTINF:-1,Bölüm 1
        http://example.com/series/u/p/3.mp4

        http://example.com/bare-stream.m3u8
    """.trimIndent()

    @Test
    fun parsesAttributesTitlesAndTypes() {
        val items = M3uParser.parse(sample)
        assertEquals(4, items.size)

        val live = items[0]
        assertEquals("TRT 1 HD", live.name)
        assertEquals("http://logo/trt1.png", live.logo)
        assertEquals("Ulusal", live.categoryId)
        assertEquals("trt1.tr", live.epgId)
        assertEquals(ContentType.LIVE, live.type)

        assertEquals("Yüzüklerin Efendisi, Kralın Dönüşü", items[1].name)
        assertEquals(ContentType.MOVIE, items[1].type)

        assertEquals(ContentType.SERIES, items[2].type)
        assertEquals("Diğer", items[2].categoryId)
        assertNull(items[2].logo)

        assertEquals("http://example.com/bare-stream.m3u8", items[3].name)
        assertEquals(ContentType.LIVE, items[3].type)
    }
}
