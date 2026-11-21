package dev.iptv.player.data

object M3uParser {
    private val attribute = Regex("""([\w-]+)="([^"]*)"""")
    private val movieExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv")

    fun parse(text: String): List<Stream> {
        val result = mutableListOf<Stream>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    result += toStream(result.size, info, line)
                    info = null
                }
            }
        }
        return result
    }

    private fun toStream(index: Int, info: String?, url: String): Stream {
        val attrs = info?.let { attribute.findAll(it).associate { m -> m.groupValues[1].lowercase() to m.groupValues[2] } }.orEmpty()
        // Titles may contain commas, so the title starts at the first comma after the last quoted attribute.
        val title = info?.let { it.substring(it.indexOf(',', it.lastIndexOf('"') + 1) + 1).trim() }.orEmpty()
        return Stream(
            id = index.toString(),
            name = title.ifEmpty { attrs["tvg-name"].orEmpty() }.ifEmpty { url },
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            url = url,
            categoryId = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Diğer",
            type = typeOf(url),
            epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun typeOf(url: String): ContentType {
        val path = url.substringBefore('?').lowercase()
        return when {
            "/series/" in path -> ContentType.SERIES
            "/movie/" in path || path.substringAfterLast('.', "") in movieExtensions -> ContentType.MOVIE
            else -> ContentType.LIVE
        }
    }
}
