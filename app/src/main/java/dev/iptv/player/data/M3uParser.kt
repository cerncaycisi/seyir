package dev.iptv.player.data

/** Result of parsing a whole playlist: the entries plus playlist-level metadata. */
data class M3uDocument(val streams: List<Stream>, val epgUrl: String?)

object M3uParser {
    private val attribute = Regex("""([\w-]+)="([^"]*)"""")
    private val movieExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv")

    fun parse(text: String): List<Stream> = parseDocument(text).streams

    fun parseDocument(text: String): M3uDocument {
        val result = mutableListOf<Stream>()
        var info: String? = null
        var pending = LinkedHashMap<String, String>()
        var headerAttrs: Map<String, String> = emptyMap()
        var epgUrl: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    headerAttrs = attributes(line)
                    epgUrl = (headerAttrs["url-tvg"] ?: headerAttrs["x-tvg-url"] ?: headerAttrs["tvg-url"])
                        ?.split(',')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().trim('"')
                    when (key) {
                        "http-user-agent" -> if (value.isNotEmpty()) pending[USER_AGENT] = value
                        "http-referrer", "http-referer" -> if (value.isNotEmpty()) pending[REFERER] = value
                    }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    result += toStream(result.size, info, line, headerAttrs, pending)
                    info = null
                    pending = LinkedHashMap()
                }
            }
        }
        return M3uDocument(result, epgUrl)
    }

    private fun attributes(line: String): Map<String, String> =
        attribute.findAll(line).associate { m -> m.groupValues[1].lowercase() to m.groupValues[2] }

    private fun toStream(
        index: Int,
        info: String?,
        url: String,
        header: Map<String, String>,
        options: Map<String, String>,
    ): Stream {
        val attrs = info?.let { attributes(it) }.orEmpty()
        // Titles may contain commas, so the title starts at the first comma after the last quoted attribute.
        val title = info?.let {
            val comma = it.indexOf(',', it.lastIndexOf('"') + 1)
            if (comma < 0) "" else it.substring(comma + 1).trim()
        }.orEmpty()

        val headers = LinkedHashMap<String, String>()
        (attrs["http-user-agent"] ?: attrs["user-agent"])?.takeIf { it.isNotBlank() }?.let { headers[USER_AGENT] = it }
        (attrs["http-referrer"] ?: attrs["http-referer"] ?: attrs["referrer"] ?: attrs["referer"])
            ?.takeIf { it.isNotBlank() }?.let { headers[REFERER] = it }
        headers.putAll(options) // #EXTVLCOPT lines are the more specific source

        val catchupSource = (attrs["catchup-source"] ?: header["catchup-source"])?.takeIf { it.isNotBlank() }
        val catchup = (attrs["catchup"] ?: header["catchup"])?.trim()?.takeIf { it.isNotEmpty() }
            ?: catchupSource?.let { "default" }
        val days = listOf(attrs["catchup-days"], header["catchup-days"], attrs["timeshift"], attrs["tvg-rec"])
            .firstNotNullOfOrNull { it?.trim()?.toIntOrNull()?.takeIf { d -> d > 0 } } ?: 0

        return Stream(
            id = index.toString(),
            name = title.ifEmpty { attrs["tvg-name"].orEmpty() }.ifEmpty { url },
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            url = url,
            categoryId = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Diğer",
            type = typeOf(url),
            epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            headers = headers,
            catchup = catchup,
            catchupDays = if (catchup != null) days else 0,
            catchupSource = catchupSource,
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

    private const val USER_AGENT = "User-Agent"
    private const val REFERER = "Referer"
}
