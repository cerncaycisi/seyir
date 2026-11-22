package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DecodeEntitiesTest {
    private fun d(s: String) = DisplayText.decodeEntities(s)

    @Test fun plainTextUntouched() = assertEquals("Ana Haber", d("Ana Haber"))
    @Test fun named() = assertEquals("Tom & Jerry <3", d("Tom &amp; Jerry &lt;3"))
    @Test fun doubleEncoded() = assertEquals("0--->100", d("0---&amp;gt;100"))
    @Test fun numeric() = assertEquals("Kadın'ın", d("Kadın&#39;ın"))
    @Test fun lostAmpersand() = assertEquals("0--->100", d("0---gt;100"))
    @Test fun wordEndingInGtKept() = assertEquals("Budgt;x", d("Budgt;x"))
}
