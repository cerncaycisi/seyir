package dev.iptv.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BrowseChoice(val category: String = "", val sort: String = "")

private val browseJson = Json { ignoreUnknownKeys = true }
private val browseSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
@Volatile private var cachedBrowsePrefs: String? = null

internal fun browseKey(profileId: String, playlistId: String, type: ContentType): String =
    "$profileId|$playlistId|${type.name}"

internal fun decodeBrowsePrefs(raw: String): Map<String, BrowseChoice> =
    if (raw.isBlank()) emptyMap()
    else runCatching { browseJson.decodeFromString<Map<String, BrowseChoice>>(raw) }.getOrDefault(emptyMap())

internal fun updateBrowsePref(raw: String, key: String, category: String, sort: String): String {
    val map = decodeBrowsePrefs(raw).toMutableMap()
    map[key] = BrowseChoice(category = category, sort = sort)
    // Share VOD sort selection (e.g. "Son Eklenen" / "TMDB Puanı") across Movies and Series for the same profile.
    if (key.endsWith("|MOVIE") || key.endsWith("|SERIES")) {
        val prefix = key.substringBeforeLast('|')
        for (vodType in listOf("MOVIE", "SERIES")) {
            val sibling = "$prefix|$vodType"
            if (sibling != key) {
                val prev = map[sibling] ?: BrowseChoice()
                map[sibling] = prev.copy(sort = sort)
            }
        }
        map["${key.substringBefore('|')}|VOD_SORT"] = BrowseChoice(sort = sort)
    }
    return browseJson.encodeToString(map)
}

suspend fun SettingsStore.browseChoice(key: String): BrowseChoice {
    val raw = cachedBrowsePrefs ?: get(Setting.BrowsePrefs).also { cachedBrowsePrefs = it }
    val map = decodeBrowsePrefs(raw)
    val entry = map[key] ?: BrowseChoice()
    if ((key.endsWith("|MOVIE") || key.endsWith("|SERIES")) && entry.sort.isEmpty()) {
        val sharedSort = map["${key.substringBefore('|')}|VOD_SORT"]?.sort.orEmpty()
        if (sharedSort.isNotEmpty()) return entry.copy(sort = sharedSort)
    }
    return entry
}

suspend fun SettingsStore.saveBrowseChoice(key: String, category: String, sort: String) {
    val base = cachedBrowsePrefs ?: get(Setting.BrowsePrefs)
    val next = updateBrowsePref(base, key, category, sort)
    cachedBrowsePrefs = next
    set(Setting.BrowsePrefs, next)
}

fun SettingsStore.saveBrowseChoiceAsync(key: String, category: String, sort: String) {
    val current = cachedBrowsePrefs
    if (current != null) {
        cachedBrowsePrefs = updateBrowsePref(current, key, category, sort)
    }
    browseSaveScope.launch { saveBrowseChoice(key, category, sort) }
}
