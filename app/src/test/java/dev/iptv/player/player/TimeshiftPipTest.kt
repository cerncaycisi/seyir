package dev.iptv.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftPipTest {
    @Test fun catchupOnlyAfterLongPause() {
        assertFalse(Timeshift.useCatchup(5_000, true))
        assertTrue(Timeshift.useCatchup(20_000, true))
        assertFalse(Timeshift.useCatchup(60_000, false))
    }

    @Test fun catchupRangeStartsAtPause() {
        val (s, e) = Timeshift.catchupRange(1_700_000_123_456L)
        assertEquals(1_700_000_123L, s)
        assertEquals(s + Timeshift.CATCHUP_SPAN_SEC, e)
    }

    @Test fun behindMath() {
        assertEquals(90L, Timeshift.behindSec(1_000, 800, 110_000))
        assertEquals(0L, Timeshift.behindSec(1_000, 1_000, 5_000))
        assertEquals(0L, Timeshift.behindFromLiveOffset(null, null))
        assertEquals(0L, Timeshift.behindFromLiveOffset(15_000, 20_000))
        assertEquals(40L, Timeshift.behindFromLiveOffset(60_000, 20_000))
        assertEquals(10L, Timeshift.behindFromLiveOffset(30_000, null))
    }

    @Test fun labelsAndReplayHeuristic() {
        assertEquals("Canlının 30 sn gerisinde", Timeshift.label(30))
        assertEquals("Canlının 2 dk 5 sn gerisinde", Timeshift.label(125))
        assertTrue(Timeshift.looksLikeReplay("http://h/timeshift/u/p/60/2026-01-01:10-00/1.ts"))
        assertTrue(Timeshift.looksLikeReplay("http://h/x.m3u8?utc=1&lutc=2"))
        assertFalse(Timeshift.looksLikeReplay("http://h/live/u/p/1.ts"))
    }

    @Test fun pipRatioIsClamped() {
        assertEquals(16 to 9, PipMath.ratio(0, 0))
        assertEquals(1920 * 100 to 1080 * 100, PipMath.ratio(1920, 1080))
        assertEquals(239 to 100, PipMath.ratio(4000, 1000))
        assertEquals(100 to 239, PipMath.ratio(500, 2000))
        // anamorphic 720x576 with 64:45 pixels is about 16:10 after the pixel ratio
        val (n, d) = PipMath.ratio(720, 576, 64f / 45f)
        assertTrue(n.toFloat() / d in 1.7f..1.8f)
    }
}
