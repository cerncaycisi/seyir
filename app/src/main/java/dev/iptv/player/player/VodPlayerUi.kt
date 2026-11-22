package dev.iptv.player.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.ui.formatClock
import dev.iptv.player.ui.pointerClick

/*
 * Film/dizi oynatıcısı (tasarım v2, docs/BACKLOG.md "Tasarım v2"): zaman çubuğu ana kontrol, ekranda yalnızca
 * oynat/duraklat, "Sonraki bölüm" ve tek "Ses ve altyazı" düğmesi; ses+altyazı+görünüm tek alt panelde;
 * duraklatınca karartılmış bilgi ekranı. Canlı yayın kendi arayüzünü kullanır.
 */

internal val Ink = Color(0xFFF5F5F7)
internal val InkMuted = Color(0xB3F5F5F7)
private val InkFaint = Color(0x80F5F5F7)
internal val Amber = Color(0xFFF2B544)
private val Chip = Color(0x24F5F5F7)

/** "The Last of Us · S1B1 · Pilot" → ("The Last of Us", "S1B1 · Pilot"); movies keep their whole name. */
internal fun splitPlayerTitle(name: String): Pair<String, String?> {
    val i = name.indexOf(" · ")
    return if (i <= 0) name to null else name.substring(0, i) to name.substring(i + 3)
}

@Composable
internal fun VodControls(
    name: String,
    playing: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    speed: Float,
    active: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    onNext: (() -> Unit)?,
    onTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = remember(name) { splitPlayerTitle(name) }
    val playFocus = remember { FocusRequester() }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))))
        Column(Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 26.dp, end = 200.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8000000))))
                .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 26.dp),
        ) {
            ScrubBar(position, buffered, duration, onSeek, onInteraction, onTargetChange = { scrubTarget = it })
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPauseButton(playing, onToggle, Modifier.focusRequester(playFocus))
                Spacer(Modifier.width(14.dp))
                Text(time(scrubTarget ?: position), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink)
                if (scrubTarget != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.vod_scrub_hint), style = MaterialTheme.typography.labelMedium, color = InkFaint)
                }
                Spacer(Modifier.weight(1f))
                if (duration > 0) {
                    val remaining = (duration - position).coerceAtLeast(0)
                    val endsAt = System.currentTimeMillis() / 1000 + (remaining / speed.coerceAtLeast(0.25f)).toLong() / 1000
                    Text("−${time(remaining)} · " + stringResource(R.string.vod_ends_at, formatClock(endsAt)),
                        style = MaterialTheme.typography.labelLarge, color = InkMuted)
                    Spacer(Modifier.width(16.dp))
                }
                if (onNext != null) {
                    PillButton(stringResource(R.string.vod_next_episode), Icons.Rounded.SkipNext, onNext)
                    Spacer(Modifier.width(10.dp))
                }
                RoundIconButton(Icons.Rounded.Subtitles, stringResource(R.string.vod_audio_subtitles), onTracks)
            }
        }
    }
}

/** Focusable timeline: ◀▶ move a draft position in 10 s steps, OK applies it, Back drops it. */
@Composable
private fun ScrubBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    onTargetChange: (Long?) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    fun setTarget(t: Long?) { target = t; onTargetChange(t) }
    val shown = target ?: position
    val label = stringResource(R.string.ux_seek)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused; if (!it.isFocused) setTarget(null) }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) {
                            setTarget(seekPosition(target ?: position, if (event.key == Key.DirectionRight) 10_000 else -10_000, duration))
                            onInteraction()
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyUp) { target?.let(onSeek); setTarget(null); onInteraction() }
                        true
                    }
                    Key.Back -> if (target != null) { if (event.type == KeyEventType.KeyUp) setTarget(null); true } else false
                    else -> false
                }
            }
            .semantics { contentDescription = label }
            .focusable(enabled = duration > 0)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0 && size.width > 0) { onSeek((offset.x / size.width * duration).toLong().coerceIn(0, duration)); onInteraction() }
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        val full = maxWidth
        val frac = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val playedFrac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufFrac = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val track = if (focused) 8.dp else 5.dp
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(track).clip(RoundedCornerShape(4.dp)).background(Color(0x47F5F5F7))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(bufFrac).background(Color(0x6BF5F5F7)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(playedFrac).background(if (focused) Ink else Amber))
            if (target != null) Box(Modifier.fillMaxHeight().width(2.dp).offset(x = full * frac).background(Amber))
        }
        if (focused) {
            // Playhead (or draft position while scrubbing) and its time label.
            Box(Modifier.padding(bottom = 4.dp).offset(x = full * frac - 2.dp).width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Ink))
            if (target != null) {
                val delta = (target ?: position) - position
                Text(
                    time(shown) + "  " + (if (delta >= 0) "+" else "−") + time(kotlin.math.abs(delta)),
                    modifier = Modifier.offset(x = (full * frac - 60.dp).coerceIn(0.dp, full - 120.dp), y = (-28).dp).width(120.dp),
                    style = MaterialTheme.typography.titleSmall.copy(shadow = Shadow(Color.Black, blurRadius = 6f)),
                    fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(46.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x3DF5F5F7), contentColor = Ink,
            focusedContainerColor = Ink, focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Ink), inset = (-4).dp, shape = CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.ux_pause else R.string.ux_play), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Chip, contentColor = Ink, focusedContainerColor = Ink, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
internal fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(40.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Chip, contentColor = Ink, focusedContainerColor = Ink, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Ses + altyazı + görünüm in one bottom sheet; the video keeps playing above it. Back closes. */
@Composable
internal fun AudioSubtitleSheet(
    audio: List<TrackOption>,
    onAudio: (TrackOption) -> Unit,
    subtitles: List<TrackOption>,
    onSubtitle: (TrackOption) -> Unit,
    style: SubtitleStyle,
    onStyle: (SubtitleStyle) -> Unit,
    scale: Int,
    onScale: (Int) -> Unit,
    onOnlineSearch: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val first = remember { FocusRequester() }
    val audioState = androidx.compose.foundation.lazy.rememberLazyListState()
    val subState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) {
        // Long track lists: bring the selected rows into view first, or the focus target is not composed yet.
        audioState.scrollToItem((audio.indexOfFirst { it.selected } - 1).coerceAtLeast(0))
        subState.scrollToItem((subtitles.indexOfFirst { it.selected } - 1).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Color(0xF00E0F12))
            .padding(start = 40.dp, end = 40.dp, top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        SheetColumn(stringResource(R.string.vod_sheet_audio), Modifier.weight(1f), audioState) {
            val start = audio.indexOfFirst { it.selected }.coerceAtLeast(0)
            if (audio.isEmpty()) item { SheetRow(stringResource(R.string.play_no_audio), false, {}, Modifier.focusRequester(first)) }
            itemsIndexed(audio) { i, o ->
                SheetRow(o.label, o.selected, { onAudio(o) }, if (i == start) Modifier.focusRequester(first) else Modifier)
            }
        }
        SheetColumn(stringResource(R.string.vod_sheet_subtitles), Modifier.weight(1f), subState) {
            itemsIndexed(subtitles) { _, o -> SheetRow(o.label, o.selected, { onSubtitle(o) }) }
            if (onOnlineSearch != null) item { SheetRow(stringResource(R.string.play_online_subtitles), false, onOnlineSearch, muted = true) }
        }
        Column(Modifier.weight(1f)) {
            SheetHeader(stringResource(R.string.vod_sheet_look))
            StylePicker(style, onStyle)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.vod_sheet_size), style = MaterialTheme.typography.labelLarge, color = InkMuted, modifier = Modifier.width(52.dp))
                SubtitleScales.forEachIndexed { i, s ->
                    SizeChip(s, (12 + i * 2).sp, s == scale) { onScale(s) }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InkFaint,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SheetColumn(title: String, modifier: Modifier, state: androidx.compose.foundation.lazy.LazyListState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier) {
        SheetHeader(title)
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x1FF5F5F7) else Color.Transparent,
            contentColor = if (muted) InkMuted else Ink,
            focusedContainerColor = Ink, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp)) { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp)) }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePicker(current: SubtitleStyle, onStyle: (SubtitleStyle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubtitleStyle.entries.forEach { s ->
            val selected = s == current
            val sample = when (s) {
                SubtitleStyle.Yellow -> TextStyle(color = Color(0xFFFFE45C), shadow = Shadow(Color.Black, blurRadius = 4f))
                SubtitleStyle.Contrast -> TextStyle(color = Color.White, background = Color.Black)
                SubtitleStyle.Soft -> TextStyle(color = Color.White, shadow = Shadow(Color.Black, blurRadius = 10f))
                else -> TextStyle(color = Color.White, shadow = Shadow(Color.Black, blurRadius = 3f))
            }
            Surface(
                onClick = { onStyle(s) },
                modifier = Modifier.height(36.dp).pointerClick({ onStyle(s) }),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF26282E), focusedContainerColor = Color(0xFF3A3D45)),
                border = ClickableSurfaceDefaults.border(
                    border = if (selected) Border(BorderStroke(2.dp, Amber), shape = RoundedCornerShape(8.dp)) else Border.None,
                    focusedBorder = Border(BorderStroke(2.dp, Ink), shape = RoundedCornerShape(8.dp)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            ) {
                Box(Modifier.fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(s.labelRes), style = MaterialTheme.typography.labelLarge.merge(sample), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SizeChip(scale: Int, size: androidx.compose.ui.unit.TextUnit, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 40.dp, height = 34.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x2EF5F5F7) else Color.Transparent, contentColor = Ink,
            focusedContainerColor = Ink, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize().semantics { contentDescription = "%$scale" }, contentAlignment = Alignment.Center) {
            Text("Aa", fontSize = size, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** Shown after a few seconds of pause: dimmed frame with the title's details, like a streaming service. */
@Composable
internal fun PausedInfo(
    name: String,
    meta: String?,
    plot: String?,
    cast: String?,
    director: String?,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = remember(name) { splitPlayerTitle(name) }
    Box(modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE0000000), 0.45f to Color(0xB3000000), 1f to Color(0x33000000)))) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 54.dp).widthIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vod_watching), style = MaterialTheme.typography.labelLarge, color = InkMuted)
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1) }
            meta?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = InkMuted) }
            plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xE0F5F5F7), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val people = cast?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4).orEmpty()
            if (people.isNotEmpty() || director != null) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { p ->
                        Text(p, style = MaterialTheme.typography.labelMedium, color = Ink,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x1FF5F5F7)).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    director?.let {
                        Text(stringResource(R.string.ui_director) + ": " + it, style = MaterialTheme.typography.labelMedium, color = InkMuted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 54.dp, end = 54.dp, bottom = 30.dp)) {
            val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40F5F5F7))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Ink))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(time(position), style = MaterialTheme.typography.labelLarge, color = InkMuted)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.vod_paused_hint), style = MaterialTheme.typography.labelLarge, color = InkMuted)
                Spacer(Modifier.weight(1f))
                Text("−" + time((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.labelLarge, color = InkMuted)
            }
        }
    }
}
