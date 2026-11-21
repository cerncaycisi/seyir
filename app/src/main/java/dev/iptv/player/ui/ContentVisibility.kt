package dev.iptv.player.ui

import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentRules
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.HistoryEntry
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Stream

/** Flat lists must retain category metadata until adult and hidden categories have been filtered. */
internal fun visibleContentItems(playlist: Playlist, type: ContentType, content: Content, rules: ContentRules): List<Stream> =
    rules.filterBase(playlist, type, content).items.filter { rules.streamAllowed(playlist, it) }

/** Episode history may have no category ID, so apply the parent series restrictions as well. */
internal fun visibleHistoryStream(
    playlist: Playlist,
    entry: HistoryEntry,
    contents: Map<ContentType, Content>,
    rules: ContentRules,
): Stream? {
    val content = contents[entry.type] ?: Content(emptyList(), emptyList())
    val episode = entry.type == ContentType.SERIES && entry.seriesId != null
    val sourceId = if (episode) entry.seriesId else entry.id
    val original = content.items.firstOrNull { it.id == sourceId }
    if (original != null) {
        if (visibleContentItems(playlist, entry.type, Content(content.categories, listOf(original)), rules).isEmpty()) return null
        val stream = if (episode) entry.toStream().copy(categoryId = original.categoryId) else original
        return stream.takeIf { rules.streamAllowed(playlist, it) }
    }
    // A removed item is not reintroduced from history. If the catalog is unavailable, retain
    // unrestricted offline history; otherwise missing category metadata cannot prove visibility.
    if (content.items.isNotEmpty()) return null
    if (rules.hideAdult || rules.hiddenCategories.isNotEmpty() || (!rules.unlocked && rules.lockedCategories.isNotEmpty())) return null
    return entry.toStream().takeIf { rules.streamAllowed(playlist, it) }
}
