package dev.iptv.player.data

import java.text.Normalizer
import java.util.Locale

/** Turkish-insensitive text matching: "ISTANBUL", "İstanbul" and "istanbul" all match each other. */
object TextSearch {
    private val combiningMarks = Regex("[\u0300-\u036f]+")

    fun normalize(text: String): String {
        val folded = StringBuilder(text.length)
        for (c in text) {
            folded.append(
                when (c) {
                    'ı', 'İ', 'I', 'i' -> 'i'
                    'ş', 'Ş' -> 's'
                    'ğ', 'Ğ' -> 'g'
                    'ü', 'Ü' -> 'u'
                    'ö', 'Ö' -> 'o'
                    'ç', 'Ç' -> 'c'
                    else -> c
                },
            )
        }
        val decomposed = Normalizer.normalize(folded.toString().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "").trim().split(' ', '\t', '\n').filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun matches(name: String, query: String): Boolean {
        val q = normalize(query)
        return q.isEmpty() || normalize(name).contains(q)
    }
}
