package dev.iptv.player.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BrowseChoice(val category: String = "", val sort: String = "")

private val browseJson = Json { ignoreUnknownKeys = true }

internal fun browseKey(profileId: String, playlistId: String, type: ContentType): String =
    "$profileId|$playlistId|${type.name}"

internal fun decodeBrowsePrefs(raw: String): Map<String, BrowseChoice> =
    if (raw.isBlank()) emptyMap()
    else runCatching { browseJson.decodeFromString<Map<String, BrowseChoice>>(raw) }.getOrDefault(emptyMap())

internal fun updateBrowsePref(raw: String, key: String, category: String, sort: String): String {
    val map = decodeBrowsePrefs(raw).toMutableMap()
    map[key] = BrowseChoice(category = category, sort = sort)
    return browseJson.encodeToString(map)
}

suspend fun SettingsStore.browseChoice(key: String): BrowseChoice =
    decodeBrowsePrefs(get(Setting.BrowsePrefs))[key] ?: BrowseChoice()

suspend fun SettingsStore.saveBrowseChoice(key: String, category: String, sort: String) {
    val next = updateBrowsePref(get(Setting.BrowsePrefs), key, category, sort)
    set(Setting.BrowsePrefs, next)
}
