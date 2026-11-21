package dev.iptv.player.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class BackupSetting(val name: String, val type: String, val value: JsonElement)

@Serializable
data class BackupData(
    val version: Int = BackupManager.VERSION,
    val exportedAt: Long = 0,
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val positions: Map<String, Long> = emptyMap(),
    val settings: List<BackupSetting> = emptyList(),
)

data class ImportResult(val playlistsAdded: Int, val playlistsUpdated: Int, val favorites: Int, val positions: Int, val settings: Int)

/**
 * Exports playlists, favorites, watch positions and settings to one JSON document and merges such a document back.
 * The export contains Xtream passwords / M3U URLs in CLEAR TEXT (they are decrypted), so the UI must treat the
 * result as sensitive.
 */
class BackupManager(private val store: PlaylistStore, private val settings: SettingsStore) {

    suspend fun exportJson(now: Long = System.currentTimeMillis()): String {
        val snapshot = store.snapshot()
        return toJson(
            BackupData(
                exportedAt = now,
                playlists = snapshot.playlists,
                favorites = snapshot.favorites.sorted(),
                positions = snapshot.positions,
                settings = settings.exportAll(),
            ),
        )
    }

    /** Merges the backup into the current data. Throws [IllegalArgumentException] when [json] is not a valid backup. */
    suspend fun importJson(json: String): ImportResult {
        val data = fromJson(json)
        val before = store.snapshot()
        store.merge(StoreSnapshot(data.playlists, data.favorites.toSet(), data.positions))
        val settingsApplied = settings.importAll(data.settings)
        val existing = before.playlists.map { it.id }.toSet()
        return ImportResult(
            playlistsAdded = data.playlists.count { it.id !in existing },
            playlistsUpdated = data.playlists.count { it.id in existing },
            favorites = data.favorites.count { it !in before.favorites },
            positions = data.positions.size,
            settings = settingsApplied,
        )
    }

    companion object {
        const val VERSION = 1
        private val format = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun toJson(data: BackupData): String = format.encodeToString(BackupData.serializer(), data)

        fun fromJson(json: String): BackupData {
            val data = try {
                format.decodeFromString(BackupData.serializer(), json)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Yedek dosyası geçersiz", e)
            }
            require(data.version in 1..VERSION) { "Yedek sürümü desteklenmiyor: ${data.version}" }
            return data.copy(playlists = data.playlists.filter { it.id.isNotBlank() && it.source.isUsable() })
        }

        /** Incoming playlists replace existing ones with the same id and are appended otherwise; order is kept. */
        fun mergePlaylists(existing: List<Playlist>, incoming: List<Playlist>): List<Playlist> {
            val byId = incoming.associateBy { it.id }
            val updated = existing.map { byId[it.id] ?: it }
            val known = existing.map { it.id }.toSet()
            return updated + incoming.filter { it.id !in known }.distinctBy { it.id }
        }

        fun settingsToBackup(prefs: Preferences): List<BackupSetting> = prefs.asMap().mapNotNull { (key, value) ->
            val (type, element) = when (value) {
                is String -> "string" to JsonPrimitive(value)
                is Int -> "int" to JsonPrimitive(value)
                is Long -> "long" to JsonPrimitive(value)
                is Boolean -> "bool" to JsonPrimitive(value)
                is Float -> "float" to JsonPrimitive(value)
                is Double -> "double" to JsonPrimitive(value)
                is Set<*> -> "set" to JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive))
                else -> return@mapNotNull null
            }
            BackupSetting(key.name, type, element)
        }.sortedBy { it.name }

        /** Writes valid entries into [prefs]; malformed ones are skipped. Returns how many were applied. */
        fun applySettings(prefs: MutablePreferences, items: List<BackupSetting>): Int {
            var applied = 0
            for (item in items) {
                val v = item.value
                val ok: Any? = when (item.type) {
                    "string" -> (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.also { prefs[stringPreferencesKey(item.name)] = it }
                    "int" -> (v as? JsonPrimitive)?.intOrNull?.also { prefs[intPreferencesKey(item.name)] = it }
                    "long" -> (v as? JsonPrimitive)?.longOrNull?.also { prefs[longPreferencesKey(item.name)] = it }
                    "bool" -> (v as? JsonPrimitive)?.booleanOrNull?.also { prefs[booleanPreferencesKey(item.name)] = it }
                    "float" -> (v as? JsonPrimitive)?.floatOrNull?.also { prefs[floatPreferencesKey(item.name)] = it }
                    "double" -> (v as? JsonPrimitive)?.doubleOrNull?.also { prefs[doublePreferencesKey(item.name)] = it }
                    "set" -> (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
                        ?.also { prefs[stringSetPreferencesKey(item.name)] = it }
                    else -> null
                }
                if (ok != null) applied++
            }
            return applied
        }

        private fun PlaylistSource.isUsable() = when (this) {
            is PlaylistSource.M3u -> url.isNotBlank()
            is PlaylistSource.Xtream -> server.isNotBlank() && username.isNotBlank()
        }
    }
}
