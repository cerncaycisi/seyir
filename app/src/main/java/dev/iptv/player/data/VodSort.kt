package dev.iptv.player.data

import java.text.Collator
import java.util.Locale

/** Sort modes of the Movies / Series screens, in the order the remote cycles through them. */
enum class VodSort(val label: String) {
    DEFAULT("Varsayılan"),
    RECENT("Son eklenen"),
    RATING("Puan"),
    AZ("A-Z"),
    ZA("Z-A"),
    YEAR("Yıl"),
}

private val collator: Collator = Collator.getInstance(Locale("tr", "TR"))

fun sortStreams(items: List<Stream>, sort: VodSort): List<Stream> = when (sort) {
    VodSort.DEFAULT -> items
    VodSort.RECENT -> latestOf(items, Int.MAX_VALUE)
    VodSort.RATING -> items.sortedByDescending { it.rating?.toDoubleOrNull() ?: -1.0 }
    VodSort.AZ -> items.sortedWith { a, b -> collator.compare(a.name, b.name) }
    VodSort.ZA -> items.sortedWith { a, b -> collator.compare(b.name, a.name) }
    VodSort.YEAR -> items.sortedByDescending { it.year?.take(4)?.toIntOrNull() ?: -1 }
}

/** Newest first by the panel's `added` timestamp; without timestamps (M3U) the list order is kept. */
fun latestOf(items: List<Stream>, limit: Int): List<Stream> {
    val sorted = if (items.any { it.added != null }) items.sortedByDescending { it.added ?: 0L } else items
    return sorted.take(limit)
}

/** Individual genres across the items, most frequent first. */
fun genresOf(items: List<Stream>): List<String> =
    items.flatMap { splitGenres(it.genre) }.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

fun splitGenres(genre: String?): List<String> =
    genre?.split(',', '/', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

fun filterByGenre(items: List<Stream>, genre: String?): List<Stream> =
    if (genre == null) items else items.filter { s -> splitGenres(s.genre).any { it.equals(genre, ignoreCase = true) } }
