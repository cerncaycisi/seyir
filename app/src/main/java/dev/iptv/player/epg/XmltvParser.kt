package dev.iptv.player.epg

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone

data class XmltvChannel(val id: String, val names: List<String>)

/**
 * Streaming XMLTV reader: only the current element is held in memory, so 100+ MB guides are fine.
 * The caller supplies the [XmlPullParser] (Android's `Xml.newPullParser()` in the app, kXML in tests).
 */
object XmltvParser {
    private const val MAX_DESCRIPTION = 500

    fun parse(
        parser: XmlPullParser,
        input: InputStream,
        onChannel: (XmltvChannel) -> Unit,
        onProgramme: (Programme) -> Unit,
    ) {
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let(onChannel)
                    "programme" -> readProgramme(parser)?.let(onProgramme)
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(p: XmlPullParser): XmltvChannel? {
        val id = p.getAttributeValue(null, "id")?.trim().orEmpty()
        val names = ArrayList<String>(2)
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.name == "channel") break
            if (e == XmlPullParser.START_TAG) {
                if (p.name == "display-name") safeText(p).takeIf { it.isNotBlank() }?.let { names += it.trim() } else skip(p)
            }
        }
        return if (id.isEmpty()) null else XmltvChannel(id, names)
    }

    private fun readProgramme(p: XmlPullParser): Programme? {
        val channel = p.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parseTime(p.getAttributeValue(null, "start"))
        val stop = parseTime(p.getAttributeValue(null, "stop"))
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.name == "programme") break
            if (e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> if (title == null) title = safeText(p).trim() else skip(p)
                "desc" -> if (desc == null) desc = safeText(p).trim() else skip(p)
                "category" -> if (category == null) category = safeText(p).trim() else skip(p)
                else -> skip(p)
            }
        }
        if (channel.isEmpty() || start == null) return null
        // Some feeds omit `stop`; assume a half-hour slot rather than dropping the entry.
        val end = stop ?: (start + 30 * 60)
        if (end <= start) return null
        return Programme(
            channelId = channel,
            start = start,
            stop = end,
            title = title.orEmpty().ifEmpty { "-" },
            description = desc?.takeIf { it.isNotEmpty() }?.take(MAX_DESCRIPTION),
            category = category?.takeIf { it.isNotEmpty() },
        )
    }

    /** Text of a leaf element (nested markup is skipped). Leaves the parser on the element's END_TAG. */
    private fun safeText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (depth == 1) sb.append(p.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * XMLTV time: `yyyyMMddHHmmss[ ]+hhmm`. Shorter digit runs (date, hhmm) are accepted; a missing zone means UTC.
     * Returns UTC epoch seconds, or null if unparseable.
     */
    fun parseTime(raw: String?): Long? {
        val t = raw?.trim().orEmpty()
        var i = 0
        while (i < t.length && i < 14 && t[i].isDigit()) i++
        if (i < 8) return null
        val digits = t.substring(0, i).padEnd(14, '0')
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.substring(8, 10).toInt()
        val minute = digits.substring(10, 12).toInt()
        val second = digits.substring(12, 14).toInt()
        if (month !in 1..12 || day !in 1..31 || hour > 24 || minute > 59 || second > 60) return null
        val zone = t.substring(i).trim()
        val offsetSeconds = when {
            zone.isEmpty() || zone.equals("Z", true) || zone.equals("UTC", true) || zone.equals("GMT", true) -> 0
            zone[0] == '+' || zone[0] == '-' -> {
                val body = zone.substring(1).replace(":", "")
                if (body.length < 2 || !body.all { it.isDigit() }) return null
                val h = body.substring(0, 2).toInt()
                val m = if (body.length >= 4) body.substring(2, 4).toInt() else 0
                (h * 3600 + m * 60) * if (zone[0] == '-') -1 else 1
            }
            else -> 0
        }
        val cal = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis / 1000 - offsetSeconds
    }
}
