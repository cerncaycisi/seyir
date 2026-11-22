package dev.iptv.player.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeGuardTest {

    @Test
    fun `unarmed click requires confirmation`() {
        assertFalse(isEpisodeJumpConfirmed(armedAtMs = null, nowMs = 1_000L))
    }

    @Test
    fun `second click within confirmation window confirms`() {
        assertTrue(isEpisodeJumpConfirmed(armedAtMs = 10_000L, nowMs = 12_500L))
        assertTrue(isEpisodeJumpConfirmed(armedAtMs = 10_000L, nowMs = 14_000L))
    }

    @Test
    fun `expired or skewed timestamps do not confirm`() {
        assertFalse(isEpisodeJumpConfirmed(armedAtMs = 10_000L, nowMs = 14_001L))
        assertFalse(isEpisodeJumpConfirmed(armedAtMs = 10_000L, nowMs = 9_999L))
    }
}
