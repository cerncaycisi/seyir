package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Stream
import dev.iptv.player.epg.Catchup
import dev.iptv.player.epg.EpgChannel
import dev.iptv.player.epg.EpgService
import dev.iptv.player.epg.Programme
import dev.iptv.player.epg.ReminderBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberEpg(): EpgService {
    val context = LocalContext.current.applicationContext
    return remember { EpgService.get(context) }
}

/** Wall-clock seconds, refreshed every [intervalMs]; read the State inside layout/draw lambdas to avoid recomposition. */
@Composable
fun rememberNowSec(intervalMs: Long = 30_000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            now.longValue = System.currentTimeMillis() / 1000
        }
    }
    return now
}

@Composable
fun formatDayTime(epochSeconds: Long): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val pattern = if (LocalClock24h.current) "d MMM HH:mm" else "d MMM h:mm a"
    return SimpleDateFormat(pattern, locale).format(Date(epochSeconds * 1000))
}

@Composable
fun EpgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Palette.Primary.copy(alpha = 0.35f) else Palette.SurfaceHigh,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DialogPanel(width: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(20.dp),
        ) { content() }
    }
}

class DialogOption(val label: String, val onClick: () -> Unit)

/** Long-press menu: a short list of actions, first one focused. */
@Composable
fun OptionsDialog(title: String, subtitle: String?, options: List<DialogOption>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    DialogPanel(380.dp, onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                EpgButton(o.label, { onDismiss(); o.onClick() }, if (i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
}

/** Searchable picker that pins a playlist channel to an XMLTV channel. */
@Composable
fun EpgMatchDialog(playlist: Playlist, stream: Stream, onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    val uiContext = LocalContext.current
    val epg = rememberEpg()
    var query by remember { mutableStateOf("") }
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val current by produceState<EpgChannel?>(null, stream.id) { value = epg.currentMatch(playlist.id, stream) }
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val results by produceState(emptyList<EpgChannel>(), query) {
        delay(150)
        value = epg.searchChannels(query)
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }

    DialogPanel(520.dp, onDismiss) {
        Text(uiContext.getString(R.string.ui_epg_match), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text(
            "${stream.name}  →  ${current?.name ?: uiContext.getString(R.string.ui_no_match)}",
            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(query, { query = it }, uiContext.getString(R.string.ui_epg_search), leadingIcon = Icons.Rounded.Search)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "auto") {
                EpgButton(uiContext.getString(R.string.ui_epg_auto), {
                    epgLaunch(epg) { epg.setOverride(playlist.id, stream.id, null) }
                    onChanged(); onDismiss()
                }, Modifier.focusRequester(first))
            }
            items(results, key = { it.id }) { c ->
                EpgButton(if (c.name == c.id) c.name else "${c.name}  ·  ${c.id}", {
                    epgLaunch(epg) { epg.setOverride(playlist.id, stream.id, c.id) }
                    onChanged(); onDismiss()
                }, primary = c.id == current?.id)
            }
        }
        if (results.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(uiContext.getString(R.string.ui_epg_empty), style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

private fun epgLaunch(epg: EpgService, block: suspend () -> Unit) {
    epg.scope.launch { block() }
}

/** Finished programmes of an archive channel, newest first. */
@Composable
fun PastBroadcastsDialog(playlist: Playlist, stream: Stream, onPlay: (Programme) -> Unit, onDismiss: () -> Unit) {
    val uiContext = LocalContext.current
    val epg = rememberEpg()
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val list by produceState<List<Programme>?>(null, stream.id) {
        value = epg.past(playlist.id, stream, Catchup.days(stream)).filter { Catchup.playable(playlist, stream, it, EpgService.nowSec()) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(list) { if (!list.isNullOrEmpty()) runCatching { first.requestFocus() } }
    DialogPanel(520.dp, onDismiss) {
        Text(uiContext.getString(R.string.ui_past), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text(
            uiContext.getString(R.string.ui_past_days, stream.name, Catchup.days(stream)),
            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        val l = list
        when {
            l == null -> Text(uiContext.getString(R.string.ui_loading), color = Palette.TextMuted)
            l.isEmpty() -> Text(uiContext.getString(R.string.ui_past_empty), style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
            else -> LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(l.size, key = { l[it].start }) { i ->
                    val p = l[i]
                    EpgButton("${formatDayTime(p.start)}   ${p.title}", { onDismiss(); onPlay(p) }, if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

/** In-app reminder banner. Place once at the app root so it also shows over the player. */
@Composable
fun ReminderHost() {
    val uiContext = LocalContext.current
    var current by remember { mutableStateOf<dev.iptv.player.epg.ReminderEvent?>(null) }
    LaunchedEffect(Unit) { ReminderBus.fired.collect { current = it } }
    val event = current ?: return
    LaunchedEffect(event) {
        delay(60_000)
        if (current === event) current = null
    }
    val watch = remember(event) { FocusRequester() }
    LaunchedEffect(event) { delay(100); runCatching { watch.requestFocus() } }
    DialogPanel(420.dp, onDismiss = { current = null }) {
        Text(uiContext.getString(R.string.ui_starting), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Palette.Primary)
        Spacer(Modifier.height(6.dp))
        Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(event.channelName, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EpgButton(uiContext.getString(R.string.ui_watch), {
                ReminderBus.tune.value = dev.iptv.player.epg.TuneRequest(event.playlistId, event.streamId)
                current = null
            }, Modifier.weight(1f).focusRequester(watch), primary = true)
            EpgButton(uiContext.getString(R.string.ui_close), { current = null }, Modifier.weight(1f))
        }
    }
}
