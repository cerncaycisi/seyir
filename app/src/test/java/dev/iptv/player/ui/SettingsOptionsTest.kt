package dev.iptv.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsOptionsTest {

    @Test
    fun `presetIntOptions keeps sorted presets without duplicates`() {
        val options = presetIntOptions(listOf(0, 6, 12, 24), 12) { "$it h" }
        assertEquals(listOf(0 to "0 h", 6 to "6 h", 12 to "12 h", 24 to "24 h"), options)
    }

    @Test
    fun `presetIntOptions inserts legacy custom value in sorted order`() {
        val options = presetIntOptions(listOf(5, 10, 15, 30), 22) { "$it s" }
        assertEquals(
            listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 22 to "22 s", 30 to "30 s"),
            options,
        )
    }
}
