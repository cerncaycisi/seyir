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

    /** Drops every channel of a removed playlist. */
    fun withoutPlaylist(groups: List<ChannelGroup>, playlistId: String): List<ChannelGroup> =
        groups.map { g -> g.copy(keys = g.keys.filterNot { it.startsWith("$playlistId|") }) }
}
