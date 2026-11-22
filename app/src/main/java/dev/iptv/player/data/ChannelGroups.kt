package dev.iptv.player.data

import kotlinx.serialization.Serializable

/** A user-made channel group ("Gruplarım"). [keys] are [favoriteKey] strings, in the order they were added. */
@Serializable
data class ChannelGroup(val id: String, val name: String, val keys: List<String> = emptyList())

/** Pure edits on the group list, kept apart from storage so they can be unit tested. */
object ChannelGroups {
    fun create(groups: List<ChannelGroup>, id: String, name: String, firstKey: String?): List<ChannelGroup> {
        val clean = name.trim().ifEmpty { return groups }
        return groups + ChannelGroup(id, clean, listOfNotNull(firstKey))
    }

    /** Adds [key] to the group, or removes it when it is already there. */
    fun toggle(groups: List<ChannelGroup>, groupId: String, key: String): List<ChannelGroup> = groups.map { g ->
        if (g.id != groupId) g else g.copy(keys = if (key in g.keys) g.keys - key else g.keys + key)
    }

    fun delete(groups: List<ChannelGroup>, groupId: String): List<ChannelGroup> = groups.filterNot { it.id == groupId }

    fun rename(groups: List<ChannelGroup>, groupId: String, name: String): List<ChannelGroup> {
        val clean = name.trim().ifEmpty { return groups }
        return groups.map { if (it.id == groupId) it.copy(name = clean) else it }
    }

    /** Moves [groupId] by [delta] positions (e.g. -1 = up, +1 = down), clamped to list bounds. */
    fun move(groups: List<ChannelGroup>, groupId: String, delta: Int): List<ChannelGroup> {
        val from = groups.indexOfFirst { it.id == groupId }
        if (from < 0) return groups
        val to = (from + delta).coerceIn(0, groups.lastIndex)
        if (to == from) return groups
        val copy = groups.toMutableList()
        val item = copy.removeAt(from)
        copy.add(to, item)
        return copy
    }

    /** Merges imported backup groups into [existing] by id (or case-insensitive name), preserving channel order. */
    fun merge(existing: List<ChannelGroup>, incoming: List<ChannelGroup>): List<ChannelGroup> {
        val valid = incoming.mapNotNull { g ->
            val clean = g.name.trim()
            if (g.id.isBlank() || clean.isEmpty()) null
            else g.copy(name = clean, keys = g.keys.filter { it.isNotBlank() }.distinct())
        }
        if (valid.isEmpty()) return existing
        val matchedIds = HashSet<String>()
        val updated = existing.map { cur ->
            val hit = valid.firstOrNull { it.id == cur.id || it.name.equals(cur.name, ignoreCase = true) }
            if (hit == null) cur else {
                matchedIds += hit.id
                cur.copy(name = hit.name, keys = (cur.keys + hit.keys).distinct())
            }
        }
        val appended = valid.filter { it.id !in matchedIds }.distinctBy { it.id }
        return updated + appended
    }

    /** Drops every channel of a removed playlist. */
    fun withoutPlaylist(groups: List<ChannelGroup>, playlistId: String): List<ChannelGroup> =
        groups.map { g -> g.copy(keys = g.keys.filterNot { it.startsWith("$playlistId|") }) }
}
