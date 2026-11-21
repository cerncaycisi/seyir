package dev.iptv.player.data.tmdb

/** A playlist title reduced to what TMDB can search for. */
data class CleanTitle(val title: String, val year: String?)

/** Strips provider noise (quality tags, language tags, brackets, release-group dots) from IPTV titles. */
object TitleCleaner {
    private val yearInParens = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val trailingYear = Regex("""[\s.\-_]((?:19|20)\d{2})\s*$""")
    private val inlineYear = Regex("""[\s.]((?:19|20)\d{2})(?=[\s.](?:\d{3,4}p|4k|uhd|fhd|hd|web|blu|brrip|hdrip|dvd|x26|hevc|tr\b))""", RegexOption.IGNORE_CASE)
    // Android's ICU regex engine requires literal closing delimiters to be escaped.
    private val brackets = Regex("""\[[^\]]*\]|\{[^}]*\}|\([^)]*\)""")
    private val pipeTag = Regex("""\|[^|]{1,12}\|""")
    private val prefix = Regex("""^\s*[A-Za-z]{2,3}\s*[-:|]\s+""")
    private val tags = Regex(
        """(?<![\p{L}\p{N}])(?:4k|uhd|fhd|hd|sd|hdr10?|hdr|hevc|h\.?26[45]|x26[45]|2160p|1080[pi]|720p|480p|web-?dl|web-?rip|blu-?ray|brrip|bdrip|hdrip|dvdrip|""" +
            """tr|tur|turk|türk|türkçe|turkce|dublaj|dublajlı|dublajli|altyazılı|altyazili|altyazı|altyazi|dual|multi|sub|subs|imdb)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val spaces = Regex("""\s+""")

    fun clean(raw: String): CleanTitle {
        var s = raw.trim()
        var year: String? = yearInParens.findAll(s).lastOrNull()?.groupValues?.get(1)
        s = pipeTag.replace(s, " ")
        s = brackets.replace(s, " ")
        s = prefix.replace(s, "")
        // Dotted release names: "Film.Adi.2019.1080p" -> "Film Adi 2019 1080p".
        if (!s.contains(' ') && s.count { it == '.' || it == '_' } >= 2) s = s.replace('.', ' ').replace('_', ' ')
        if (year == null) {
            inlineYear.find(s)?.let { m ->
                year = m.groupValues[1]
                s = s.removeRange(m.groups[1]!!.range)
            }
        }
        s = tags.replace(s, " ")
        s = spaces.replace(s, " ").trim()
        if (year == null) {
            trailingYear.find(s)?.let { m ->
                val rest = s.substring(0, m.range.first).trim()
                if (rest.isNotEmpty()) {
                    year = m.groupValues[1]
                    s = rest
                }
            }
        }
        s = s.trim(' ', '-', ':', '|', '.', '_', ',')
        return CleanTitle(s.ifEmpty { raw.trim() }, year)
    }
}
