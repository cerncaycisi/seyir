package dev.iptv.player.ui

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.epg.Catchup
import dev.iptv.player.epg.EpgService
import dev.iptv.player.epg.EpgStatus
import dev.iptv.player.epg.Programme
import dev.iptv.player.epg.Reminder
import dev.iptv.player.epg.ReminderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val ChannelW = 132.dp
private val RowH = 48.dp
private val RowGap = 4.dp
private const val DpPerMin = 3.2f // 30 min = 96 dp; ~3.3 h visible on the 960 dp canvas
private const val FillerMinutes = 240

private class GuideFocus(val row: Int, val stream: Stream, val prog: Programme, val filler: Boolean)
private class PendingFocus(val row: Int, val anchorMin: Float)

/** Everything a row needs to place cells; one instance per layout so rows skip recomposition otherwise. */
@Stable
private class GuideEnv(
    val playlist: Playlist,
    val winStartSec: Long,
    val totalMin: Float,
    val dpmPx: Float,
    val viewportMin: Float,
    val viewStart: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val visFrom: State<Float>,
) {
    fun minOf(sec: Long) = (sec - winStartSec) / 60f
}

@Composable
fun GuideSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    // Lint fails to resolve ProduceStateScope.value here; the producer assigns it directly.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<Content>>(Load.Loading, playlist.id) {
        value = loadCatching { repo.content(playlist, ContentType.LIVE) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> if (s.value.items.isEmpty()) EmptyState(Icons.Rounded.History, stringResource(R.string.play_guide_no_channels)) else Guide(playlist, s.value.items, actions)
    }
}

@Composable
private fun Guide(playlist: Playlist, allChannels: List<Stream>, actions: HomeActions) {
    val epg = rememberEpg()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val nowSec = rememberNowSec()
    val gen by epg.generation.collectAsState()
    val status by epg.status.collectAsState()
    val reminders by epg.reminderIds.collectAsState(initial = emptySet())
    val recordings by remember { dev.iptv.player.record.RecordingDb.get(context).dao().observeAll() }.collectAsState(initial = emptyList())
    // These producers assign their own value after the suspend calls complete.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val updated by produceState<Long?>(null, gen, status) { value = epg.lastUpdated() }

    var epgOnly by rememberSaveable { mutableStateOf(false) }
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val withEpg by produceState<Set<String>?>(null, allChannels, gen) {
        value = allChannels.filter { epg.resolve(playlist.id, it) != null }.map { it.id }.toSet()
    }
    val channels = remember(allChannels, epgOnly, withEpg) {
        val ids = withEpg
        if (epgOnly && ids != null) allChannels.filter { it.id in ids } else allChannels
    }

    // Timeline: stored window (-2 .. +7 days), measured in minutes from winStart.
    val winStartSec = remember { (EpgService.nowSec() - EpgService.WINDOW_PAST_DAYS * 86400) / 1800 * 1800 }
    val totalMin = (EpgService.WINDOW_PAST_DAYS + EpgService.WINDOW_FUTURE_DAYS) * 1440f
    val initialNowMin = remember { (EpgService.nowSec() - winStartSec) / 60f }
    val viewStart = remember { Animatable((initialNowMin - 30f).coerceAtLeast(0f)) }
    val visFrom = remember { derivedStateOf { floor(viewStart.value / 60f) * 60f } }

    val listState = rememberLazyListState()
    var focused by remember { mutableStateOf<GuideFocus?>(null) }
    var pending by remember { mutableStateOf<PendingFocus?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<GuideFocus?>(null) }
    var recordFor by remember { mutableStateOf<GuideFocus?>(null) }
    var matchFor by remember { mutableStateOf<Stream?>(null) }
    var askedNotifications by remember { mutableStateOf(false) }
    var askExactAlarmAccess by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = context.getString(R.string.play_guide_no_permission)
    }

    LaunchedEffect(playlist.id) { epg.requestFresh(playlist) }

    val restore = actions.restore
    LaunchedEffect(Unit) {
        val idx = restore.itemId?.let { id -> channels.indexOfFirst { it.id == id } } ?: -1
        val row = max(idx, 0)
        if (row > 1) listState.scrollToItem(row - 1)
        pending = PendingFocus(row, initialNowMin)
        if (restore.itemId != null || restore.initial) restore.done()
    }

    fun ensureVisible(startMin: Float, endMin: Float, viewportMin: Float) {
        val vs = viewStart.targetValue
        val visible = min(endMin, vs + viewportMin) - max(startMin, vs)
        if (visible >= min(endMin - startMin, 25f)) return
        scope.launch { viewStart.animateTo(startMin.coerceIn(0f, max(totalMin - viewportMin, 0f)), tween(220)) }
    }

    fun toggleReminder(stream: Stream, p: Programme) {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications && !dev.iptv.player.epg.ReminderScheduler.canNotify(context)) {
            askedNotifications = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            val set = epg.toggleReminder(playlist, stream, p)
            message = if (set) context.getString(R.string.play_guide_reminder_set, p.title) else context.getString(R.string.play_guide_reminder_removed)
        }
    }

    fun playLive(stream: Stream) {
        val idx = channels.indexOf(stream)
        if (idx >= 0) actions.open(channels, idx)
    }

    fun playPast(stream: Stream, p: Programme) {
        val now = EpgService.nowSec()
        val copy = if (Catchup.playable(playlist, stream, p, now)) Catchup.streamFor(playlist, stream, p, now) else null
        if (copy != null) actions.open(listOf(copy), 0) else message = context.getString(R.string.play_guide_no_catchup)
    }

    fun activate(f: GuideFocus) {
        val now = EpgService.nowSec()
        when {
            f.filler || (f.prog.start <= now && f.prog.stop > now) -> playLive(f.stream)
            f.prog.stop <= now -> playPast(f.stream, f.prog)
            else -> toggleReminder(f.stream, f.prog)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dpmPx = with(density) { DpPerMin.dp.toPx() }
        val viewportPx = constraints.maxWidth - with(density) { ChannelW.toPx() }
        val viewportMin = (viewportPx / dpmPx).coerceAtLeast(30f)
        val env = remember(playlist, winStartSec, dpmPx, viewportMin) {
            GuideEnv(playlist, winStartSec, totalMin, dpmPx, viewportMin, viewStart, visFrom)
        }

        Column(Modifier.fillMaxSize()) {
            GuideHeader(focused, nowSec.value, status, updated, message, playlist, reminders)
            Spacer(Modifier.height(8.dp))
            Ruler(env)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val dir = when (e.key) {
                            Key.DirectionDown -> 1
                            Key.DirectionUp -> -1
                            else -> return@onPreviewKeyEvent false
                        }
                        val f = focused ?: return@onPreviewKeyEvent false
                        val target = f.row + dir
                        if (target !in channels.indices) return@onPreviewKeyEvent false
                        // Vertical moves keep the time position instead of relying on geometric focus search.
                        val anchor = max(env.minOf(f.prog.start), viewStart.targetValue)
                        pending = PendingFocus(target, anchor)
                        scope.launch {
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == target }
                            if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset) {
                                if (dir > 0) listState.animateScrollToItem(max(0, target - (info.visibleItemsInfo.size - 2)))
                                else listState.animateScrollToItem(target)
                            }
                        }
                        true
                    },
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                    contentPadding = PaddingValues(bottom = 40.dp),
                ) {
                    itemsIndexed(channels, key = { _, s -> s.id }) { index, stream ->
                        GuideRow(
                            index = index,
                            stream = stream,
                            env = env,
                            epg = epg,
                            gen = gen,
                            nowSec = nowSec.value,
                            reminders = reminders,
                            highlighted = focused?.row == index,
                            pending = pending,
                            onPendingDone = { pending = null },
                            onFocus = { f ->
                                focused = f
                                message = null
                                ensureVisible(env.minOf(f.prog.start), env.minOf(f.prog.stop), env.viewportMin)
                            },
                            onActivate = { activate(it) },
                            onMenu = { menu = it },
                        )
                    }
                }
                // Red "now" line over the timeline area only.
                Box(Modifier.fillMaxSize().padding(start = ChannelW).clipToBounds()) {
                    Box(
                        Modifier
                            .offset { IntOffset(((env.minOf(nowSec.value) - viewStart.value) * dpmPx).roundToInt(), 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Palette.Live),
                    )
                }
            }
        }
    }

    menu?.let { f ->
        val now = EpgService.nowSec()
        val options = buildList {
            if (!f.filler) {
                when {
                    f.prog.start > now -> {
                        val set = Reminder.idOf(playlist.id, f.stream.id, f.prog.start) in reminders
                        add(DialogOption(if (set) context.getString(R.string.play_guide_reminder_remove) else context.getString(R.string.play_guide_reminder_add)) { toggleReminder(f.stream, f.prog) })
                        val planned = recordings.firstOrNull {
                            it.id == dev.iptv.player.record.Recording.idOf(playlist.id, f.stream.id, f.prog.start * 1000) &&
                                it.status == dev.iptv.player.record.RecStatus.SCHEDULED
                        }
                        add(DialogOption(if (planned != null) context.getString(R.string.play_guide_record_cancel) else context.getString(R.string.play_guide_record_schedule)) {
                            scope.launch {
                                message = if (planned != null) {
                                    dev.iptv.player.record.RecordingManager.cancel(context, planned)
                                    context.getString(R.string.play_guide_schedule_removed)
                                } else {
                                    val outcome = dev.iptv.player.record.RecordingManager.schedule(context, playlist, f.stream, f.prog.title, f.prog.start, f.prog.stop)
                                    askExactAlarmAccess = outcome.requiresExactAlarmPermission
                                    outcome.message
                                }
                            }
                        })
                    }
                    f.prog.stop > now -> add(DialogOption(context.getString(R.string.play_guide_record_now)) {
                        recordFor = f
                    })
                    f.prog.stop <= now && Catchup.playable(playlist, f.stream, f.prog, now) ->
                        add(DialogOption(context.getString(R.string.play_guide_catchup)) { playPast(f.stream, f.prog) })
                }
            }
            add(DialogOption(context.getString(R.string.play_guide_live)) { playLive(f.stream) })
            add(DialogOption(context.getString(R.string.play_guide_match)) { matchFor = f.stream })
            add(DialogOption(context.getString(R.string.play_guide_refresh)) { scope.launch { epg.refresh(playlist) } })
            add(DialogOption(if (epgOnly) context.getString(R.string.play_guide_all_channels) else context.getString(R.string.play_guide_epg_channels)) { epgOnly = !epgOnly })
        }
        OptionsDialog(f.stream.name, f.prog.title.takeUnless { f.filler }, options) { menu = null }
    }
    matchFor?.let { s -> EpgMatchDialog(playlist, s, onDismiss = { matchFor = null }) }
    recordFor?.let { target ->
        RecordingDurationDialog(
            channelName = target.stream.name,
            programmeEndSec = target.prog.stop,
            onSelectEnd = { endSec ->
                recordFor = null
                scope.launch {
                    message = dev.iptv.player.record.RecordingManager.startNow(context, playlist, target.stream, target.prog.title, endSec).message
                }
            },
            onDismiss = { recordFor = null },
        )
    }
    if (askExactAlarmAccess) {
        ConfirmDialog(
            title = stringResource(R.string.play_alarm_access_title),
            message = stringResource(R.string.play_alarm_access_message),
            confirmLabel = stringResource(R.string.play_alarm_access_open),
            onConfirm = {
                askExactAlarmAccess = false
                if (!dev.iptv.player.record.RecordingManager.requestExactAlarmPermission(context)) {
                    message = context.getString(R.string.play_alarm_access_unavailable)
                }
            },
            onDismiss = { askExactAlarmAccess = false },
        )
    }
}

@Composable
private fun GuideHeader(
    focus: GuideFocus?,
    nowSec: Long,
    status: EpgStatus,
    updated: Long?,
    message: String?,
    playlist: Playlist,
    reminders: Set<String>,
) {
    val context = LocalContext.current
    val statusText = when (status) {
        is EpgStatus.Refreshing -> context.getString(R.string.play_guide_refreshing, if (status.programmes > 0) "(${status.programmes})" else "")
        is EpgStatus.Error -> context.getString(R.string.play_guide_error, status.message)
        EpgStatus.Idle -> updated?.let { context.getString(R.string.play_guide_updated, formatDayTime(it)) } ?: context.getString(R.string.play_guide_no_data)
    }
    Column(Modifier.fillMaxWidth().height(96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.play_guide_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Spacer(Modifier.width(12.dp))
            Text(
                statusText, style = MaterialTheme.typography.labelMedium,
                color = if (status is EpgStatus.Error) Palette.Live else Palette.TextFaint,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(context.getString(R.string.play_guide_controls), style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(6.dp))
        if (focus == null) return@Column
        val p = focus.prog
        if (focus.filler) {
            Text(focus.stream.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1)
            Text(message ?: context.getString(R.string.play_guide_match_hint), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            return@Column
        }
        val live = p.start <= nowSec && p.stop > nowSec
        val past = p.stop <= nowSec
        val hasReminder = Reminder.idOf(playlist.id, focus.stream.id, p.start) in reminders
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                live -> Pill(context.getString(R.string.play_guide_now), color = Palette.Live, contentColor = Color.White)
                past -> Pill(context.getString(R.string.play_guide_past), color = Palette.SurfaceHigh)
                else -> Pill(if (hasReminder) context.getString(R.string.play_guide_reminder) else context.getString(R.string.play_guide_next), color = if (hasReminder) Palette.Gold else Palette.SurfaceHigh, contentColor = if (hasReminder) Color.Black else Palette.Text)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${focus.stream.name}  ·  ${formatClock(p.start)} – ${formatClock(p.stop)}" + (p.category?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val hint = message ?: p.description
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Ruler(env: GuideEnv) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val clock24h = LocalClock24h.current
    val labelTime = remember(locale, clock24h) { SimpleDateFormat(if (clock24h) "HH:mm" else "h:mm a", locale) }
    val labelDay = remember(locale) { SimpleDateFormat("EEE d MMM", locale) }
    val midnightFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    Row(Modifier.fillMaxWidth().height(22.dp)) {
        Spacer(Modifier.width(ChannelW))
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val first = (env.visFrom.value / 30f).toInt() * 30
            val last = (env.visFrom.value + env.viewportMin + 60f).toInt()
            var m = first
            while (m <= last) {
                val minute = m
                androidx.compose.runtime.key(minute) {
                    val date = Date((env.winStartSec + minute * 60L) * 1000)
                    val time = labelTime.format(date)
                    val midnight = midnightFormat.format(date) == "00:00"
                    Text(
                        if (midnight) labelDay.format(date) else time,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (midnight) FontWeight.Bold else FontWeight.Normal,
                        color = if (midnight) Palette.Primary else Palette.TextMuted,
                        maxLines = 1,
                        modifier = Modifier.offset { IntOffset(((minute - env.viewStart.value) * env.dpmPx).roundToInt() + 6.dp.roundToPx(), 0) },
                    )
                }
                m += 30
            }
        }
    }
}

private class Cell(val prog: Programme, val filler: Boolean)

@Composable
private fun GuideRow(
    index: Int,
    stream: Stream,
    env: GuideEnv,
    epg: EpgService,
    gen: Int,
    nowSec: Long,
    reminders: Set<String>,
    highlighted: Boolean,
    pending: PendingFocus?,
    onPendingDone: () -> Unit,
    onFocus: (GuideFocus) -> Unit,
    onActivate: (GuideFocus) -> Unit,
    onMenu: (GuideFocus) -> Unit,
) {
    val context = LocalContext.current
    // Lint misses this direct ProduceStateScope.value assignment.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val progs by produceState<List<Programme>?>(null, stream.id, gen) { value = epg.programmes(env.playlist.id, stream) }
    val archive = remember(stream) { Catchup.available(env.playlist, stream) }
    val requesters = remember { HashMap<Long, FocusRequester>() }

    // Compose only programmes near the viewport (half a screen of margin so focus can step into neighbours).
    val margin = env.viewportMin * 0.5f + 30f
    val from = env.visFrom.value - margin
    val to = env.visFrom.value + env.viewportMin + 60f + margin
    val noProgramme = stringResource(R.string.play_guide_no_programme)
    val cells = remember(progs, from, to, noProgramme) {
        val list = progs
        when {
            list == null -> emptyList()
            list.isEmpty() -> {
                val b0 = floor(max(from, 0f) / FillerMinutes).toInt()
                val b1 = ceil(min(to, env.totalMin) / FillerMinutes).toInt()
                (b0 until b1).map { b ->
                    val s = env.winStartSec + b * FillerMinutes * 60L
                    Cell(Programme("", s, s + FillerMinutes * 60L, noProgramme), true)
                }
            }
            else -> list.filter { env.minOf(it.start) < to && env.minOf(it.stop) > from }.map { Cell(it, false) }
        }
    }

    LaunchedEffect(pending, cells) {
        val pf = pending ?: return@LaunchedEffect
        if (pf.row != index || cells.isEmpty()) return@LaunchedEffect
        val target = cells.firstOrNull { env.minOf(it.prog.start) <= pf.anchorMin && pf.anchorMin < env.minOf(it.prog.stop) }
            ?: cells.firstOrNull { env.minOf(it.prog.start) >= pf.anchorMin }
            ?: cells.last()
        withFrameNanos { }
        withFrameNanos { }
        val fr = requesters[target.prog.start] ?: return@LaunchedEffect
        if (runCatching { fr.requestFocus() }.isSuccess) onPendingDone()
    }

    Row(Modifier.fillMaxWidth().height(RowH)) {
        Row(Modifier.width(ChannelW).fillMaxHeight().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoTile(stream.name, stream.logo, Modifier.size(width = 38.dp, height = 28.dp), MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            Text(
                stream.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) Palette.Text else Palette.TextMuted,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            for (cell in cells) {
                val p = cell.prog
                androidx.compose.runtime.key(p.start) {
                    val fr = requesters.getOrPut(p.start) { FocusRequester() }
                    GuideCell(
                        cell = cell,
                        env = env,
                        nowSec = nowSec,
                        hasReminder = !cell.filler && Reminder.idOf(env.playlist.id, stream.id, p.start) in reminders,
                        replayable = archive && !cell.filler && p.stop <= nowSec && p.start >= nowSec - Catchup.days(stream) * 86400L,
                        modifier = Modifier.focusRequester(fr),
                        onFocus = { onFocus(GuideFocus(index, stream, p, cell.filler)) },
                        onClick = { onActivate(GuideFocus(index, stream, p, cell.filler)) },
                        onLongClick = { onMenu(GuideFocus(index, stream, p, cell.filler)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideCell(
    cell: Cell,
    env: GuideEnv,
    nowSec: Long,
    hasReminder: Boolean,
    replayable: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = cell.prog
    val startMin = env.minOf(p.start)
    val durMin = (p.stop - p.start) / 60f
    val live = !cell.filler && p.start <= nowSec && p.stop > nowSec
    val past = !cell.filler && p.stop <= nowSec
    val shape = RoundedCornerShape(8.dp)
    val container = when {
        cell.filler -> Palette.Surface
        live -> Palette.Primary.copy(alpha = 0.28f)
        past -> Palette.Surface
        else -> Palette.SurfaceHigh
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .offset { IntOffset(((startMin - env.viewStart.value) * env.dpmPx).roundToInt(), 0) }
            .width(((durMin * DpPerMin).dp - 2.dp).coerceAtLeast(6.dp))
            .height(RowH)
            .pointerClick(onClick, onLongClick)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = if (past || cell.filler) Palette.TextMuted else Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Keep the title readable while the left part of a long programme is scrolled off-screen.
            Column(
                Modifier
                    .layout { measurable, c ->
                        val shift = ((env.viewStart.value - startMin) * env.dpmPx)
                            .coerceIn(0f, (c.maxWidth - 72.dp.roundToPx()).coerceAtLeast(0).toFloat()).roundToInt()
                        val placeable = measurable.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth - shift).coerceAtLeast(0)))
                        layout(c.maxWidth, placeable.height) { placeable.placeRelative(shift, 0) }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    p.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!cell.filler) {
                    Text(
                        "${formatClock(p.start)} – ${formatClock(p.stop)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                        maxLines = 1,
                    )
                }
            }
            if (hasReminder || replayable) {
                Icon(
                    if (hasReminder) Icons.Rounded.Notifications else Icons.Rounded.History,
                    contentDescription = null,
                    tint = if (hasReminder) Palette.Gold else Palette.TextFaint,
                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(14.dp),
                )
            }
        }
    }
}
