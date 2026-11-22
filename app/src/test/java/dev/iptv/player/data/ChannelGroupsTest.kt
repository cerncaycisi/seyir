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

    @Test fun renameAndMoveGroups() {
        var list = ChannelGroups.create(base, "g2", "Spor", "p1|LIVE|2")
        list = ChannelGroups.create(list, "g3", "Sinema", "p1|LIVE|3")
        list = ChannelGroups.rename(list, "g2", "  Spor HD ")
        assertEquals("Spor HD", list[1].name)

        // Move g3 up, then up again to the top; moving above 0 is a no-op.
        list = ChannelGroups.move(list, "g3", -1)
        assertEquals(listOf("g1", "g3", "g2"), list.map { it.id })
        list = ChannelGroups.move(list, "g3", -5)
        assertEquals(listOf("g3", "g1", "g2"), list.map { it.id })
        list = ChannelGroups.move(list, "g3", 1)
        assertEquals(listOf("g1", "g3", "g2"), list.map { it.id })
    }

    @Test fun mergeUnionsExistingAndAppendsNew() {
        val existing = listOf(
            ChannelGroup("g1", "Haber", listOf("p1|LIVE|1", "p1|LIVE|2")),
            ChannelGroup("g2", "Spor", listOf("p1|LIVE|10")),
        )
        val incoming = listOf(
            ChannelGroup("g1", "Haberler", listOf("p1|LIVE|2", "p1|LIVE|3")),
            ChannelGroup("g99", "spor", listOf("p1|LIVE|11")),
            ChannelGroup("g3", "Belgesel", listOf("p1|LIVE|20")),
        )
        val merged = ChannelGroups.merge(existing, incoming)
        assertEquals(3, merged.size)
        assertEquals(ChannelGroup("g1", "Haberler", listOf("p1|LIVE|1", "p1|LIVE|2", "p1|LIVE|3")), merged[0])
        assertEquals(ChannelGroup("g2", "spor", listOf("p1|LIVE|10", "p1|LIVE|11")), merged[1])
        assertEquals(ChannelGroup("g3", "Belgesel", listOf("p1|LIVE|20")), merged[2])
    }
}
