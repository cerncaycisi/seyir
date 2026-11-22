package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelGroupsTest {
    private val base = ChannelGroups.create(emptyList(), "g1", "  Haber ", "p1|LIVE|1")

    @Test fun createTrimsAndAddsFirstChannel() = assertEquals(listOf(ChannelGroup("g1", "Haber", listOf("p1|LIVE|1"))), base)
    @Test fun blankNameIsIgnored() = assertEquals(base, ChannelGroups.create(base, "g2", "   ", null))
    @Test fun toggleAddsThenRemovesKeepingOrder() {
        val added = ChannelGroups.toggle(base, "g1", "p1|LIVE|2")
        assertEquals(listOf("p1|LIVE|1", "p1|LIVE|2"), added.single().keys)
        assertEquals(listOf("p1|LIVE|2"), ChannelGroups.toggle(added, "g1", "p1|LIVE|1").single().keys)
    }
    @Test fun deleteAndPlaylistRemoval() {
        val two = ChannelGroups.toggle(base, "g1", "p2|LIVE|9")
        assertEquals(listOf("p2|LIVE|9"), ChannelGroups.withoutPlaylist(two, "p1").single().keys)
        assertEquals(emptyList<ChannelGroup>(), ChannelGroups.delete(two, "g1"))
    }
}
