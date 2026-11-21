package dev.iptv.player.data

import java.text.Collator
import java.util.Locale

/** Entries pinned above the provider categories of the Movies / Series / Live browsers. */
enum class SpecialCategory { ALL, CONTINUE, RECENT, WATCH_LATER, FAVORITES }

/** Items of a special category. [items] must already be the list the viewer is allowed to see. */
fun specialItems(
    special: SpecialCategory,
    playlist: Playlist,
    items: List<Stream>,
    favorites: Set<String>,
    watchLater: Set<String>,
    history: List<HistoryEntry>,
): List<Stream> = when (special) {
    SpecialCategory.ALL -> items
    SpecialCategory.FAVORITES -> items.filter { favoriteKey(playlist, it) in favorites }
    SpecialCategory.WATCH_LATER -> items.filter { favoriteKey(playlist, it) in watchLater }
    SpecialCategory.CONTINUE -> recentStreams(continueWatching(history), items, playlist.id)
    SpecialCategory.RECENT -> recentStreams(history, items, playlist.id)
}

/**
 * Catalog items behind history [entries] (newest first by updatedAt), one per movie / series / channel.
 * Episodes map to their series; entries whose item is no longer in [items] are dropped.
 */
fun recentStreams(entries: List<HistoryEntry>, items: List<Stream>, playlistId: String): List<Stream> {
    val byKey = items.associateBy { it.type to it.id }
    val seen = HashSet<Pair<ContentType, String>>()
    return entries.asSequence()
        .filter { it.playlistId == playlistId }
        .sortedByDescending { it.updatedAt }
        .mapNotNull { e ->
            val k = e.type to (e.seriesId ?: e.id)
            if (k in seen) null else byKey[k]?.also { seen += k }
        }
        .toList()
}

/** Sort modes of the live channel list. */
enum class LiveSort { DEFAULT, NUMBER, AZ, ZA }

private val collator: Collator = Collator.getInstance(Locale("tr", "TR"))

/** NUMBER orders by numeric panel id (channel number); channels without one keep their order at the end. */
fun sortChannels(items: List<Stream>, sort: LiveSort): List<Stream> = when (sort) {
    LiveSort.DEFAULT -> items
    LiveSort.NUMBER -> items.sortedBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
    LiveSort.AZ -> items.sortedWith { a, b -> collator.compare(a.name, b.name) }
    LiveSort.ZA -> items.sortedWith { a, b -> collator.compare(b.name, a.name) }
}
