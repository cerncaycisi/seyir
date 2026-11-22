package dev.iptv.player.player

import android.content.Context
import androidx.annotation.OptIn
import dev.iptv.player.R
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

/** One selectable row in the audio / subtitle / quality pickers. `group == null` means "Off" or "Auto". */
data class TrackOption(
    val label: String,
    val selected: Boolean,
    val group: Tracks.Group? = null,
    val index: Int = -1,
)

internal fun trackOptions(
    context: Context,
    tracks: Tracks,
    params: TrackSelectionParameters,
    type: Int,
    noneLabel: String?,
): List<TrackOption> {
    val disabled = type in params.disabledTrackTypes
    val groups = tracks.groups.filter { it.type == type }
    val out = ArrayList<TrackOption>()
    var ordinal = 0
    for (g in groups) for (i in 0 until g.length) {
        if (!g.isTrackSupported(i)) continue
        ordinal++
        val f = g.getTrackFormat(i)
        val label = when (type) {
            C.TRACK_TYPE_AUDIO -> audioLabel(context, f, ordinal)
            C.TRACK_TYPE_TEXT -> textLabel(context, f, ordinal)
            else -> videoLabel(f)
        }
        out += TrackOption(label, g.isTrackSelected(i) && !disabled, g, i)
    }
    if (type == C.TRACK_TYPE_VIDEO) {
        if (out.size < 2) return emptyList()
        val sorted = out.sortedByDescending { it.group!!.getTrackFormat(it.index).height }
        val auto = params.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }
        return listOf(TrackOption(context.getString(R.string.play_auto), auto)) + sorted.map { it.copy(selected = it.selected && !auto) }
    }
    if (noneLabel != null) {
        val anySelected = out.any { it.selected }
        out.add(0, TrackOption(noneLabel, !anySelected))
    }
    return out
}

internal fun ExoPlayer.selectTrack(type: Int, option: TrackOption) {
    val b = trackSelectionParameters.buildUpon()
    val group = option.group
    if (group == null) {
        if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.clearOverridesOfType(type)
    } else {
        b.setTrackTypeDisabled(type, false)
        b.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.index))
    }
    trackSelectionParameters = b.build()
}

private fun languageName(context: Context, code: String?): String? {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return null
    val locale = context.resources.configuration.locales[0]
    val name = Locale.forLanguageTag(code).getDisplayLanguage(locale)
    return (if (name.isBlank() || name.equals(code, true)) code.uppercase() else name)
        .replaceFirstChar { it.titlecase(locale) }
}

private fun channelsLabel(n: Int) = when (n) {
    Format.NO_VALUE, 0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${n}ch"
}

private fun codecLabel(mime: String?) = when {
    mime == null -> null
    mime == MimeTypes.AUDIO_AC3 -> "AC3"
    mime == MimeTypes.AUDIO_E_AC3 || mime == MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3"
    mime.startsWith("audio/vnd.dts") -> "DTS"
    mime == MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    mime == MimeTypes.AUDIO_AAC -> "AAC"
    mime == MimeTypes.AUDIO_MPEG -> "MP3"
    mime == MimeTypes.AUDIO_OPUS -> "Opus"
    else -> null
}

private fun audioLabel(context: Context, f: Format, ordinal: Int): String {
    val lang = languageName(context, f.language)
    val parts = listOfNotNull(lang, extraLabel(context, f, lang), channelsLabel(f.channelCount), codecLabel(f.sampleMimeType))
        .distinct()
    return if (parts.isEmpty()) context.getString(R.string.play_audio_number, ordinal) else parts.joinToString(" · ")
}

private fun textLabel(context: Context, f: Format, ordinal: Int): String {
    val forced = if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) context.getString(R.string.play_sub_forced) else null
    val lang = languageName(context, f.language)
    val parts = listOfNotNull(lang, extraLabel(context, f, lang), forced).distinct()
    return if (parts.isEmpty()) context.getString(R.string.play_sub_number, ordinal) else parts.joinToString(" · ")
}

@OptIn(UnstableApi::class)
private fun videoLabel(f: Format): String {
    val res = if (f.height > 0) "${f.height}p" else "Video"
    val rate = if (f.bitrate > 0) " · %.1f Mbps".format(Locale.US, f.bitrate / 1_000_000.0) else ""
    return res + rate
}

/** Two-letter form of an ISO 639-1/2 code ("tur" → "tr", "eng" → "en"); null when unknown/undetermined. */
internal fun normalizeLanguage(code: String?): String? {
    val c = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
    if (c.isNullOrEmpty() || c == "und" || c == "mul" || c == "zxx") return null
    if (c.length == 2) return c
    return iso3to2[c] ?: c
}

private val iso3to2: Map<String, String> by lazy {
    buildMap {
        Locale.getISOLanguages().forEach { two -> runCatching { put(Locale(two).isO3Language, two) } }
        // Bibliographic ISO 639-2/B variants that Locale does not report.
        put("ger", "de"); put("fre", "fr"); put("dut", "nl"); put("gre", "el"); put("chi", "zh"); put("per", "fa"); put("cze", "cs"); put("rum", "ro")
    }
}

/**
 * Index (into [languages], one entry per audio track in display order) of the "original language" track,
 * using TMDB's original language. When TMDB does not know the title the stream default is kept (null):
 * guessing "the non-Turkish track" picked the English dub of a Turkish documentary on the real TV.
 * When the original language is known but not among the tracks (a Japanese film with TR + EN), the
 * first non-Turkish track is the closer choice.
 */
internal fun originalAudioPick(languages: List<String?>, originalLanguage: String?): Int? {
    val want = normalizeLanguage(originalLanguage) ?: return null
    val langs = languages.map(::normalizeLanguage)
    langs.indexOf(want).takeIf { it >= 0 }?.let { return it }
    if (want == "tr" || langs.size < 2) return null
    return langs.indexOfFirst { it != null && it != "tr" }.takeIf { it >= 0 }
}

/**
 * Index (into [tracks], (language, forced) per text track in display order) of the subtitle to show by
 * default: the first full (non-forced) track in [preferred]; a forced (signs-only) track only when it is
 * the sole match. Null when no track has that language.
 */
internal fun subtitlePick(tracks: List<Pair<String?, Boolean>>, preferred: String?): Int? {
    val want = normalizeLanguage(preferred) ?: return null
    val matches = tracks.withIndex().filter { normalizeLanguage(it.value.first) == want }
    return (matches.firstOrNull { !it.value.second } ?: matches.firstOrNull())?.index
}

/** The track's own label unless it just repeats the language ("Türkçe · TÜRKÇE", "Turkish"). */
private fun extraLabel(context: Context, f: Format, languageName: String?): String? {
    val label = f.label?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val locale = context.resources.configuration.locales[0]
    val repeats = label.equals(f.language, true) ||
        (languageName != null && label.lowercase(locale) == languageName.lowercase(locale)) ||
        (f.language != null && label.equals(Locale.forLanguageTag(f.language!!).getDisplayLanguage(Locale.ENGLISH), true))
    return label.takeUnless { repeats }
}
