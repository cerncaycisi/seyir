package dev.iptv.player.search

import dev.iptv.player.data.TextSearch

/** A normalized system-search query: every token must appear in a title. */
class SearchQuery private constructor(val tokens: List<String>) {
    val joined: String get() = tokens.joinToString(" ")

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 64

        /** Null when the query is too short to be useful (global search fires on every keystroke). */
        fun parse(raw: String?): SearchQuery? {
            val normalized = TextSearch.normalize((raw ?: "").trim().take(MAX_LENGTH))
            if (normalized.length < MIN_LENGTH) return null
            return SearchQuery(normalized.split(' ').filter { it.isNotEmpty() })
        }
    }
}

/** One searchable cached item. [norm] is [TextSearch.normalize] of [name], computed once when the index is built. */
class SearchRow(
    val playlistId: String,
    val type: String,
    val id: String,
    val name: String,
    val norm: String,
    val logo: String?,
    val year: String?,
)

object SearchLogic {
    private val typeOrder = mapOf("LIVE" to 0, "MOVIE" to 1, "SERIES" to 2)

    /** 3 = title starts with the query, 2 = a word starts with it, 1 = all tokens contained somewhere, 0 = no match. */
    fun score(norm: String, q: SearchQuery): Int {
        if (!q.tokens.all { norm.contains(it) }) return 0
        val joined = q.joined
        return when {
            norm.startsWith(joined) -> 3
            norm.split(' ').any { it.startsWith(q.tokens.first()) } -> 2
            else -> 1
        }
    }

    fun rank(rows: List<SearchRow>, q: SearchQuery, limit: Int): List<SearchRow> =
        rows.asSequence()
            .map { it to score(it.norm, q) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<SearchRow, Int>> { it.second }
                    .thenBy { typeOrder[it.first.type] ?: 3 }
                    .thenBy { it.first.norm.length },
            )
            .take(limit)
            .map { it.first }
            .toList()

    fun typeLabel(type: String) = when (type) {
        "LIVE" -> "Canlı TV"
        "MOVIE" -> "Film"
        "SERIES" -> "Dizi"
        else -> ""
    }
}
