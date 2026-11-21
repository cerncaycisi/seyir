package dev.iptv.player.epg

import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.util.TimeZone

class EpgTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tv SYSTEM "xmltv.dtd">
<tv>
  <channel id="trt1.tr"><display-name>TRT 1 HD</display-name><display-name>TRT1</display-name></channel>
  <programme start="20240101120000 +0300" stop="20240101130000 +0300" channel="trt1.tr">
    <title lang="tr">Haber &amp; Spor</title><desc>Açıklama</desc><category>Haber</category>
  </programme>
  <programme start="20240101130000" stop="20240101133000" channel="trt1.tr"><title>Film</title></programme>
  <programme start="bozuk" channel="x"><title>Yok</title></programme>
</tv>"""

    @Test
    fun parsesChannelsAndProgrammes() {
        val channels = mutableListOf<XmltvChannel>()
        val progs = mutableListOf<Programme>()
        XmltvParser.parse(KXmlParser(), xml.byteInputStream(), { channels += it }, { progs += it })
        assertEquals(listOf("TRT 1 HD", "TRT1"), channels.single().names)
        assertEquals(2, progs.size)
        assertEquals("Haber & Spor", progs[0].title)
        assertEquals("Haber", progs[0].category)
        // 12:00 +0300 = 09:00 UTC
        assertEquals(1704099600L, progs[0].start)
        assertEquals(3600L, progs[0].stop - progs[0].start)
        assertEquals(1704114000L, progs[1].start)
    }

    @Test
    fun parsesTimeOffsets() {
        val utc = XmltvParser.parseTime("20240101090000 +0000")!!
        assertEquals(utc, XmltvParser.parseTime("20240101120000 +0300"))
        assertEquals(utc, XmltvParser.parseTime("20240101040000 -0500"))
        assertEquals(utc, XmltvParser.parseTime("20240101090000"))
        assertEquals(utc, XmltvParser.parseTime("202401010900"))
        assertNull(XmltvParser.parseTime("xx"))
    }

    @Test
    fun matchesByIdThenName() {
        val idx = ChannelIndex(listOf(EpgChannel("trt1.tr", "TRT 1 HD", "TRT 1 HD\nTRT1"), EpgChannel("kanald", "Kanal D")))
        assertEquals("kanald", idx.match("KanalD", "whatever"))
        assertEquals("trt1.tr", idx.match(null, "TR: TRT 1 FHD"))
        assertEquals("kanald", idx.match("", "Kanal D HD"))
        assertNull(idx.match(null, "Bilinmeyen"))
        assertEquals(listOf("trt1.tr"), idx.search("trt").map { it.id })
        assertEquals("sebnemsener", ChannelMatcher.normalize("Şebnem Şener"))
    }

    @Test
    fun timeshiftUrl() {
        val url = Catchup.xtreamTimeshiftUrl("host:8080/", "u", "p", "101", 1704099600L, 60, TimeZone.getTimeZone("GMT"))
        assertEquals("http://host:8080/timeshift/u/p/60/2024-01-01:09-00/101.ts", url)
    }

    @Test
    fun catchupTemplates() {
        val s = 1704099600L
        assertEquals("http://x/a?s=1704099600&e=1704103200&d=60&n=1704110000", Catchup.expandTemplate("http://x/a?s={utc}&e=\${end}&d={duration:60}&n={lutc}", s, s + 3600, 1704110000))
        assertEquals("2024/01/01 09:00", Catchup.expandTemplate("{Y}/{m}/{d} {H}:{M}", s, s + 3600, s))
        val spec = CatchupSpec("append", "?utc={utc}&lutc={lutc}")
        assertEquals("http://h/live.ts?utc=$s&lutc=5", Catchup.m3uUrl("http://h/live.ts", spec, s, s + 60, 5))
        assertEquals("http://h/l.ts?utc=$s&lutc=5", Catchup.m3uUrl("http://h/l.ts", CatchupSpec("shift", null), s, s + 60, 5))
        assertTrue(Stream("1", "n", null, "u", "c", ContentType.LIVE).archiveDays == 0)
    }
}
