package dev.iptv.player.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Stream
import dev.iptv.player.epg.Programme
import dev.iptv.player.epg.Reminder
import dev.iptv.player.ui.LogoTile
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.pointerClick
import dev.iptv.player.ui.rememberEpg
import dev.iptv.player.ui.rememberNowSec
import dev.iptv.player.ui.formatClock
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * Pure filter for the live programme guide panel: keeps recent (up to 1h back), current and upcoming
 * programmes over the next 24h, falling back to [fallbackEpg] when XMLTV has no entries for the channel.
 */
internal fun visibleGuideProgrammes(
    all: List<Programme>,
    fallbackEpg: List<EpgEntry>,
    nowSec: Long,
    limit: Int = 36,
): List<Programme> {
    val window = all.filter { it.stop > nowSec - 3600 && it.start < nowSec + 24 * 3600 }.take(limit)
    if (window.isNotEmpty()) return window
    return fallbackEpg.map { EpgEntry ->
        Programme(
            channelId = "",
            start = EpgEntry.start,
            stop = EpgEntry.end,
            title = EpgEntry.title,
            description = EpgEntry.description,
        )
    }
}

/**
 * Right-side live programme guide & info panel (design v2): channel identity, current & upcoming EPG with
 * descriptions, reminder toggling on future programmes, and ◀ / ▶ channel switching without closing.
 */
@Composable
internal fun LiveInfoPanel(
    playlist: Playlist,
    number: Int,
    stream: Stream,
    favorite: Boolean,
    fallbackEpg: List<EpgEntry>,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToast: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val epg = rememberEpg()
    val nowSec by rememberNowSec(30_000)
    val reminders by epg.reminderIds.collectAsState(initial = emptySet())
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val rawProgrammes by produceState(emptyList<Programme>(), playlist.id, stream.id) {
        value = emptyList()
        value = runCatching { epg.programmes(playlist.id, stream) }.getOrDefault(emptyList())
    }
    val programmes = remember(rawProgrammes, fallbackEpg, nowSec / 60) {
        visibleGuideProgrammes(rawProgrammes, fallbackEpg, nowSec)
    }
    val currentIdx = remember(programmes, nowSec / 60) {
        programmes.indexOfFirst { it.start <= nowSec && it.stop > nowSec }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(stream.id, programmes.size) {
        if (programmes.isNotEmpty()) {
            listState.scrollToItem((currentIdx - 1).coerceAtLeast(0))
            awaitFrame()
            runCatching { focus.requestFocus() }
        }
    }

    val panelBg = remember { Brush.horizontalGradient(listOf(Color(0xE607080A), Color(0xF807080A))) }
    Column(
        modifier
            .fillMaxHeight()
            .width(460.dp)
            .background(panelBg)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onPrevChannel(); true }
                    Key.DirectionRight -> { onNextChannel(); true }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoTile(stream.name, stream.logo, Modifier.size(width = 64.dp, height = 40.dp), MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = InkMuted)
                    Text(
                        stream.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.live_guide_hint), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (programmes.isEmpty()) {
            Surface(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).pointerClick(onClose),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x14F5F5F7),
                    contentColor = InkMuted,
                    focusedContainerColor = Ink,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Text(
                    stringResource(R.string.ui_no_programme_info),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(programmes, key = { i, p -> "$i|${p.start}" }) { i, p ->
                    val isNow = p.start <= nowSec && p.stop > nowSec
                    val isFuture = p.start > nowSec
                    val hasReminder = isFuture && Reminder.idOf(playlist.id, stream.id, p.start) in reminders
                    GuideProgrammeRow(
                        programme = p,
                        isNow = isNow,
                        isFuture = isFuture,
                        hasReminder = hasReminder,
                        nowSec = nowSec,
                        onClick = {
                            if (isFuture) {
                                scope.launch {
                                    val set = epg.toggleReminder(playlist, stream, p)
                                    onToast(
                                        if (set) context.getString(R.string.live_reminder_set, p.title)
                                        else context.getString(R.string.live_reminder_removed),
                                    )
                                }
                            } else {
                                onClose()
                            }
                        },
                        modifier = if (i == currentIdx) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideProgrammeRow(
    programme: Programme,
    isNow: Boolean,
    isFuture: Boolean,
    hasReminder: Boolean,
    nowSec: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pointerClick(onClick)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isNow) Color(0x24F2B544) else Color(0x14F5F5F7),
            contentColor = Ink,
            focusedContainerColor = Ink,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatClock(programme.start)} – ${formatClock(programme.stop)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (focused) Color(0xFF33353A) else InkMuted,
                )
                if (isNow) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ui_now),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Amber)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (hasReminder) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (focused) Color.Black else Amber,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                programme.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isNow || focused) FontWeight.Bold else FontWeight.SemiBold,
                color = if (focused) Color.Black else Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isNow && programme.stop > programme.start) {
                Spacer(Modifier.height(6.dp))
                val frac = ((nowSec - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (focused) Color(0x26000000) else Color(0x33F5F5F7)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(if (focused) Color.Black else Amber),
                    )
                }
            }
            val desc = programme.description?.takeIf { it.isNotBlank() }
            if (desc != null && (isNow || focused || !isFuture)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focused) Color(0xFF2B2D33) else InkMuted,
                    maxLines = if (focused || isNow) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
