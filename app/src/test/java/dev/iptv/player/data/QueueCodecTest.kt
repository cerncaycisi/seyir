package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
    fun processRestoreRetainsCatchupAndEpisodeSeriesIdentity() {
        val live = stream(0).copy(catchup = "xc", catchupDays = 7, archiveDays = 7)
        val episode = stream(1).copy(type = ContentType.MOVIE, seriesId = "series-42")
        val (_, restored, index) = QueueCodec.decode(QueueCodec.encode(playlist, listOf(live, episode), 1, codec), codec)!!
        assertEquals(7, restored[0].archiveDays)
        assertEquals("xc", restored[0].catchup)
        assertEquals("series-42", restored[index].seriesId)
        assertEquals(listOf(live, episode), restored)
    }

    @Test
    fun olderSnapshotsWithoutNewFieldsRemainReadable() {
        val encoded = QueueCodec.encode(playlist, listOf(stream(0)), 0, codec)
        val snapshot = Json.parseToJsonElement(codec.decrypt(encoded)).jsonObject
        val oldItems = snapshot.getValue("items").jsonArray.map { item ->
            JsonObject(item.jsonObject.filterKeys { it != "archiveDays" && it != "seriesId" })
        }
        val oldSnapshot = JsonObject(snapshot + ("items" to JsonArray(oldItems)))
        val (_, restored, _) = QueueCodec.decode(codec.encrypt(oldSnapshot.toString()), codec)!!
        assertEquals(0, restored.single().archiveDays)
        assertNull(restored.single().seriesId)
    }

    @Test
    fun corruptDataYieldsNull() {
        assertNull(QueueCodec.decode("garbage", codec))
        assertNull(QueueCodec.decode("enc:v1:00ff", codec))
        assertNotNull(QueueCodec.decode(QueueCodec.encode(playlist, listOf(stream(0)), 0, codec), codec))
    }
}
