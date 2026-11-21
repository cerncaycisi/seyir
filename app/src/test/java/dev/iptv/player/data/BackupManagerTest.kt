package dev.iptv.player.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    private val xtream = Playlist("a", "Ev", PlaylistSource.Xtream("http://s:80", "user", "secret"))
    private val m3u = Playlist("b", "Liste", PlaylistSource.M3u("http://x/list.m3u?token=abc"))

    @Test
    fun jsonRoundTripKeepsEverything() {
        val data = BackupData(
            exportedAt = 123,
            playlists = listOf(xtream, m3u),
            favorites = listOf("a|LIVE|1"),
            positions = mapOf("a|MOVIE|9" to 4500L),
            settings = BackupManager.settingsToBackup(
                mutablePreferencesOf(
                    intPreferencesKey("auto_refresh_hours") to 12,
                    booleanPreferencesKey("clock_24h") to false,
                    stringPreferencesKey("language") to "tr",
                    stringSetPreferencesKey("hidden_channels") to setOf("x", "y"),
                ),
            ),
        )
        val json = BackupManager.toJson(data)
        assertEquals(data, BackupManager.fromJson(json))
        assertTrue(json.contains("secret"))
    }

    @Test
    fun settingsApplyBackToPreferences() {
        val items = BackupManager.settingsToBackup(
            mutablePreferencesOf(
                intPreferencesKey("n") to 5,
                stringSetPreferencesKey("s") to setOf("a"),
                booleanPreferencesKey("b") to true,
            ),
        )
        val target = mutablePreferencesOf(intPreferencesKey("n") to 1)
        val applied = BackupManager.applySettings(target, items + BackupSetting("bad", "int", JsonPrimitive("nope")))
        assertEquals(3, applied)
        assertEquals(5, target[intPreferencesKey("n")])
        assertEquals(setOf("a"), target[stringSetPreferencesKey("s")])
        assertEquals(true, target[booleanPreferencesKey("b")])
    }

    @Test
    fun mergeReplacesByIdAndAppendsNew() {
        val renamed = xtream.copy(name = "Yeni ad")
        val other = Playlist("c", "Başka", PlaylistSource.M3u("http://o/1.m3u"))
        val merged = BackupManager.mergePlaylists(listOf(xtream, m3u), listOf(renamed, other))
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("Yeni ad", merged[0].name)
        assertEquals(m3u, merged[1])
    }

    @Test
    fun invalidEntriesAreDropped() {
        val json = """{"version":1,"playlists":[{"id":"","name":"x","source":{"type":"m3u","url":"http://a"}},
            {"id":"ok","name":"y","source":{"type":"m3u","url":"  "}}],"unknown":1}"""
        assertTrue(BackupManager.fromJson(json).playlists.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageIsRejected() {
        BackupManager.fromJson("not json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureVersionIsRejected() {
        BackupManager.fromJson("""{"version":99}""")
    }
}
