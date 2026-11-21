package dev.iptv.player.data.tmdb

import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Stream
import org.junit.Assert.*
import org.junit.Test

class CatalogSyncPlanTest {
    @Test fun unchanged5608TitlesNeedNoLookupsEvenAfterReordering() {
        val catalog = (1..5608).associate { "$it" to "series $it|2024" }
        val plan = catalogSyncPlan(catalog, catalog.entries.reversed().associate { it.toPair() })
        assertTrue(plan.changed.isEmpty())
        assertTrue(plan.removed.isEmpty())
    }

    @Test fun onlyNewOrChangedIdentitiesNeedLookup() {
        val plan = catalogSyncPlan(mapOf("1" to "a|2020", "2" to "b|2021", "3" to "c|2022"),
            mapOf("1" to "a|2020", "2" to "b|2024", "4" to "d|2023"))
        assertEquals(setOf("2", "4"), plan.changed)
        assertEquals(setOf("3"), plan.removed)
    }

    @Test fun streamUrlAndProviderRatingDoNotInvalidateMetadata() {
        val stream = Stream("1", "Tulsa King (2022)", null, "old", "1", ContentType.SERIES)
        assertEquals(metadataIdentity(stream), metadataIdentity(stream.copy(url = "new", rating = "9.1", categoryId = "2")))
        assertNotEquals(metadataIdentity(stream), metadataIdentity(stream.copy(name = "Tulsa King (2024)")))
    }
}
