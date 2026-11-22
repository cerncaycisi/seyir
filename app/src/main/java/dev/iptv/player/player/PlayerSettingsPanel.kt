package dev.iptv.player.player

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.pointerClick
import kotlinx.coroutines.android.awaitFrame

enum class AspectMode(val key: String, val labelRes: Int, val ratio: Float?) {
    Fit("FIT", R.string.play_aspect_fit, null),
    Fill("FILL", R.string.play_aspect_fill, null),
    Zoom("ZOOM", R.string.play_aspect_zoom, null),
    R16x9("16_9", R.string.play_aspect_r16x9, 16f / 9f),
    R4x3("4_3", R.string.play_aspect_r4x3, 4f / 3f);

    val label: String
        @Composable get() = stringResource(labelRes)

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Fit
    }
}

val PlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
val SubtitleScales = listOf(75, 100, 125, 150, 200)
val SleepOptions = listOf(0, 15, 30, 60, 90)

@Composable
fun speedLabel(s: Float) = if (s == 1f) stringResource(R.string.play_speed_normal) else "${s.toString().removeSuffix(".0")}x"
@Composable
private fun sleepLabel(m: Int) = if (m == 0) stringResource(R.string.play_off) else stringResource(R.string.play_minutes, m)

enum class PlayerPage(val title: Int) {
    Main(R.string.play_panel_title),
    Aspect(R.string.play_aspect),
    Speed(R.string.play_speed),
    Quality(R.string.play_quality),
    Audio(R.string.play_audio),
    Subtitle(R.string.play_subtitles),
    SubStyle(R.string.sub_style_title),
    SubSize(R.string.play_sub_size),
    Sleep(R.string.play_sleep),
    Online(R.string.play_online_subtitles),
    Info(R.string.ux_info),
}

private class RowSpec(
    val label: String,
    val value: String? = null,
    val checked: Boolean = false,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit,
)

/** Right-hand settings panel; every page is a D-pad list, BACK goes up one level and finally closes. */
@Composable
fun PlayerSettingsPanel(
    live: Boolean,
    aspect: AspectMode,
    onAspect: (AspectMode) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    audio: List<TrackOption>,
    onAudio: (TrackOption) -> Unit,
    subtitles: List<TrackOption>,
    onSubtitle: (TrackOption) -> Unit,
    quality: List<TrackOption>,
    onQuality: (TrackOption) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    subtitleScale: Int,
    onSubtitleScale: (Int) -> Unit,
    sleepMinutes: Int,
    onSleep: (Int) -> Unit,
    subtitleSearch: SubtitleSearch?,
    onPreviousChannel: (() -> Unit)?,
    onChannelList: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToLive: (() -> Unit)? = null,
    recording: Boolean = false,
    onRecordToggle: (() -> Unit)? = null,
    onMultiView: ((Int) -> Unit)? = null,
    onPip: (() -> Unit)? = null,
    initialPage: PlayerPage = PlayerPage.Main,
    streamInfo: String = "",
) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    var mainFocus by remember { mutableIntStateOf(0) }
    fun back() { if (initialPage == PlayerPage.Main) page = PlayerPage.Main else onClose() }
    BackHandler { onClose() }

    val offOrAuto = { o: List<TrackOption> -> o.firstOrNull { it.selected }?.label ?: "—" }

    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Color(0xF2111620))
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        val title = if (page == PlayerPage.Main) stringResource(if (live) R.string.play_panel_live else R.string.play_panel_vod) else stringResource(page.title)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        key(page) {
            when (page) {
                PlayerPage.Main -> {
                    // Live channels and movies/episodes get different menus: channel actions first for TV,
                    // track and subtitle settings first for VOD. Rows without real choices are hidden.
                    val rows = ArrayList<RowSpec>()
                    fun add(label: String, value: String?, target: PlayerPage, icon: ImageVector) {
                        val i = rows.size
                        rows += RowSpec(label, value, icon = icon) { mainFocus = i; page = target }
                    }
                    val audioValue = audio.firstOrNull { it.selected }?.label ?: if (audio.isEmpty()) stringResource(R.string.play_none) else "—"
                    val subtitleValue = subtitles.firstOrNull { it.selected && it.group != null }?.label ?: stringResource(R.string.play_off)
                    val qualityValue = if (quality.isEmpty()) stringResource(R.string.play_auto) else offOrAuto(quality)
                    if (live) {
                        if (onBackToLive != null) rows += RowSpec(stringResource(R.string.play_back_live), icon = Icons.Rounded.LiveTv) { onBackToLive() }
                        if (onPreviousChannel != null) rows += RowSpec(stringResource(R.string.play_previous_channel), icon = Icons.Rounded.History) { onPreviousChannel() }
                        if (onChannelList != null) rows += RowSpec(stringResource(R.string.play_channel_list), icon = Icons.AutoMirrored.Rounded.List) { onChannelList() }
                        if (onRecordToggle != null) rows += RowSpec(
                            if (recording) stringResource(R.string.play_stop_recording) else stringResource(R.string.play_record),
                            if (recording) stringResource(R.string.play_record_badge) else null,
                            icon = Icons.Rounded.FiberManualRecord,
                        ) { onRecordToggle() }
                        if (onMultiView != null) {
                            rows += RowSpec(stringResource(R.string.play_multiview), stringResource(R.string.play_two_channels), icon = Icons.Rounded.GridView) { onMultiView(2) }
                            rows += RowSpec(stringResource(R.string.play_multiview), stringResource(R.string.play_four_channels), icon = Icons.Rounded.GridView) { onMultiView(4) }
                        }
                        if (onPip != null) rows += RowSpec(stringResource(R.string.play_pip), icon = Icons.Rounded.PictureInPicture) { onPip() }
                        add(stringResource(R.string.play_aspect), aspect.label, PlayerPage.Aspect, Icons.Rounded.AspectRatio)
                        if (quality.isNotEmpty()) add(stringResource(R.string.play_quality), qualityValue, PlayerPage.Quality, Icons.Rounded.HighQuality)
                        if (audio.size > 1) add(stringResource(R.string.play_audio), audioValue, PlayerPage.Audio, Icons.Rounded.Audiotrack)
                        if (subtitles.any { it.group != null }) add(stringResource(R.string.play_subtitles), subtitleValue, PlayerPage.Subtitle, Icons.Rounded.Subtitles)
                    } else {
                        add(stringResource(R.string.play_audio), audioValue, PlayerPage.Audio, Icons.Rounded.Audiotrack)
                        add(stringResource(R.string.play_subtitles), subtitleValue, PlayerPage.Subtitle, Icons.Rounded.Subtitles)
                        add(stringResource(R.string.sub_style_title), stringResource(subtitleStyle.labelRes), PlayerPage.SubStyle, Icons.Rounded.Palette)
                        add(stringResource(R.string.play_sub_size), "%$subtitleScale", PlayerPage.SubSize, Icons.Rounded.FormatSize)
                        if (subtitleSearch != null) add(stringResource(R.string.play_online_subtitles), null, PlayerPage.Online, Icons.Rounded.CloudDownload)
                        add(stringResource(R.string.play_speed), speedLabel(speed), PlayerPage.Speed, Icons.Rounded.Speed)
                        add(stringResource(R.string.play_aspect), aspect.label, PlayerPage.Aspect, Icons.Rounded.AspectRatio)
                        if (quality.isNotEmpty()) add(stringResource(R.string.play_quality), qualityValue, PlayerPage.Quality, Icons.Rounded.HighQuality)
                        if (onPip != null) rows += RowSpec(stringResource(R.string.play_pip), icon = Icons.Rounded.PictureInPicture) { onPip() }
                    }
                    add(stringResource(R.string.play_sleep), sleepLabel(sleepMinutes), PlayerPage.Sleep, Icons.Rounded.Bedtime)
                    add(stringResource(R.string.ux_info), null, PlayerPage.Info, Icons.Rounded.Info)
                    PanelList(rows, mainFocus.coerceIn(0, rows.lastIndex))
                }
                PlayerPage.Aspect -> {
                    val rows = AspectMode.entries.map { m -> RowSpec(m.label, checked = m == aspect) { onAspect(m); back() } }
                    PanelList(rows, aspect.ordinal)
                }
                PlayerPage.Speed -> {
                    val rows = PlaybackSpeeds.map { s -> RowSpec(speedLabel(s), checked = s == speed) { onSpeed(s); back() } }
                    PanelList(rows, PlaybackSpeeds.indexOf(speed).coerceAtLeast(0))
                }
                PlayerPage.Quality -> TrackPage(quality, stringResource(R.string.play_auto), onQuality, ::back)
                PlayerPage.Audio -> TrackPage(audio, stringResource(R.string.play_no_audio), onAudio, ::back)
                PlayerPage.Subtitle -> {
                    val rows = subtitles.map { o -> RowSpec(o.label, checked = o.selected) { onSubtitle(o); back() } } + listOf(
                        RowSpec(stringResource(R.string.sub_style_title), stringResource(subtitleStyle.labelRes)) { page = PlayerPage.SubStyle },
                        RowSpec(stringResource(R.string.play_sub_size), "%$subtitleScale") { page = PlayerPage.SubSize },
                    )
                    PanelList(rows, subtitles.indexOfFirst { it.selected }.coerceAtLeast(0))
                }
                PlayerPage.SubStyle -> {
                    SubtitlePreview(subtitleStyle, subtitleScale)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sub_style_hint), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    PanelList(SubtitleStyle.entries.map { s ->
                        RowSpec(stringResource(s.labelRes), checked = s == subtitleStyle) { onSubtitleStyle(s) }
                    }, subtitleStyle.ordinal)
                }
                PlayerPage.SubSize -> {
                    val rows = SubtitleScales.map { s -> RowSpec("%$s", checked = s == subtitleScale) { onSubtitleScale(s); back() } }
                    PanelList(rows, SubtitleScales.indexOf(subtitleScale).coerceAtLeast(1))
                }
                PlayerPage.Sleep -> {
                    val rows = SleepOptions.map { m -> RowSpec(sleepLabel(m), checked = m == sleepMinutes) { onSleep(m); back() } }
                    PanelList(rows, SleepOptions.indexOf(sleepMinutes).coerceAtLeast(0))
                }
                PlayerPage.Info -> Text(streamInfo.ifBlank { stringResource(R.string.ux_info_unknown) }, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                PlayerPage.Online -> if (subtitleSearch != null) OnlineSubtitlePage(subtitleSearch, onDone = onClose)
            }
        }
    }
}

@Composable
private fun TrackPage(options: List<TrackOption>, emptyText: String, onPick: (TrackOption) -> Unit, back: () -> Unit) {
    if (options.isEmpty()) {
        PanelList(listOf(RowSpec(emptyText) { back() }), 0)
    } else {
        val rows = options.map { o -> RowSpec(o.label, checked = o.selected) { onPick(o); back() } }
        PanelList(rows, options.indexOfFirst { it.selected }.coerceAtLeast(0))
    }
}

@Composable
private fun OnlineSubtitlePage(search: SubtitleSearch, onDone: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.getString(R.string.play_searching)) }
    var hits by remember { mutableStateOf<List<SubtitleHit>>(emptyList()) }
    var chosen by remember { mutableStateOf<SubtitleHit?>(null) }
    LaunchedEffect(Unit) {
        val found = runCatching { search.search() }.getOrNull()
        hits = found.orEmpty()
        status = if (found == null) context.getString(R.string.play_search_failed) else context.getString(R.string.play_no_results)
    }
    LaunchedEffect(chosen) {
        val h = chosen ?: return@LaunchedEffect
        val ok = runCatching { search.download(h) }.getOrDefault(false)
        if (ok) onDone() else { chosen = null; status = context.getString(R.string.play_download_failed) }
    }
    if (hits.isEmpty()) {
        PanelList(listOf(RowSpec(status) {}), 0)
    } else {
        val rows = hits.map { h ->
            RowSpec("[${h.language.uppercase()}] ${h.release.ifBlank { context.getString(R.string.play_subtitles) }}", if (chosen == h) context.getString(R.string.play_downloading) else context.getString(R.string.play_downloads, h.downloads)) {
                if (chosen == null) chosen = h
            }
        }
        PanelList(rows, 0)
    }
}

@Composable
private fun PanelList(rows: List<RowSpec>, startIndex: Int) {
    val state = rememberLazyListState()
    // Reserve the tick column only on choice lists; menu rows show their icon instead of an empty gap.
    val checkSlot = rows.any { it.checked }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.scrollToItem((startIndex - 2).coerceAtLeast(0))
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(rows) { i, row ->
            PanelRow(row, checkSlot, if (i == startIndex) Modifier.focusRequester(focus) else Modifier)
        }
    }
}

@Composable
private fun PanelRow(row: RowSpec, checkSlot: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = row.onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).pointerClick(row.onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.SurfaceHigh,
            contentColor = Palette.Text,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                row.icon != null -> Icon(row.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                row.checked -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                checkSlot -> Spacer(Modifier.width(20.dp))
            }
            if (row.icon != null || checkSlot) Spacer(Modifier.width(12.dp))
            Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.value?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            }
        }
    }
}
