package dev.iptv.player.data

enum class ContinueMode { START, RESUME, NEXT, RESTART }

/** What the primary button of a series does: play [index] of the episode list, labelled by [mode]. */
data class SeriesContinue(val index: Int, val mode: ContinueMode)

/**
 * Netflix-style continue rule. [lastId] is the most recently watched episode (null = never watched).
 * Partial -> resume it; finished -> next episode in (season, number) order; last episode finished -> start over.
 * [episodes] may come in any order; the returned index refers to that list.
 */
fun seriesContinue(episodes: List<Episode>, lastId: String?, lastFinished: Boolean): SeriesContinue? {
    if (episodes.isEmpty()) return null
    val order = episodes.indices.sortedWith(compareBy({ episodes[it].season }, { episodes[it].number }))
    val idx = lastId?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
    if (idx < 0) return SeriesContinue(order.first(), ContinueMode.START)
    if (!lastFinished) return SeriesContinue(idx, ContinueMode.RESUME)
    val pos = order.indexOf(idx)
    return if (pos < order.lastIndex) SeriesContinue(order[pos + 1], ContinueMode.NEXT) else SeriesContinue(order.first(), ContinueMode.RESTART)
}

/** Latest history entry per series (episodes only), keyed by the series' [favoriteKey]-style key. */
fun lastEpisodePerSeries(entries: List<HistoryEntry>): Map<String, HistoryEntry> {
    val out = LinkedHashMap<String, HistoryEntry>()
    for (e in entries.sortedByDescending { it.updatedAt }) {
        val s = e.seriesId ?: continue
        out.putIfAbsent("${e.playlistId}|${ContentType.SERIES}|$s", e)
    }
    return out
}
