package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxTest {
    @Test fun adultNames() {
        assertTrue(ParentalFilter.isAdultName("XXX Movies"))
        assertTrue(ParentalFilter.isAdultName("Yetişkin Kanalı"))
        assertTrue(ParentalFilter.isAdultName("Night +18"))
        assertTrue(ParentalFilter.isAdultName("Adult"))
        assertFalse(ParentalFilter.isAdultName("TRT 1 HD"))
        assertFalse(ParentalFilter.isAdultName("Madulta"))
    }

    @Test fun pinHash() {
        val h = ParentalFilter.hashPin("1234")
        assertTrue(ParentalFilter.verifyPin(h, "1234"))
        assertFalse(ParentalFilter.verifyPin(h, "4321"))
        assertTrue(ParentalFilter.verifyPin("1234", "1234"))
    }

    @Test fun scopedKeys() {
        assertEquals("favorites", ProfileStore.profileScopedKey("favorites", ProfileStore.DEFAULT_ID))
        assertEquals("profile.abc.favorites", ProfileStore.profileScopedKey("favorites", "abc"))
    }

    @Test fun filtering() {
        val p = Playlist("p", "x", PlaylistSource.M3u("http://a"))
        val s1 = Stream("1", "A", null, "u", "c1", ContentType.LIVE)
        val s2 = Stream("2", "XXX B", null, "u", "c1", ContentType.LIVE)
        val s3 = Stream("3", "C", null, "u", "c2", ContentType.LIVE)
        val content = Content(listOf(Category("c1", "One"), Category("c2", "Two")), listOf(s1, s2, s3))
        val rules = ContentRules(hiddenChannels = setOf("p|LIVE|1"), hideAdult = true)
        assertEquals(listOf("3"), rules.filterBase(p, ContentType.LIVE, content).items.map { it.id })
        val locked = ContentRules(lockedCategories = setOf("p|LIVE|c2"))
        assertFalse(locked.streamAllowed(p, s3))
        assertTrue(locked.copy(unlocked = true).streamAllowed(p, s3))
    }
}
