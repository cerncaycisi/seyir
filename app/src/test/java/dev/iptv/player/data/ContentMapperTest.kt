package dev.iptv.player.data

import dev.iptv.player.data.db.ContentMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class ContentMapperTest {
    private val codec = AesGcmCodec(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test
    fun xtreamUrlsStoreNoCredentialsAndRoundTrip() {
        val mapper = ContentMapper(PlaylistSource.Xtream("http://s:80", "user", "pw123"), codec)
        val stream = Stream("5", "Kanal", null, "http://s:80/live/user/pw123/5.ts", "1", ContentType.LIVE, catchup = "xc", catchupDays = 3)
        val row = mapper.toEntity("p", stream, 0)
        assertFalse(row.url.contains("pw123"))
        assertFalse(row.url.contains("user"))
        assertEquals(stream, mapper.toStream(row))
    }

    @Test
    fun m3uUrlsAreEncryptedAndRoundTrip() {
        val mapper = ContentMapper(PlaylistSource.M3u("http://x/l.m3u"), codec)
        val stream = Stream(
            "0", "A", "http://logo", "http://h/live/a.ts?token=SECRET", "Spor", ContentType.LIVE, "epg",
            headers = mapOf("User-Agent" to "UA", "Referer" to "http://r/"), catchup = "append", catchupDays = 2, catchupSource = "?t={utc}",
        )
        val row = mapper.toEntity("p", stream, 7)
        assertTrue(Codec.isEncrypted(row.url))
        assertFalse(row.url.contains("SECRET"))
        assertTrue(Codec.isEncrypted(row.headers!!))
        assertEquals(7, row.ord)
        assertEquals(stream, mapper.toStream(row))
    }
}
