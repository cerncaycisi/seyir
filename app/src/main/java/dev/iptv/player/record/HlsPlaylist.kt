package dev.iptv.player.record

import java.net.URI

data class HlsSegment(val seq: Long, val url: String, val durationSec: Double)
data class HlsVariant(val bandwidth: Long, val url: String)

/** Just enough of an HLS playlist for segment-by-segment recording. */
data class HlsPlaylist(
    val variants: List<HlsVariant>,
    val segments: List<HlsSegment>,
    val targetDurationSec: Int,
    val endList: Boolean,
    val initUrl: String?,
    val encrypted: Boolean,
    val unsupported: Boolean = false,
) {
    fun bestVariant(): HlsVariant? = variants.maxByOrNull { it.bandwidth }

    companion object {
        private val attr = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

        private fun attrs(line: String): Map<String, String> =
            attr.findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

        fun resolve(base: String, ref: String): String =
            runCatching { URI(base).resolve(ref.trim()).toString() }.getOrDefault(ref.trim())

        fun parse(text: String, baseUrl: String): HlsPlaylist {
            val variants = ArrayList<HlsVariant>()
            val segments = ArrayList<HlsSegment>()
            var target = 0
            var end = false
            var init: String? = null
            var encrypted = false
            var unsupported = false
            var seq = 0L
            var pendingBandwidth: Long? = null
            var pendingDuration: Double? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> seq = line.substringAfter(':').trim().toLongOrNull() ?: 0
                    line.startsWith("#EXT-X-ENDLIST") -> end = true
                    line.startsWith("#EXT-X-KEY:") -> {
                        val method = attrs(line)["METHOD"]
                        if (method != null && !method.equals("NONE", true)) encrypted = true
                    }
                    line.startsWith("#EXT-X-BYTERANGE:") -> unsupported = true
                    line.startsWith("#EXT-X-MAP:") -> {
                        val values = attrs(line)
                        if (values.containsKey("BYTERANGE")) unsupported = true
                        values["URI"]?.let {
                            val next = resolve(baseUrl, it)
                            if (init != null && init != next) unsupported = true
                            init = next
                        }
                    }
                    line.startsWith("#EXT-X-STREAM-INF:") -> pendingBandwidth = attrs(line)["BANDWIDTH"]?.toLongOrNull() ?: 0
                    line.startsWith("#EXTINF:") -> pendingDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                    line.startsWith("#") -> Unit
                    else -> {
                        val bw = pendingBandwidth
                        if (bw != null) {
                            variants += HlsVariant(bw, resolve(baseUrl, line))
                            pendingBandwidth = null
                        } else {
                            segments += HlsSegment(seq++, resolve(baseUrl, line), pendingDuration ?: 0.0)
                            pendingDuration = null
                        }
                    }
                }
            }
            if (!text.trimStart().startsWith("#EXTM3U")) unsupported = true
            return HlsPlaylist(variants, segments, target, end, init, encrypted, unsupported)
        }
    }
}
