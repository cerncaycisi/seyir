package dev.iptv.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Reads/writes hidden channels, hidden categories, locked categories and the PIN. */
class ContentPrefs(private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }

    val rules: Flow<ContentRules> = combine(
        settings.flow(Setting.HiddenChannels),
        settings.flow(Setting.HiddenCategories),
        settings.flow(Setting.LockedCategories),
        settings.flow(Setting.HideAdult),
    ) { channels, cats, locked, adult -> ContentRules(channels, cats, locked, adult) }

    val labels: Flow<Map<String, String>> = settings.flow(Setting.HiddenLabels).map { decodeLabels(it) }

    private fun decodeLabels(raw: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    private suspend fun putLabel(key: String, label: String?) {
        val map = decodeLabels(settings.get(Setting.HiddenLabels)).toMutableMap()
        if (label == null) map.remove(key) else map[key] = label
        settings.set(Setting.HiddenLabels, json.encodeToString(map))
    }

    private suspend fun toggle(setting: Setting<Set<String>>, key: String, on: Boolean, label: String?) {
        val cur = settings.get(setting)
        settings.set(setting, if (on) cur + key else cur - key)
        if (on && label != null) putLabel(key, label)
        // Keep a label while the key is still used by another list.
        if (!on && key !in settings.get(Setting.HiddenChannels) && key !in settings.get(Setting.HiddenCategories) &&
            key !in settings.get(Setting.LockedCategories)
        ) putLabel(key, null)
    }

    suspend fun setChannelHidden(playlist: Playlist, s: Stream, hidden: Boolean) =
        toggle(Setting.HiddenChannels, ParentalFilter.streamKey(playlist.id, s.type, s.id), hidden, s.name)

    suspend fun setCategoryHidden(playlist: Playlist, type: ContentType, id: String, name: String, hidden: Boolean) =
        toggle(Setting.HiddenCategories, ParentalFilter.categoryKey(playlist.id, type, id), hidden, name)

    /** One write for many categories (the bulk "hide all / show all" on the Categories settings page). */
    suspend fun setCategoriesHidden(playlist: Playlist, type: ContentType, categories: List<Category>, hidden: Boolean) {
        val keys = categories.associate { ParentalFilter.categoryKey(playlist.id, type, it.id) to it.name }
        val cur = settings.get(Setting.HiddenCategories)
        settings.set(Setting.HiddenCategories, if (hidden) cur + keys.keys else cur - keys.keys)
        if (hidden) {
            val map = decodeLabels(settings.get(Setting.HiddenLabels)) + keys
            settings.set(Setting.HiddenLabels, json.encodeToString(map))
        }
    }

    suspend fun setCategoryLocked(playlist: Playlist, type: ContentType, id: String, name: String, locked: Boolean) =
        toggle(Setting.LockedCategories, ParentalFilter.categoryKey(playlist.id, type, id), locked, name)

    suspend fun setChannelHiddenByKey(key: String, hidden: Boolean) = toggle(Setting.HiddenChannels, key, hidden, null)
    suspend fun setCategoryHiddenByKey(key: String, hidden: Boolean) = toggle(Setting.HiddenCategories, key, hidden, null)
    suspend fun setCategoryLockedByKey(key: String, locked: Boolean) = toggle(Setting.LockedCategories, key, locked, null)

    suspend fun clearHidden() {
        settings.set(Setting.HiddenChannels, emptySet())
        settings.set(Setting.HiddenCategories, emptySet())
    }

    suspend fun hasPin(): Boolean = settings.get(Setting.ParentalPin).isNotEmpty()

    suspend fun verifyPin(pin: String): Boolean = ParentalFilter.verifyPin(settings.get(Setting.ParentalPin), pin)

    suspend fun setPin(pin: String) = settings.set(Setting.ParentalPin, ParentalFilter.hashPin(pin))

    /** Removing the PIN also drops category locks, which would otherwise be unopenable. */
    suspend fun removePin() {
        settings.set(Setting.ParentalPin, "")
        settings.set(Setting.LockedCategories, emptySet())
    }
}
