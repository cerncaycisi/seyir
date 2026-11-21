package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
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
private const val RECENT = "~recent"
private const val FAVS = "~favorites"
private const val PREVIEW_DEBOUNCE_MS = 400L

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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LiveBrowser(
    repo: Repository,
    playlist: Playlist,
    content: Content,
    actions: HomeActions,
    categoryWidth: Dp = 190.dp,
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

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(categoryWidth).fillMaxHeight()) {
            ScreenTitle(uiContext.getString(R.string.ui_live_tv))
            LazyColumn(
                Modifier.focusProperties {
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
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(categoryName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(uiContext.getString(R.string.ui_channel_count, items.size), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                DropdownButton(uiContext.getString(R.string.ui_sort), LiveSort.entries.map { it to liveSortLabel(uiContext, it) }, sort, { sort = it })
            }
            Spacer(Modifier.height(8.dp))
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
                    Modifier.focusProperties { enter = { firstVisibleFocus } },
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
                            nowTitle = nowProg?.title,
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
        Spacer(Modifier.width(16.dp))
        ChannelPreview(
            stream = focused,
            preview = preview,
            epg = epg,
            favorite = focused?.let { favoriteKey(playlist, it) in actions.favorites } == true,
            archive = focused?.let { Catchup.available(playlist, it) } == true,
            onFullscreen = { focused?.let { f -> items.indexOfFirst { it.id == f.id }.takeIf { it >= 0 }?.let(::openFullscreen) } },
            onFavorite = { focused?.let(actions.toggleFavorite) },
            onMatch = { showMatch = true },
            onPast = { showPast = true },
            modifier = Modifier.width(272.dp).fillMaxHeight().padding(bottom = 24.dp),
        )
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

@Composable
private fun ChannelPreview(
    stream: Stream?,
    preview: LivePreview?,
    epg: List<EpgEntry>,
    favorite: Boolean,
    archive: Boolean,
    onFullscreen: () -> Unit,
    onFavorite: () -> Unit,
    onMatch: () -> Unit,
    onPast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiContext = LocalContext.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(12.dp).verticalScroll(rememberScrollState())) {
        if (stream == null) {
            EmptyState(Icons.Rounded.LiveTv, uiContext.getString(R.string.ui_choose_channel), uiContext.getString(R.string.ui_programme_hint))
        } else {
            // OK / tap on the video opens the fullscreen player.
            Surface(
                onClick = onFullscreen,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).pointerClick(onFullscreen),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.Primary), shape = RoundedCornerShape(10.dp))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            ) {
                LivePreviewVideo(preview, stream, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(uiContext.getString(R.string.ui_live), color = Palette.Live, contentColor = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stream.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            val now = epg.firstOrNull()
            if (now == null) {
                Text(uiContext.getString(R.string.ui_no_programme), style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            } else {
                ProgramBlock(uiContext.getString(R.string.ui_now), now, showProgress = true)
                epg.getOrNull(1)?.let {
                    Spacer(Modifier.height(8.dp))
                    ProgramBlock(uiContext.getString(R.string.ui_next), it, showProgress = false)
                }
            }
            Spacer(Modifier.height(10.dp))
            EpgButton(if (favorite) uiContext.getString(R.string.ui_remove_favorites) else uiContext.getString(R.string.ui_add_favorites), onFavorite)
            Spacer(Modifier.height(6.dp))
            if (archive) {
                EpgButton(uiContext.getString(R.string.ui_past), onPast)
                Spacer(Modifier.height(6.dp))
            }
            EpgButton(uiContext.getString(R.string.ui_epg_match), onMatch)
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
