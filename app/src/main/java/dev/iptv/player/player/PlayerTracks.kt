package dev.iptv.player.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
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
            C.TRACK_TYPE_AUDIO -> audioLabel(f, ordinal)
            C.TRACK_TYPE_TEXT -> textLabel(f, ordinal)
            else -> videoLabel(f)
        }
        out += TrackOption(label, g.isTrackSelected(i) && !disabled, g, i)
    }
    if (type == C.TRACK_TYPE_VIDEO) {
        if (out.size < 2) return emptyList()
        val sorted = out.sortedByDescending { it.group!!.getTrackFormat(it.index).height }
        val auto = params.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }
        return listOf(TrackOption("Otomatik", auto)) + sorted.map { it.copy(selected = it.selected && !auto) }
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

private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale("tr"))
    return (if (name.isBlank() || name.equals(code, true)) code.uppercase() else name)
        .replaceFirstChar { it.titlecase(Locale("tr")) }
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

private fun audioLabel(f: Format, ordinal: Int): String {
    val parts = listOfNotNull(languageName(f.language), f.label?.takeIf { it.isNotBlank() && !it.equals(f.language, true) }, channelsLabel(f.channelCount), codecLabel(f.sampleMimeType))
        .distinct()
    return if (parts.isEmpty()) "Ses $ordinal" else parts.joinToString(" · ")
}

private fun textLabel(f: Format, ordinal: Int): String {
    val forced = if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "Zorunlu" else null
    val parts = listOfNotNull(languageName(f.language), f.label?.takeIf { it.isNotBlank() && !it.equals(f.language, true) }, forced).distinct()
    return if (parts.isEmpty()) "Altyazı $ordinal" else parts.joinToString(" · ")
}

private fun videoLabel(f: Format): String {
    val res = if (f.height > 0) "${f.height}p" else "Video"
    val rate = if (f.bitrate > 0) " · %.1f Mb/sn".format(Locale.US, f.bitrate / 1_000_000.0) else ""
    return res + rate
}
