package dev.iptv.player.data.tmdb

import java.text.Normalizer
import java.util.Locale

/** Prefer an unambiguous title match over attaching another title's rating/cast. */
internal fun sameTitle(query: String, candidate: String?): Boolean {
    fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), "")
    return !candidate.isNullOrBlank() && normalize(query) == normalize(candidate)
}
