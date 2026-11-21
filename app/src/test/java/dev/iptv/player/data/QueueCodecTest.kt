package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator

class QueueCodecTest {
    private val codec = AesGcmCodec(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    private val playlist = Playlist("p", "Ev", PlaylistSource.Xtream("http://s", "u", "pw"))

    private fun stream(i: Int) = Stream("$i", "Kanal $i", null, "http://s/live/u/pw/$i.ts", "1", ContentType.LIVE, headers = mapOf("User-Agent" to "x"))

    @Test
    fun roundTrip() {
        val items = (0 until 5).map(::stream)
        val text = QueueCodec.encode(playlist, items, 3, codec)
        assertFalse(text.contains("pw"))
        val (pl, restored, index) = QueueCodec.decode(text, codec)!!
        assertEquals(playlist, pl)
        assertEquals(items, restored)
        assertEquals(3, index)
    }

    @Test
    fun longQueuesAreWindowedAroundCurrentItem() {
        val items = (0 until 5000).map(::stream)
        val (_, restored, index) = QueueCodec.decode(QueueCodec.encode(playlist, items, 4000, codec), codec)!!
        assertEquals(QueueCodec.MAX_ITEMS, restored.size)
        assertEquals("4000", restored[index].id)
    }

    @Test
    fun corruptDataYieldsNull() {
        assertNull(QueueCodec.decode("garbage", codec))
        assertNull(QueueCodec.decode("enc:v1:00ff", codec))
        assertNotNull(QueueCodec.decode(QueueCodec.encode(playlist, listOf(stream(0)), 0, codec), codec))
    }
}
