package dev.iptv.player.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RecordingMathTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun slugFoldsTurkishAndSymbols() {
        assertEquals("Sampiyonlar_Ligi_Canli", RecordingMath.slug("Şampiyonlar Ligi: Canlı!"))
        assertEquals("", RecordingMath.slug("***"))
        assertEquals(5, RecordingMath.slug("abcdefghij", 5).length)
    }

    @Test fun baseNameHasStampAndSkipsBlankTitle() {
        // 2026-11-21T20:30:00Z
        val start = 1_795_293_000_000L
        assertEquals("TRT_1_Haberler_2026-11-21_20-30", RecordingMath.baseName("TRT 1", "Haberler", start, utc))
        assertEquals("TRT_1_2026-11-21_20-30", RecordingMath.baseName("TRT 1", " ", start, utc))
        assertEquals("kanal_2026-11-21_20-30", RecordingMath.baseName("???", "", start, utc))
    }

    @Test fun uniqueFileNameAddsCounter() {
        val taken = setOf("a.ts", "a_2.ts")
        assertEquals("a_3.ts", RecordingMath.uniqueFileName("a", "ts") { it in taken })
        assertEquals("b.ts", RecordingMath.uniqueFileName("b", "ts") { it in taken })
    }

    @Test fun windowPadsTailAndRejectsOver() {
        val now = 1_000_000_000_000L
        val w = RecordingMath.window(now / 1000 + 600, now / 1000 + 4200, now)
        assertNotNull(w)
        assertEquals(now + 600_000, w!!.startMs)
        assertEquals(now + 4_200_000 + RecordingMath.PAD_AFTER_MS, w.stopMs)
        assertEquals(600_000L, RecordingMath.delayUntilStart(w, now))
        assertNull(RecordingMath.window(now / 1000 - 7200, now / 1000 - 3600, now))
        assertNull(RecordingMath.window(100, 100, now))
    }

    @Test fun windowForRunningProgrammeStartsNow() {
        val now = 1_000_000_000_000L
        val w = RecordingMath.window(now / 1000 - 600, now / 1000 + 1800, now)!!
        assertEquals(0L, RecordingMath.delayUntilStart(w, now))
    }

    @Test fun windowIsCapped() {
        val now = 1_000_000_000_000L
        val w = RecordingMath.window(now / 1000, now / 1000 + 20 * 3600, now)!!
        assertEquals(RecordingMath.MAX_RECORD_MS, w.durationMs)
    }

    @Test fun instantStop() {
        val now = 1_000_000_000_000L
        assertEquals(now + RecordingMath.DEFAULT_INSTANT_MS, RecordingMath.instantStopMs(now, null))
        assertEquals(now + RecordingMath.DEFAULT_INSTANT_MS, RecordingMath.instantStopMs(now, now / 1000 + 5))
        assertEquals(now + 1_800_000 + RecordingMath.PAD_AFTER_MS, RecordingMath.instantStopMs(now, now / 1000 + 1800))
    }

    @Test fun spaceEstimate() {
        // 1 hour at 6 Mbit/s = 2.7 GB
        assertEquals(2_700_000_000L, RecordingMath.requiredBytes(3_600_000))
        assertTrue(RecordingMath.hasSpace(10L * 1024 * 1024 * 1024, 3_600_000))
        assertFalse(RecordingMath.hasSpace(2_800_000_000L, 3_600_000))
    }

    @Test fun formatBytes() {
        assertEquals("1,5 GB", RecordingMath.formatBytes(1_610_612_736L))
        assertEquals("200 MB", RecordingMath.formatBytes(200L * 1024 * 1024))
        assertEquals("512 KB", RecordingMath.formatBytes(512L * 1024))
    }
}
