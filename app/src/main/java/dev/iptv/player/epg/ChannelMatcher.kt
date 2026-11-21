package dev.iptv.player.epg

/** Matches playlist channels to XMLTV channels: tvg-id first, then normalized display-name. */
class ChannelIndex(channels: List<EpgChannel>) {
    val channels: List<EpgChannel> = channels
    private val byId = HashMap<String, String>()
    private val byName = HashMap<String, String>()

    init {
        for (c in channels) {
            byId.putIfAbsent(c.id.lowercase(), c.id)
            for (n in c.allNames) {
                val key = ChannelMatcher.normalize(n)
                if (key.isNotEmpty()) byName.putIfAbsent(key, c.id)
            }
            val idKey = ChannelMatcher.normalize(c.id.substringBeforeLast('.', c.id))
            if (idKey.isNotEmpty()) byName.putIfAbsent(idKey, c.id)
        }
    }

    fun match(epgId: String?, name: String): String? {
        if (!epgId.isNullOrBlank()) byId[epgId.trim().lowercase()]?.let { return it }
        val key = ChannelMatcher.normalize(name)
        return if (key.isEmpty()) null else byName[key]
    }

    /** Search for the manual picker: names containing every query word, best (shortest) first. */
    fun search(query: String, limit: Int = 100): List<EpgChannel> {
        val words = ChannelMatcher.normalizeWords(query)
        val all = channels.sortedBy { it.name.lowercase() }
        if (words.isEmpty()) return all.take(limit)
        return all.filter { c ->
            val hay = (c.allNames + c.id).joinToString(" ") { ChannelMatcher.normalizeWords(it).joinToString(" ") }
            words.all { it in hay }
        }.take(limit)
    }
}

object ChannelMatcher {
    private val noise = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "raw", "tv", "kanal", "channel", "tr", "turkey", "turkiye")

    private fun fold(c: Char): Char = when (c) {
        'ı', 'İ', 'I' -> 'i'
        'ş', 'Ş' -> 's'
        'ğ', 'Ğ' -> 'g'
        'ü', 'Ü' -> 'u'
        'ö', 'Ö' -> 'o'
        'ç', 'Ç' -> 'c'
        else -> c.lowercaseChar()
    }

    /** Lowercased, Turkish-folded words without punctuation. */
    fun normalizeWords(name: String): List<String> {
        val sb = StringBuilder(name.length)
        for (ch in name) sb.append(if (ch.isLetterOrDigit()) fold(ch) else ' ')
        return sb.toString().split(' ').filter { it.isNotEmpty() }
    }

    /**
     * Key used for name matching: drops quality/country tags (HD, FHD, 4K, TR ...) and spaces, so
     * "TR: TRT 1 HD" and "TRT 1" share a key. Falls back to all words when everything is noise.
     */
    fun normalize(name: String): String {
        val words = normalizeWords(name)
        val kept = words.filter { it !in noise }
        return (kept.ifEmpty { words }).joinToString("")
    }
}
