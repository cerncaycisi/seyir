package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.data.Category
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.epg.Catchup
import dev.iptv.player.epg.Programme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay

private const val ALL = "*"

@Composable
fun LiveSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<Content>>(Load.Loading, playlist.id) {
        value = loadCatching { repo.content(playlist, ContentType.LIVE) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> LiveBrowser(repo, playlist, s.value, actions)
    }
}

@Composable
private fun LiveBrowser(repo: Repository, playlist: Playlist, content: Content, actions: HomeActions) {
    val uiContext = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val rules = actions.rules
    // Hidden channels/categories and adult names are removed; locked categories stay listed (with a lock).
    val base = remember(content, rules.hiddenChannels, rules.hiddenCategories, rules.hideAdult) {
        rules.filterBase(playlist, ContentType.LIVE, content)
    }
    val counts = remember(base) { base.items.groupingBy { it.categoryId }.eachCount() }
    val categories = remember(uiContext, base) {
        listOf(Category(ALL, uiContext.getString(R.string.ui_all))) + base.categories.filter { (counts[it.id] ?: 0) > 0 }
    }
    if (selected != ALL && categories.none { it.id == selected }) selected = ALL
    val lockedSelected = selected != ALL && rules.isCategoryLocked(playlist, ContentType.LIVE, selected)
    if (lockedSelected) selected = ALL
    val items = remember(base, selected, rules.lockedCategories, rules.unlocked) {
        if (selected == ALL) rules.unlockedItems(playlist, ContentType.LIVE, base.items) else base.items.filter { it.categoryId == selected }
    }
    val categoryName = categories.firstOrNull { it.id == selected }?.name ?: uiContext.getString(R.string.ui_all)
    val listState = rememberLazyListState()
    val itemFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf<Stream?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val epgSvc = rememberEpg()
    val gen by epgSvc.generation.collectAsState()
    val nowSec by rememberNowSec(60_000)
    var showMatch by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }

    val restore = actions.restore
    val restoreIndex = restore.itemId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
    LaunchedEffect(Unit) {
        if (restoreIndex >= 0) {
            listState.scrollToItem(restoreIndex)
            withFrameNanos { }
            runCatching { itemFocus.requestFocus() }
        } else if (restore.initial) {
            withFrameNanos { }
            runCatching { categoryFocus.requestFocus() }
        }
        if (restore.itemId != null || restore.initial) restore.done()
    }
    LaunchedEffect(playlist.id) { epgSvc.requestFresh(playlist) }
    LaunchedEffect(focused, gen) {
        epg = emptyList()
        val f = focused ?: return@LaunchedEffect
        delay(350)
        epg = epgSvc.nowAndNext(playlist.id, f, 2).map { EpgEntry(it.title, it.start, it.stop) }
            .ifEmpty { runCatching { repo.shortEpg(playlist, f) }.getOrDefault(emptyList()) }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(190.dp).fillMaxHeight()) {
            ScreenTitle(uiContext.getString(R.string.ui_live_tv))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(categories) { i, c ->
                    CategoryItem(
                        name = c.name,
                        count = if (c.id == ALL) base.items.size else counts[c.id],
                        selected = c.id == selected,
                        locked = c.id != ALL && rules.isCategoryMarkedLocked(playlist, ContentType.LIVE, c.id),
                        onClick = {
                            if (c.id != ALL && rules.isCategoryLocked(playlist, ContentType.LIVE, c.id)) {
                                actions.requireUnlock { selected = c.id }
                            } else {
                                selected = c.id
                            }
                        },
                        onLongClick = if (c.id == ALL) null else ({ actions.categoryLongPress(ContentType.LIVE, c.id, c.name) }),
                        modifier = if (i == 0) Modifier.focusRequester(categoryFocus) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Spacer(Modifier.height(4.dp))
            SectionTitle(categoryName, uiContext.getString(R.string.ui_channel_count, items.size))
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Tv, uiContext.getString(R.string.ui_channels_empty))
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                ) {
                    itemsIndexed(items) { i, s ->
                        // Lint misses the direct ProduceStateScope.value assignment in this producer.
                        @SuppressLint("ProduceStateDoesNotAssignValue")
                        val nowProg by produceState<Programme?>(null, s.id, gen, nowSec / 60) { value = epgSvc.nowPlaying(playlist.id, s, nowSec) }
                        ChannelRow(
                            number = i + 1,
                            stream = s,
                            favorite = favoriteKey(playlist, s) in actions.favorites,
                            onClick = { actions.open(items, i) },
                            onLongClick = { actions.longPress(s, categories.firstOrNull { it.id == s.categoryId }?.name) },
                            onFocused = { focused = s },
                            nowTitle = nowProg?.title,
                            progress = nowProg?.let { (nowSec - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                            modifier = if (i == restoreIndex) Modifier.focusRequester(itemFocus) else Modifier,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        ChannelPreview(focused, epg, archive = focused?.let { Catchup.available(playlist, it) } == true, onMatch = { showMatch = true }, onPast = { showPast = true }, modifier = Modifier.width(260.dp).fillMaxHeight().padding(bottom = 24.dp))
    }
    if (showMatch) focused?.let { s -> EpgMatchDialog(playlist, s, onDismiss = { showMatch = false }) }
    if (showPast) focused?.let { s ->
        PastBroadcastsDialog(playlist, s, onPlay = { p ->
            Catchup.streamFor(playlist, s, p)?.let { actions.open(listOf(it), 0) }
        }, onDismiss = { showPast = false })
    }
}

@Composable
private fun ChannelPreview(stream: Stream?, epg: List<EpgEntry>, archive: Boolean, onMatch: () -> Unit, onPast: () -> Unit, modifier: Modifier = Modifier) {
    val uiContext = LocalContext.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(16.dp)) {
        if (stream == null) {
            EmptyState(Icons.Rounded.LiveTv, uiContext.getString(R.string.ui_choose_channel), uiContext.getString(R.string.ui_programme_hint))
        } else {
            LogoTile(stream.name, stream.logo, Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Pill(uiContext.getString(R.string.ui_live), color = Palette.Live, contentColor = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                stream.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            val now = epg.firstOrNull()
            if (now == null) {
                Text(uiContext.getString(R.string.ui_no_programme), style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
            } else {
                ProgramBlock(uiContext.getString(R.string.ui_now), now, showProgress = true)
                epg.getOrNull(1)?.let {
                    Spacer(Modifier.height(16.dp))
                    ProgramBlock(uiContext.getString(R.string.ui_next), it, showProgress = false)
                }
            }
            Spacer(Modifier.weight(1f))
            if (archive) {
                EpgButton(uiContext.getString(R.string.ui_past), onPast)
                Spacer(Modifier.height(6.dp))
            }
            EpgButton(uiContext.getString(R.string.ui_epg_match), onMatch)
            Spacer(Modifier.height(8.dp))
            Text(uiContext.getString(R.string.ui_remote_hint), style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun ProgramBlock(label: String, entry: EpgEntry, showProgress: Boolean) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Palette.Primary)
    Spacer(Modifier.height(4.dp))
    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("${formatClock(entry.start)} – ${formatClock(entry.end)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    if (showProgress && entry.end > entry.start) {
        val now = System.currentTimeMillis() / 1000
        Spacer(Modifier.height(8.dp))
        ProgressBar((now - entry.start).toFloat() / (entry.end - entry.start))
    }
}
