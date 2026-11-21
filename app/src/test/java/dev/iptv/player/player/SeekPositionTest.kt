package dev.iptv.player.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPositionTest {
    @Test fun seekStopsAtBothEnds() {
        assertEquals(0L, seekPosition(3_000, -10_000, 60_000))
        assertEquals(60_000L, seekPosition(55_000, 10_000, 60_000))
        assertEquals(25_000L, seekPosition(15_000, 10_000, 60_000))
    }

    @Test fun unknownDurationCannotProduceInvalidSeek() {
        assertEquals(0L, seekPosition(0, 10_000, -1))
    }
}
