package dev.iptv.player.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {
    @Test fun masterPicksHighestBandwidth() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,CODECS="avc1.4d401f,mp4a.40.2"
            /hi/index.m3u8
        """.trimIndent()
        val p = HlsPlaylist.parse(text, "http://example.test/live/master.m3u8")
        assertEquals(2, p.variants.size)
        assertEquals("http://example.test/hi/index.m3u8", p.bestVariant()!!.url)
        assertEquals("http://example.test/live/low/index.m3u8", p.variants[0].url)
    }

    @Test fun mediaPlaylistSequenceAndRelativeUrls() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF:6.0,
            a.ts
            #EXTINF:5.5,title
            http://cdn.test/b.ts
        """.trimIndent()
        val p = HlsPlaylist.parse(text, "http://example.test/live/index.m3u8")
        assertEquals(6, p.targetDurationSec)
        assertEquals(listOf(100L, 101L), p.segments.map { it.seq })
        assertEquals("http://example.test/live/a.ts", p.segments[0].url)
        assertEquals("http://cdn.test/b.ts", p.segments[1].url)
        assertEquals(5.5, p.segments[1].durationSec, 0.001)
        assertFalse(p.endList)
        assertFalse(p.encrypted)
        assertNull(p.initUrl)
    }

    @Test fun detectsEncryptionInitAndEnd() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="k.key"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4,
            s.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val p = HlsPlaylist.parse(text, "http://example.test/v/index.m3u8")
        assertTrue(p.encrypted)
        assertTrue(p.endList)
        assertEquals("http://example.test/v/init.mp4", p.initUrl)
        assertFalse(HlsPlaylist.parse("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:4,\ns.ts", "http://x.test/a.m3u8").encrypted)
    }
}
