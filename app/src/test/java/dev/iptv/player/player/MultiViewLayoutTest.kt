package dev.iptv.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiViewLayoutTest {
    @Test fun tileCountOnlyTwoOrFour() {
        assertEquals(2, MultiViewLayout.tileCount(1))
        assertEquals(2, MultiViewLayout.tileCount(2))
        assertEquals(4, MultiViewLayout.tileCount(3))
        assertEquals(4, MultiViewLayout.tileCount(4))
    }

    @Test fun gridShape() {
        assertEquals(2, MultiViewLayout.columns(2)); assertEquals(1, MultiViewLayout.rows(2))
        assertEquals(2, MultiViewLayout.columns(4)); assertEquals(2, MultiViewLayout.rows(4))
        assertEquals(2, MultiViewLayout.rows(3))
    }

    @Test fun neighborMovesInsideGrid() {
        // 2x2: 0 1 / 2 3
        assertEquals(1, MultiViewLayout.neighbor(0, 1, 0, 4))
        assertEquals(2, MultiViewLayout.neighbor(0, 0, 1, 4))
        assertEquals(0, MultiViewLayout.neighbor(0, -1, 0, 4))
        assertEquals(0, MultiViewLayout.neighbor(0, 0, -1, 4))
        assertEquals(3, MultiViewLayout.neighbor(1, 0, 1, 4))
        assertEquals(3, MultiViewLayout.neighbor(3, 1, 0, 4))
        // 1x2: vertical moves do nothing
        assertEquals(1, MultiViewLayout.neighbor(0, 1, 0, 2))
        assertEquals(0, MultiViewLayout.neighbor(0, 0, 1, 2))
        // 3 tiles: bottom-right cell is empty
        assertEquals(2, MultiViewLayout.neighbor(2, 1, 0, 3))
        assertEquals(1, MultiViewLayout.neighbor(1, 0, 1, 3))
    }

    @Test fun initialChannelsWrapAndStayDistinct() {
        assertEquals(listOf(3, 4, 0, 1), MultiViewLayout.initialChannels(3, 5, 4))
        assertEquals(listOf(0, 1), MultiViewLayout.initialChannels(0, 2, 4))
        assertEquals(listOf(1, 0), MultiViewLayout.initialChannels(-1, 2, 2))
        assertEquals(emptyList<Int>(), MultiViewLayout.initialChannels(0, 0, 4))
    }

    @Test fun onlyFocusedTileHasAudio() {
        assertEquals(1f, MultiViewLayout.volume(2, 2))
        assertEquals(0f, MultiViewLayout.volume(0, 2))
    }

    @Test fun smallerTilesForMoreStreams() {
        assertTrue(MultiViewLayout.maxVideoHeight(4) < MultiViewLayout.maxVideoHeight(2))
        assertFalse(MultiViewLayout.maxVideoWidth(4) >= 1280)
    }
}
