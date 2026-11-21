package dev.iptv.player.data

/** Cleanup for provider-supplied titles that are shown verbatim in the UI. */
object DisplayText {
    private val placeholderTitles = setOf(
        "no data", "no information", "no info", "no epg", "no program", "no programme",
        "n/a", "na", "tba", "tbd", "-", "--", "?", "unknown", "program bilgisi yok",
    )

    /** EPG feeds often fill gaps with fake programmes such as "No Data"; treat them as missing. */
    fun isPlaceholderTitle(title: String?): Boolean =
        title == null || title.trim().lowercase().trimEnd('.', '!') in placeholderTitles

    private val episodeCode = Regex("""(?i)\bS\s?\d{1,3}\s?[EB]\s?\d{1,4}\b""")
    private val seasonEpisodeWords = Regex("""(?i)\b(sezon|season)\s*\d+\s*[,.-]?\s*(bölüm|bolum|episode|ep)\s*\d+\b""")
    private val separators = " -–—:|·.,_"

    /**
     * Strips the repeated series name and SxxEyy markers panels put into episode titles
     * ("SERIES NAME - S01E01 - Real title" → "Real title"). Returns "" when nothing meaningful is left.
     */
    fun episodeTitle(raw: String?, seriesName: String?): String {
        var t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val series = seriesName?.trim().orEmpty()
        if (series.isNotEmpty() && TextSearch.normalize(t).startsWith(TextSearch.normalize(series))) {
            t = t.substring(minOf(series.length, t.length))
        }
        t = episodeCode.replace(t, " ")
        t = seasonEpisodeWords.replace(t, " ")
        t = t.trim { it in separators || it.isWhitespace() }
        t = t.replace(Regex("""\s{2,}"""), " ")
        return if (isPlaceholderTitle(t) || t.all { it.isDigit() || it in separators }) "" else t
    }
}
