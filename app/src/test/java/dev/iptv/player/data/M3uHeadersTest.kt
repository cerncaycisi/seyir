package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uHeadersTest {
    @Test
    fun vlcOptAndAttributesBecomeHeaders() {
        val doc = M3uParser.parseDocument(
            """
            #EXTM3U url-tvg="http://epg.example/a.xml,http://epg.example/b.xml"
            #EXTINF:-1 tvg-id="a" user-agent="AttrAgent" referrer="http://attr.ref/",Kanal A
            http://example.com/live/a.ts
            #EXTINF:-1,Kanal B
            #EXTVLCOPT:http-user-agent=VlcAgent/1.0
            #EXTVLCOPT:http-referrer=http://vlc.ref/
            http://example.com/live/b.ts
            #EXTINF:-1,Kanal C
            http://example.com/live/c.ts
            """.trimIndent(),
        )
        assertEquals(3, doc.streams.size)
        assertEquals(mapOf("User-Agent" to "AttrAgent", "Referer" to "http://attr.ref/"), doc.streams[0].headers)
        assertEquals(mapOf("User-Agent" to "VlcAgent/1.0", "Referer" to "http://vlc.ref/"), doc.streams[1].headers)
        assertTrue(doc.streams[2].headers.isEmpty())
        assertEquals("http://epg.example/a.xml", doc.epgUrl)
    }

    @Test
    fun vlcOptOverridesAttribute() {
        val s = M3uParser.parse(
            """
            #EXTINF:-1 http-user-agent="Old",X
            #EXTVLCOPT:http-user-agent=New
            http://h/x.ts
            """.trimIndent(),
        ).single()
        assertEquals("New", s.headers["User-Agent"])
    }

    @Test
    fun epgUrlVariants() {
        assertEquals("http://e/x.xml", M3uParser.parseDocument("#EXTM3U x-tvg-url=\"http://e/x.xml\"\n#EXTINF:-1,A\nhttp://h/1.ts").epgUrl)
        assertEquals("http://e/y.xml", M3uParser.parseDocument("#EXTM3U url-tvg=\"http://e/y.xml\"").epgUrl)
        assertNull(M3uParser.parseDocument("#EXTM3U\n#EXTINF:-1,A\nhttp://h/1.ts").epgUrl)
        assertEquals("http://e/z.xml", M3uParser.parseDocument("﻿#EXTM3U x-tvg-url=\"http://e/z.xml\"").epgUrl)
    }

    @Test
    fun catchupAttributes() {
        val items = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 catchup="append" catchup-days="7" catchup-source="?utc={utc}&lutc={lutc}",A
            http://h/a.ts
            #EXTINF:-1 catchup="shift" timeshift="3",B
            http://h/b.ts
            #EXTINF:-1 catchup-source="http://h/{utc}.ts" catchup-days="2",C
            http://h/c.ts
            #EXTINF:-1,D
            http://h/d.ts
            """.trimIndent(),
        )
        assertEquals("append", items[0].catchup)
        assertEquals(7, items[0].catchupDays)
        assertEquals("?utc={utc}&lutc={lutc}", items[0].catchupSource)
        assertEquals("shift", items[1].catchup)
        assertEquals(3, items[1].catchupDays)
        assertNull(items[1].catchupSource)
        assertEquals("default", items[2].catchup)
        assertEquals(2, items[2].catchupDays)
        assertNull(items[3].catchup)
        assertEquals(0, items[3].catchupDays)
    }

    @Test
    fun headerLevelCatchupDefaultsApplyToEntries() {
        val s = M3uParser.parse("#EXTM3U catchup=\"flussonic\" catchup-days=\"5\"\n#EXTINF:-1,A\nhttp://h/a.ts").single()
        assertEquals("flussonic", s.catchup)
        assertEquals(5, s.catchupDays)
    }

    @Test
    fun titleWithoutCommaDoesNotCrash() {
        val s = M3uParser.parse("#EXTINF:-1 tvg-name=\"Foo\"\nhttp://h/a.ts").single()
        assertEquals("Foo", s.name)
    }
}
