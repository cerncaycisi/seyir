package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import dev.iptv.player.data.LiveSort
import dev.iptv.player.data.recentStreams
import dev.iptv.player.data.sortChannels
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
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
private const val RECENT = "~recent"
private const val FAVS = "~favorites"
private const val PREVIEW_DEBOUNCE_MS = 650L

@Composable
fun LiveSection(repo: Repository, playlist: Playlist, actions: HomeActions, onSearch: () -> Unit = {}, onRecordings: () -> Unit = {}) {
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<Content>>(Load.Loading, playlist.id) {
        value = loadCatching { repo.content(playlist, ContentType.LIVE) }
    }
    when (val s = state) {
        Load.Loading -> Box(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) { LoadingState() }
        is Load.Err -> Box(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) { ErrorState(s.message) }
        is Load.Ok -> LiveBrowser(repo, playlist, s.value, actions, onSearch, onRecordings)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LiveBrowser(
    repo: Repository,
    playlist: Playlist,
    content: Content,
    actions: HomeActions,
    onSearch: () -> Unit,
    onRecordings: () -> Unit,
) {
    val uiContext = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(ALL) }
    var sort by rememberSaveable { mutableStateOf(LiveSort.DEFAULT) }
    val rules = actions.rules
    val history by repo.history.entries.collectAsState(initial = emptyList())
    // Hidden channels/categories and adult names are removed; locked categories stay listed (with a lock).
    val base = remember(content, rules.hiddenChannels, rules.hiddenCategories, rules.hideAdult) {
        rules.filterBase(playlist, ContentType.LIVE, content)
    }
    val visible = remember(base, rules.lockedCategories, rules.unlocked) { rules.unlockedItems(playlist, ContentType.LIVE, base.items) }
    val recent = remember(history, visible) { recentStreams(history.filter { it.type == ContentType.LIVE }, visible, playlist.id) }
    val favs = remember(visible, actions.favorites) { visible.filter { favoriteKey(playlist, it) in actions.favorites } }
    val counts = remember(base) { base.items.groupingBy { it.categoryId }.eachCount() }
    val categories = remember(uiContext, base, recent.size, favs.size) {
        listOf(
            Category(ALL, uiContext.getString(R.string.ui_all)),
            Category(RECENT, uiContext.getString(R.string.ui_cat_recent)),
            Category(FAVS, uiContext.getString(R.string.ui_favorites)),
        ) + base.categories.filter { (counts[it.id] ?: 0) > 0 }
    }
    if (categories.none { it.id == selected }) selected = ALL
    val isSpecial = selected == ALL || selected == RECENT || selected == FAVS
    if (!isSpecial && rules.isCategoryLocked(playlist, ContentType.LIVE, selected)) selected = ALL
    val items = remember(base, visible, recent, favs, selected, sort, rules.lockedCategories, rules.unlocked) {
        sortChannels(
            when (selected) {
                ALL -> visible
                RECENT -> recent
                FAVS -> favs
                else -> base.items.filter { it.categoryId == selected }
            },
            sort,
        )
    }
    val categoryName = categories.firstOrNull { it.id == selected }?.name ?: uiContext.getString(R.string.ui_all)
    val listState = rememberLazyListState()
    val catState = rememberLazyListState()
    val itemFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val firstVisibleFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    // Entering the list (Right from the categories, Left from the preview) lands on the last focused channel
    // when it is on screen, else the top visible one. (Modifier.focusRestorer crashed inside lazy lists.)
    var lastFocusedIndex by remember { mutableIntStateOf(-1) }
    val firstVisible by remember {
        derivedStateOf {
            val i = lastFocusedIndex
            if (listState.layoutInfo.visibleItemsInfo.any { it.index == i }) i else listState.firstVisibleItemIndex
        }
    }
    // OK on a category moves focus into its channel list (like pressing Right), starting at the top.
    var jumpToList by remember { mutableIntStateOf(0) }
    LaunchedEffect(jumpToList) {
        if (jumpToList == 0) return@LaunchedEffect
        withFrameNanos { }
        if (items.isEmpty()) return@LaunchedEffect
        lastFocusedIndex = 0
        listState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstVisibleFocus.requestFocus() }
    }
    var focused by remember { mutableStateOf<Stream?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val epgSvc = rememberEpg()
    val gen by epgSvc.generation.collectAsState()
    val nowSec by rememberNowSec(60_000)
    var showMatch by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }
    val preview = rememberLivePreview(repo)

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
    // The preview follows the focused channel; the delay keeps fast scrolling from hammering the provider.
    LaunchedEffect(focused, preview) {
        val f = focused ?: return@LaunchedEffect
        delay(PREVIEW_DEBOUNCE_MS)
        preview?.play(f)
    }
    fun openFullscreen(i: Int) {
        // Free the preview's connection before the full player opens (many panels allow one stream).
        preview?.release()
        actions.open(items, i)
    }

    val hScrim = remember {
        Brush.horizontalGradient(
            0f to Color(0xFF07080A),
            0.28f to Color(0xE607080A),
            0.56f to Color(0x4D07080A),
            1f to Color(0x1A07080A),
        )
    }
    val vScrim = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color(0xB307080A),
            1f to Color(0xFF07080A),
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF07080A))) {
        val previewBox = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.58f).aspectRatio(16f / 9f)
        LivePreviewBackdrop(preview, previewBox)
        Box(previewBox.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            Box(Modifier.fillMaxSize().background(hScrim))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.48f).background(vScrim))
        }
        Column(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiContext.getString(R.string.ui_live_tv), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(
                    "$categoryName · ${uiContext.getString(R.string.ui_channel_count, items.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                HeaderButton(uiContext.getString(R.string.ui_search), Icons.Rounded.Search, onSearch)
                HeaderButton(uiContext.getString(R.string.play_section_recordings), Icons.Rounded.FiberManualRecord, onRecordings)
                DropdownButton(uiContext.getString(R.string.ui_sort), LiveSort.entries.map { it to liveSortLabel(uiContext, it) }, sort, { sort = it })
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.width(150.dp).fillMaxHeight().focusProperties {
                        enter = { if (catState.layoutInfo.visibleItemsInfo.any { it.key == selected }) selectedFocus(categoryFocus) else FocusRequester.Default }
                    },
                    state = catState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(categories, key = { it.id }) { c ->
                        val special = c.id == ALL || c.id == RECENT || c.id == FAVS
                        CategoryItem(
                            name = c.name,
                            count = when (c.id) {
                                ALL -> visible.size
                                RECENT -> recent.size
                                FAVS -> favs.size
                                else -> counts[c.id]
                            },
                            selected = c.id == selected,
                            locked = !special && rules.isCategoryMarkedLocked(playlist, ContentType.LIVE, c.id),
                            onClick = {
                                if (!special && rules.isCategoryLocked(playlist, ContentType.LIVE, c.id)) {
                                    actions.requireUnlock { selected = c.id; jumpToList += 1 }
                                } else {
                                    selected = c.id
                                    jumpToList++
                                }
                            },
                            onLongClick = if (special) null else ({ actions.categoryLongPress(ContentType.LIVE, c.id, c.name) }),
                            modifier = if (c.id == selected) Modifier.focusRequester(categoryFocus) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    if (items.isEmpty()) {
                        val hint = when (selected) {
                            FAVS -> uiContext.getString(R.string.ui_favorites_hint)
                            RECENT -> uiContext.getString(R.string.ui_history_empty_hint)
                            else -> null
                        }
                        EmptyState(Icons.Rounded.Tv, uiContext.getString(R.string.ui_channels_empty), hint)
                    } else {
                        LazyColumn(
                            // Re-entering the list returns to the last focused channel, else the top visible one
                            // (not whichever row happens to be level with the category).
                            Modifier.fillMaxSize().focusProperties {
                                enter = { firstVisibleFocus }
                                exit = { dir -> if (dir == FocusDirection.Right && focused != null) watchFocus else FocusRequester.Default }
                            },
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                        ) {
                            itemsIndexed(items) { i, s ->
                                val rowFocus = remember(s.id) { FocusRequester() }
                                // Lint misses the direct ProduceStateScope.value assignment in this producer.
                                @SuppressLint("ProduceStateDoesNotAssignValue")
                                val nowProg by produceState<Programme?>(null, s.id, gen, nowSec / 60) { value = epgSvc.nowPlaying(playlist.id, s, nowSec) }
                                ChannelRow(
                                    number = i + 1,
                                    stream = s,
                                    favorite = favoriteKey(playlist, s) in actions.favorites,
                                    // First OK starts the channel in the preview; OK on the one already playing goes fullscreen.
                                    onClick = {
                                        runCatching { rowFocus.requestFocus() }
                                        focused = s
                                        lastFocusedIndex = i
                                        if (preview?.channel?.id == s.id) openFullscreen(i) else preview?.play(s) ?: openFullscreen(i)
                                    },
                                    onLongClick = { actions.longPress(s, categories.firstOrNull { it.id == s.categoryId }?.name) },
                                    onFocused = { focused = s; lastFocusedIndex = i },
                                    nowTitle = nowProg?.let { "${it.title} · ${formatClock(it.start)}–${formatClock(it.stop)}" },
                                    progress = nowProg?.let { (nowSec - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                                    modifier = Modifier
                                        .focusRequester(rowFocus)
                                        .then(if (i == restoreIndex) Modifier.focusRequester(itemFocus) else Modifier)
                                        .then(if (i == firstVisible) Modifier.focusRequester(firstVisibleFocus) else Modifier),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                LiveNowInfo(
                    stream = focused,
                    epg = epg,
                    nowSec = nowSec,
                    preview = preview,
                    favorite = focused?.let { favoriteKey(playlist, it) in actions.favorites } == true,
                    archive = focused?.let { Catchup.available(playlist, it) } == true,
                    watchFocus = watchFocus,
                    onFullscreen = { focused?.let { f -> items.indexOfFirst { it.id == f.id }.takeIf { it >= 0 }?.let(::openFullscreen) } },
                    onFavorite = { focused?.let(actions.toggleFavorite) },
                    onMatch = { showMatch = true },
                    onPast = { showPast = true },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 24.dp),
                )
            }
        }
    }
    if (showMatch) focused?.let { s -> EpgMatchDialog(playlist, s, onDismiss = { showMatch = false }) }
    if (showPast) focused?.let { s ->
        PastBroadcastsDialog(playlist, s, onPlay = { p ->
            Catchup.streamFor(playlist, s, p)?.let { preview?.release(); actions.open(listOf(it), 0) }
        }, onDismiss = { showPast = false })
    }
}

private fun selectedFocus(r: FocusRequester) = r

private fun liveSortLabel(context: android.content.Context, sort: LiveSort): String = context.getString(when (sort) {
    LiveSort.DEFAULT -> R.string.ui_sort_default
    LiveSort.NUMBER -> R.string.ui_sort_number
    LiveSort.AZ -> R.string.ui_sort_az
    LiveSort.ZA -> R.string.ui_sort_za
})

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LiveNowInfo(
    stream: Stream?,
    epg: List<EpgEntry>,
    nowSec: Long,
    preview: LivePreview?,
    favorite: Boolean,
    archive: Boolean,
    watchFocus: FocusRequester,
    onFullscreen: () -> Unit,
    onFavorite: () -> Unit,
    onMatch: () -> Unit,
    onPast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiContext = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (stream == null) {
            Text(uiContext.getString(R.string.ui_choose_channel), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        } else {
            val now = epg.firstOrNull()
            Text(
                "${uiContext.getString(if (now != null) R.string.ui_now else R.string.ui_live)} · ${stream.name.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = Color(0xB3F5F5F7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                now?.title ?: stream.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF5F5F7),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (now != null) {
                Text("${formatClock(now.start)}–${formatClock(now.end)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                if (now.end > now.start) {
                    Spacer(Modifier.height(6.dp))
                    ProgressBar((nowSec - now.start).toFloat() / (now.end - now.start), Modifier.width(220.dp).height(4.dp))
                }
            } else {
                Text(uiContext.getString(R.string.ui_no_programme), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            epg.getOrNull(1)?.let { next ->
                Spacer(Modifier.height(8.dp))
                Text(
                    uiContext.getString(R.string.ui_next_at, formatClock(next.start), next.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xBFF5F5F7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val err = if (preview?.channel?.id == stream.id) preview.error else null
            if (err != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Live, modifier = Modifier.size(18.dp))
                    Text(
                        "${uiContext.getString(R.string.play_no_stream)} · $err",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.focusProperties { enter = { watchFocus } },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        uiContext.getString(R.string.ui_watch),
                        Icons.Rounded.PlayArrow,
                        primary = true,
                        modifier = Modifier.focusRequester(watchFocus),
                        onClick = onFullscreen,
                    )
                    ActionButton(
                        uiContext.getString(if (favorite) R.string.ui_remove_favorites else R.string.ui_add_favorites),
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        onClick = onFavorite,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (archive) ActionButton(uiContext.getString(R.string.ui_past), Icons.Rounded.History, onClick = onPast)
                    ActionButton(uiContext.getString(R.string.ui_epg_match), Icons.Rounded.Tune, onClick = onMatch)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(uiContext.getString(R.string.ui_live_hint), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        }
    }
}
