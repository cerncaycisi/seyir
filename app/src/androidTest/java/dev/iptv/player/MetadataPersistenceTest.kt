package dev.iptv.player

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.iptv.player.data.tmdb.MetadataDb
import dev.iptv.player.data.tmdb.MetadataEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MetadataPersistenceTest {
    private fun row(id: String, identity: String = "title $id|2024", playlist: String = "p", type: String = "SERIES") =
        MetadataEntry(playlist, type, id, "Title $id", "2024", identity)

    @Test fun refreshRetainsHitsAndMissesAndRejectsStaleResults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MetadataDb::class.java).build()
        try {
            val dao = db.dao()
            val initial = listOf(row("1"), row("2"), row("3"))
            dao.reconcile("p", "SERIES", initial)
            dao.resolve("p", "SERIES", "1", initial[0].identity, "{\"tmdbId\":1}")
            dao.resolve("p", "SERIES", "2", initial[1].identity, null) // Genuine no-match is durable too.
            dao.reconcile("p", "SERIES", initial.reversed())
            assertEquals(listOf("3"), dao.pending("p", "SERIES", 100).map { it.id })
            val updated = listOf(row("1", "replacement|2025"), row("2"), row("4"))
            dao.reconcile("p", "SERIES", updated)
            dao.resolve("p", "SERIES", "1", initial[0].identity, "stale")
            assertEquals(setOf("1", "4"), dao.pending("p", "SERIES", 100).map { it.id }.toSet())
            assertEquals(setOf("1", "2", "4"), dao.entries("p", "SERIES").map { it.id }.toSet())
            assertTrue(dao.entries("p", "SERIES").single { it.id == "2" }.resolved)
        } finally { db.close() }
    }

    @Test fun reopeningDatabaseKeepsResultsAndSeparatesPlaylistsAndTypes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "metadata-test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MetadataDb::class.java, name).build()
        try {
            val entry = row("1")
            db.dao().upsert(listOf(entry, row("1", playlist = "other"), row("1", type = "MOVIE")))
            db.dao().resolve("p", "SERIES", "1", entry.identity, "{\"tmdbId\":42}")
            db.close()
            db = Room.databaseBuilder(context, MetadataDb::class.java, name).build()
            assertTrue(db.dao().pending("p", "SERIES", 10).isEmpty())
            assertEquals("{\"tmdbId\":42}", db.dao().entries("p", "SERIES").single().info)
            assertEquals(1, db.dao().pending("p", "MOVIE", 10).size)
            assertEquals(1, db.dao().pending("other", "SERIES", 10).size)
            db.dao().deletePlaylist("p")
            assertEquals(1, db.dao().entries("other", "SERIES").size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
