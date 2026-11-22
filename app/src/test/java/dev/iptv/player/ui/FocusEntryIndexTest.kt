package dev.iptv.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusEntryIndexTest {

    @Test
    fun `returns -1 when list or visible window is empty`() {
        assertEquals(-1, visibleEntryIndex(listOf(0, 1), itemCount = 0, preferred = 0))
        assertEquals(-1, visibleEntryIndex(emptyList(), itemCount = 5, preferred = 0))
    }

    @Test
    fun `keeps preferred index when valid and currently visible`() {
        assertEquals(3, visibleEntryIndex(listOf(2, 3, 4), itemCount = 10, preferred = 3))
    }

    @Test
    fun `falls back to first valid visible index when preferred is stale or offscreen`() {
        assertEquals(4, visibleEntryIndex(listOf(4, 5, 6), itemCount = 10, preferred = 1))
        assertEquals(0, visibleEntryIndex(listOf(0, 1), itemCount = 2, preferred = 50))
    }

    @Test
    fun `returns -1 if every visible index is out of bounds after list shrink`() {
        assertEquals(-1, visibleEntryIndex(listOf(5, 6, 7), itemCount = 2, preferred = 6))
    }
}
