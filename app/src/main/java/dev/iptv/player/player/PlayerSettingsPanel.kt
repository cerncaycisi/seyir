package dev.iptv.player.player

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
import kotlinx.coroutines.android.awaitFrame

enum class AspectMode(val key: String, val label: String, val ratio: Float?) {
    Fit("FIT", "Sığdır", null),
    Fill("FILL", "Doldur (ger)", null),
    Zoom("ZOOM", "Yakınlaştır (kırp)", null),
    R16x9("16_9", "16:9", 16f / 9f),
    R4x3("4_3", "4:3", 4f / 3f);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Fit
    }
}

val PlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
val SubtitleScales = listOf(75, 100, 125, 150, 200)
val SleepOptions = listOf(0, 15, 30, 60, 90)

fun speedLabel(s: Float) = if (s == 1f) "Normal (1x)" else "${s.toString().removeSuffix(".0")}x"
private fun sleepLabel(m: Int) = if (m == 0) "Kapalı" else "$m dk"

private enum class Page(val title: String) {
    Main("Oynatıcı ayarları"),
    Aspect("Görüntü oranı"),
    Speed("Oynatma hızı"),
    Quality("Video kalitesi"),
    Audio("Ses"),
    Subtitle("Altyazı"),
    SubSize("Altyazı boyutu"),
    Sleep("Uyku zamanlayıcı"),
    Online("Altyazı ara (OpenSubtitles)"),
}

private class RowSpec(val label: String, val value: String? = null, val checked: Boolean = false, val onClick: () -> Unit)

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
) {
    var page by remember { mutableStateOf(Page.Main) }
    var mainFocus by remember { mutableIntStateOf(0) }
    fun back() { page = Page.Main }
    BackHandler { if (page == Page.Main) onClose() else back() }

    val offOrAuto = { o: List<TrackOption> -> o.firstOrNull { it.selected }?.label ?: "—" }

    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Color(0xF2111620))
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        key(page) {
            when (page) {
                Page.Main -> {
                    val rows = ArrayList<RowSpec>()
                    fun add(label: String, value: String?, target: Page) {
                        val i = rows.size
                        rows += RowSpec(label, value) { mainFocus = i; page = target }
                    }
                    if (onBackToLive != null) rows += RowSpec("Canlıya dön") { onBackToLive() }
                    if (onPreviousChannel != null) rows += RowSpec("Önceki kanal") { onPreviousChannel() }
                    if (onChannelList != null) rows += RowSpec("Kanal listesi") { onChannelList() }
                    if (onRecordToggle != null) rows += RowSpec(if (recording) "Kaydı durdur" else "Kaydet", if (recording) "● KAYIT" else null) { onRecordToggle() }
                    if (onMultiView != null) {
                        rows += RowSpec("Çoklu izleme", "2 kanal") { onMultiView(2) }
                        rows += RowSpec("Çoklu izleme", "4 kanal") { onMultiView(4) }
                    }
                    if (onPip != null) rows += RowSpec("Resim içinde resim") { onPip() }
                    add("Görüntü oranı", aspect.label, Page.Aspect)
                    if (!live) add("Oynatma hızı", speedLabel(speed), Page.Speed)
                    add("Video kalitesi", if (quality.isEmpty()) "Otomatik" else offOrAuto(quality), Page.Quality)
                    add("Ses", audio.firstOrNull { it.selected }?.label ?: if (audio.isEmpty()) "Yok" else "—", Page.Audio)
                    add("Altyazı", subtitles.firstOrNull { it.selected }?.label ?: "Kapalı", Page.Subtitle)
                    add("Altyazı boyutu", "%$subtitleScale", Page.SubSize)
                    add("Uyku zamanlayıcı", sleepLabel(sleepMinutes), Page.Sleep)
                    if (subtitleSearch != null) add("Altyazı ara (OpenSubtitles)", null, Page.Online)
                    PanelList(rows, mainFocus.coerceIn(0, rows.lastIndex))
                }
                Page.Aspect -> {
                    val rows = AspectMode.entries.map { m -> RowSpec(m.label, checked = m == aspect) { onAspect(m); back() } }
                    PanelList(rows, aspect.ordinal)
                }
                Page.Speed -> {
                    val rows = PlaybackSpeeds.map { s -> RowSpec(speedLabel(s), checked = s == speed) { onSpeed(s); back() } }
                    PanelList(rows, PlaybackSpeeds.indexOf(speed).coerceAtLeast(0))
                }
                Page.Quality -> TrackPage(quality, "Otomatik", onQuality, ::back)
                Page.Audio -> TrackPage(audio, "Ses izi bulunamadı", onAudio, ::back)
                Page.Subtitle -> TrackPage(subtitles, "Altyazı bulunamadı", onSubtitle, ::back)
                Page.SubSize -> {
                    val rows = SubtitleScales.map { s -> RowSpec("%$s", checked = s == subtitleScale) { onSubtitleScale(s); back() } }
                    PanelList(rows, SubtitleScales.indexOf(subtitleScale).coerceAtLeast(1))
                }
                Page.Sleep -> {
                    val rows = SleepOptions.map { m -> RowSpec(sleepLabel(m), checked = m == sleepMinutes) { onSleep(m); back() } }
                    PanelList(rows, SleepOptions.indexOf(sleepMinutes).coerceAtLeast(0))
                }
                Page.Online -> if (subtitleSearch != null) OnlineSubtitlePage(subtitleSearch, onDone = onClose)
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
    var status by remember { mutableStateOf("Aranıyor…") }
    var hits by remember { mutableStateOf<List<SubtitleHit>>(emptyList()) }
    var chosen by remember { mutableStateOf<SubtitleHit?>(null) }
    LaunchedEffect(Unit) {
        val found = runCatching { search.search() }.getOrNull()
        hits = found.orEmpty()
        status = if (found == null) "Arama başarısız" else "Sonuç bulunamadı"
    }
    LaunchedEffect(chosen) {
        val h = chosen ?: return@LaunchedEffect
        val ok = runCatching { search.download(h) }.getOrDefault(false)
        if (ok) onDone() else { chosen = null; status = "İndirme başarısız" }
    }
    if (hits.isEmpty()) {
        PanelList(listOf(RowSpec(status) {}), 0)
    } else {
        val rows = hits.map { h ->
            RowSpec("[${h.language.uppercase()}] ${h.release}", if (chosen == h) "İndiriliyor…" else "${h.downloads} indirme") {
                if (chosen == null) chosen = h
            }
        }
        PanelList(rows, 0)
    }
}

@Composable
private fun PanelList(rows: List<RowSpec>, startIndex: Int) {
    val state = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.scrollToItem((startIndex - 2).coerceAtLeast(0))
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(rows) { i, row ->
            PanelRow(row, if (i == startIndex) Modifier.focusRequester(focus) else Modifier)
        }
    }
}

@Composable
private fun PanelRow(row: RowSpec, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = row.onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.SurfaceHigh,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.checked) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.value?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            }
        }
    }
}
