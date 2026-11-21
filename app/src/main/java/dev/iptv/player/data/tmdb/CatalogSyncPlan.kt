package dev.iptv.player.data.tmdb

import dev.iptv.player.data.Stream

/** Only fields used to identify a TMDB title invalidate its result; URLs/order do not. */
internal fun metadataIdentity(stream: Stream): String = metadataIdentity(stream.name, stream.year)

internal fun metadataIdentity(name: String, yearHint: String?): String {
    val title = TitleCleaner.clean(name)
    val year = title.year ?: yearHint?.take(4)
    return "${title.title.lowercase(java.util.Locale.ROOT)}|${year.orEmpty()}"
}

internal data class CatalogSyncPlan(val changed: Set<String>, val removed: Set<String>)

internal fun catalogSyncPlan(existing: Map<String, String>, incoming: Map<String, String>) = CatalogSyncPlan(
    changed = incoming.filter { (id, identity) -> existing[id] != identity }.keys,
    removed = existing.keys - incoming.keys,
)
