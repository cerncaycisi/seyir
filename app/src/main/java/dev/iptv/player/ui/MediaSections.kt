package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import dev.iptv.player.data.SpecialCategory
import dev.iptv.player.data.specialItems
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.VodSort
import dev.iptv.player.data.filterByGenre
import dev.iptv.player.data.genresOf
import dev.iptv.player.data.latestOf
import dev.iptv.player.data.sortStreams
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentRules
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.VodInfo
import dev.iptv.player.data.favoriteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.iptv.player.data.withCatalogMetadata
import dev.iptv.player.data.tmdb.TmdbInfo
import dev.iptv.player.data.Setting

/** [direct] rows resume on click (continue watching) instead of opening the detail page. */
data class MediaRow(val title: String, val items: List<Stream>, val direct: Boolean = false, val categoryName: String? = null)

private fun buildRows(uiContext: android.content.Context, content: Content, playlist: Playlist, type: ContentType, rules: ContentRules): List<MediaRow> {
    val base = rules.filterBase(playlist, type, content)
    return buildRowsRaw(uiContext, base, playlist, type, rules)
}

private fun buildRowsRaw(uiContext: android.content.Context, content: Content, playlist: Playlist, type: ContentType, rules: ContentRules): List<MediaRow> {
    val byCategory = content.items.groupBy { it.categoryId }
    val known = content.categories.mapNotNull { c -> byCategory[c.id]?.let { MediaRow(c.name, it, categoryName = c.name) } }.filter { !rules.isCategoryLocked(playlist, type, it.items.first().categoryId) }
    val knownIds = content.categories.mapTo(HashSet()) { it.id }
    val rest = content.items.filter { it.categoryId !in knownIds }
    return if (rest.isEmpty()) known else known + MediaRow(uiContext.getString(R.string.ui_other), rest)
}

private suspend fun allContent(repo: Repository, playlist: Playlist): Map<ContentType, Content> =
    ContentType.entries.associateWith { t -> runCatching { repo.content(playlist, t) }.getOrDefault(Content(emptyList(), emptyList())) }

@Composable
fun VodSection(repo: Repository, playlist: Playlist, type: ContentType, actions: HomeActions, onSearch: () -> Unit = {}) {
    val uiContext = LocalContext.current
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<Content>>(Load.Loading, playlist.id, type) {
        value = loadCatching { repo.content(playlist, type) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> if (s.value.items.isEmpty()) {
            EmptyState(Icons.Rounded.Movie, if (type == ContentType.MOVIE) uiContext.getString(R.string.ui_no_movie) else uiContext.getString(R.string.ui_no_series))
        } else {
            VodBrowser(repo, playlist, type, s.value, actions, onSearch)
        }
    }
}

private const val OTHER_CATEGORY = "\u0000other"

private fun specialLabel(context: android.content.Context, kind: SpecialCategory): String = context.getString(when (kind) {
    SpecialCategory.ALL -> R.string.ui_all
    SpecialCategory.FAVORITES -> R.string.ui_favorites
    SpecialCategory.WATCH_LATER -> R.string.ui_cat_watch_later
    SpecialCategory.CONTINUE -> R.string.ui_cat_continue
    SpecialCategory.RECENT -> R.string.ui_cat_recent
})

/** One entry of the category column: a pinned special category or a provider category. */
internal class BrowseCategory(val key: String, val name: String, val count: Int, val special: SpecialCategory? = null, val providerId: String? = null)

/**
 * Left category column + header (title, sort, genre) + vertical poster grid.
 * [categoryWidth] and [columns] are parameters so other form factors can reuse the pieces.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VodBrowser(
    repo: Repository,
    playlist: Playlist,
    type: ContentType,
    content: Content,
    actions: HomeActions,
    onSearch: () -> Unit,
    categoryWidth: Dp = 200.dp,
    columns: Int = 5,
) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules = actions.rules
    var sort by rememberSaveable { mutableStateOf(VodSort.DEFAULT) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedKey by rememberSaveable { mutableStateOf(SpecialCategory.ALL.name) }
    var confirmClear by remember { mutableStateOf(false) }
    val history by repo.history.entries.collectAsState(initial = null)
    val metadataEnabled by repo.settings.flow(Setting.TmdbEnabled).collectAsState(initial = true)
    var metadata by remember(content) { mutableStateOf<Map<String, TmdbInfo?>>(emptyMap()) }
    var metadataProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var availableGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(type, metadataEnabled) { availableGenres = if (metadataEnabled) repo.tmdb.genres(type) else emptyList() }
    // Fetch only a small opening batch normally. A genre/rating query completes its selected
    // category progressively, with disk caching and four requests at a time; leaving cancels it.
    LaunchedEffect(content, selectedKey, sort, genre, metadataEnabled, history == null, actions.favorites, actions.watchLater, rules) {
        metadataProgress = null
        if (history == null || !metadataEnabled || !repo.tmdb.available()) return@LaunchedEffect
        val candidates = withContext(Dispatchers.Default) {
            buildVodView(uiContext, playlist, type, content, rules, actions.favorites, actions.watchLater, history.orEmpty(), selectedKey, VodSort.DEFAULT, null).raw
        }
        val requested = if (sort == VodSort.RATING || genre != null) candidates else candidates.take(60)
        val pending = requested.filter { !metadata.containsKey(it.id) }
        var completed = requested.size - pending.size
        try {
            for (batch in pending.chunked(12)) {
                metadataProgress = completed to requested.size
                val results = batch.chunked(4).flatMap { chunk ->
                    coroutineScope { chunk.map { stream -> async { stream.id to repo.tmdb.lookup(stream.name, type, stream.year) } }.awaitAll() }
                        .also { delay(100) }
                }
                metadata = metadata + results
                completed += batch.size
            }
        } finally { metadataProgress = null }
    }
    // Filtering/sorting ~20k titles takes far longer than a frame: do it off the main thread and keep
    // showing the previous result meanwhile (this was the 1-2 s freeze when opening Movies/Series).
    val view = rememberBackground(content, rules, playlist.id, type, actions.favorites, actions.watchLater, history, selectedKey, sort, genre, metadata, metadataEnabled) {
        val h = history ?: return@rememberBackground null
        val enriched = content.copy(items = content.items.map { withCatalogMetadata(it, if (metadataEnabled) metadata[it.id] else null, sort == VodSort.RATING) })
        buildVodView(uiContext, playlist, type, enriched, rules, actions.favorites, actions.watchLater, h, selectedKey, sort, genre)
    }
    if (view == null) {
        LoadingState()
        return
    }
    val categories = view.categories
    val selected = view.selected
    val selPid = selected.providerId
    if (selPid != null && selPid != OTHER_CATEGORY && rules.isCategoryLocked(playlist, type, selPid)) selectedKey = SpecialCategory.ALL.name
    val raw = view.raw
    val genres = (availableGenres + view.genres).distinct().sorted()
    val genreSel = view.genre
    val shown = view.shown

    val gridState = rememberLazyGridState()
    val catState = rememberLazyListState(initialFirstVisibleItemIndex = (categories.indexOfFirst { it.key == selected.key } - 3).coerceAtLeast(0))
    val selectedReq = remember { FocusRequester() }
    val firstItemReq = remember { FocusRequester() }
    // OK on a category moves focus into its grid (top-left poster), like Netflix's category rail.
    var gridFocusIndex by remember { mutableIntStateOf(-1) }
    var jumpToGrid by remember { mutableIntStateOf(0) }
    val restore = actions.restore
    val restoreIndex = restore.itemId?.let { id -> shown.indexOfFirst { it.id == id } } ?: -1
    var focused by remember { mutableStateOf<Stream?>(null) }
    LaunchedEffect(Unit) {
        if (restoreIndex >= 0) {
            gridState.scrollToItem(restoreIndex)
            withFrameNanos { }
            runCatching { firstItemReq.requestFocus() }
        } else if (restore.initial) {
            withFrameNanos { }
            runCatching { selectedReq.requestFocus() }
        }
        if (restore.itemId != null || restore.initial) restore.done()
    }
    var handledJump by remember { mutableIntStateOf(0) }
    // Waits for the background result of the newly selected category before moving focus into its grid.
    LaunchedEffect(jumpToGrid, view) {
        if (jumpToGrid == handledJump || view.selected.key != selectedKey) return@LaunchedEffect
        handledJump = jumpToGrid
        gridFocusIndex = 0
        withFrameNanos { }
        if (shown.isEmpty()) return@LaunchedEffect
        gridState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstItemReq.requestFocus() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(categoryWidth).fillMaxHeight()) {
            ScreenTitle(uiContext.getString(if (type == ContentType.MOVIE) R.string.ui_movies else R.string.ui_series))
            HeaderButton(uiContext.getString(R.string.ui_search), Icons.Rounded.Search, onSearch)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.focusProperties {
                    // Entering the list from the grid lands on the selected category, not the nearest row.
                    enter = { if (catState.layoutInfo.visibleItemsInfo.any { it.key == selected.key }) selectedReq else FocusRequester.Default }
                },
                state = catState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(categories, key = { it.key }) { c ->
                    val pid = c.providerId
                    CategoryItem(
                        name = c.name,
                        count = c.count,
                        selected = c.key == selected.key,
                        locked = pid != null && pid != OTHER_CATEGORY && rules.isCategoryMarkedLocked(playlist, type, pid),
                        onClick = {
                            val select = { selectedKey = c.key; genre = null; focused = null; jumpToGrid += 1; Unit }
                            if (pid != null && pid != OTHER_CATEGORY && rules.isCategoryLocked(playlist, type, pid)) actions.requireUnlock(select) else select()
                        },
                        onLongClick = if (pid != null && pid != OTHER_CATEGORY) ({ actions.categoryLongPress(type, pid, c.name) }) else null,
                        modifier = if (c.key == selected.key) Modifier.focusRequester(selectedReq) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(selected.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(uiContext.getString(R.string.ui_item_count, shown.size), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                if (selected.special == SpecialCategory.RECENT && raw.isNotEmpty()) {
                    HeaderButton(uiContext.getString(R.string.ui_clear_history), Icons.Rounded.DeleteSweep) { confirmClear = true }
                }
                DropdownButton(uiContext.getString(R.string.ui_sort), VodSort.entries.map { it to sortLabel(uiContext, it) }, sort, { sort = it })
                run {
                    val options = listOf<String?>(null) + genres
                    DropdownButton(uiContext.getString(R.string.ui_genre), options.map { it to (it ?: uiContext.getString(R.string.ui_all)) }, genreSel, { genre = it })
                }
            }
            Spacer(Modifier.height(8.dp))
            metadataProgress?.let { (done, total) -> Text(uiContext.getString(R.string.ux_metadata_progress, done, total), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall) }
            if (sort == VodSort.RATING && (!metadataEnabled || !repo.tmdb.configured)) {
                Text(uiContext.getString(R.string.ux_tmdb_disabled), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (shown.isEmpty()) {
                val (title, hint) = when (selected.special) {
                    SpecialCategory.FAVORITES -> uiContext.getString(R.string.ui_no_favorites) to uiContext.getString(R.string.ui_favorites_hint)
                    SpecialCategory.WATCH_LATER -> uiContext.getString(R.string.ui_cat_watch_later) to uiContext.getString(R.string.ui_watch_later_hint)
                    SpecialCategory.CONTINUE, SpecialCategory.RECENT -> selected.name to uiContext.getString(R.string.ui_history_empty_hint)
                    else -> uiContext.getString(R.string.ui_category_empty) to uiContext.getString(R.string.ui_category_empty_hint)
                }
                EmptyState(Icons.Rounded.SearchOff, title, hint)
            } else {
                PosterGrid(
                    items = shown,
                    playlist = playlist,
                    actions = actions,
                    columns = columns,
                    state = gridState,
                    focusIndex = if (gridFocusIndex >= 0) gridFocusIndex else restoreIndex,
                    focusRequester = firstItemReq,
                    categoryName = selPid?.let { selected.name },
                    onFocused = { focused = it },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    focused?.let { listOfNotNull(it.name, it.year, it.rating?.let { r -> "★ $r" }).joinToString("  ·  ") }.orEmpty(),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            uiContext.getString(R.string.ui_clear_history),
            uiContext.getString(R.string.ui_clear_history_message),
            uiContext.getString(R.string.ui_clear_confirm),
            onConfirm = { confirmClear = false; scope.launch { repo.history.clearType(type) } },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Header button with an icon (e.g. "clear history"); same look and 48dp tap target as [DropdownButton]. */
@Composable
internal fun HeaderButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = MinTapTarget).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Live,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.heightIn(min = MinTapTarget).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Vertical poster grid; scrolls by finger or D-pad. [focusIndex] (>= 0) attaches [focusRequester] to that cell. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun PosterGrid(
    items: List<Stream>,
    playlist: Playlist,
    actions: HomeActions,
    columns: Int,
    state: LazyGridState,
    focusIndex: Int,
    focusRequester: FocusRequester,
    categoryName: String?,
    onFocused: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Coming back from the category list returns to the last focused poster if it is on screen, else the
    // top-left visible one. (Modifier.focusRestorer crashed inside lazy layouts.)
    var lastFocused by remember { mutableIntStateOf(-1) }
    val entryIndex by remember(state) {
        derivedStateOf {
            val i = lastFocused
            if (state.layoutInfo.visibleItemsInfo.any { it.index == i }) i else state.firstVisibleItemIndex
        }
    }
    val entry = remember { FocusRequester() }
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        modifier.fillMaxWidth().focusProperties { enter = { entry } },
        state = state,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, s -> s.id }) { i, s ->
            val key = favoriteKey(playlist, s)
            PosterCard(
                s,
                key in actions.favorites,
                onClick = { actions.open(items, i) },
                onLongClick = { actions.longPress(s, categoryName) },
                onFocused = { lastFocused = i; onFocused(s) },
                modifier = Modifier.fillMaxWidth()
                    .then(if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(if (i == entryIndex) Modifier.focusRequester(entry) else Modifier),
                progress = actions.progress[key],
                badge = actions.badges[key],
            )
        }
    }
}

@Composable
fun MediaRows(
    rows: List<MediaRow>,
    playlist: Playlist,
    actions: HomeActions,
    onFocused: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    val restore = actions.restore
    val columnState = rememberLazyListState()
    val restoreRow = restore.itemId?.let { id -> rows.indexOfFirst { r -> r.items.any { it.id == id } } } ?: -1
    val initialRow = if (restoreRow < 0 && restore.initial) 0 else -1
    LaunchedEffect(Unit) { if (restoreRow >= 0) columnState.scrollToItem(restoreRow) }

    LazyColumn(
        modifier.fillMaxWidth(),
        state = columnState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // Keyed by title: a row appearing above (e.g. favourites loading) must not recreate the focused row.
        itemsIndexed(rows, key = { _, row -> row.title }) { r, row ->
            val target = when (r) {
                restoreRow -> row.items.indexOfFirst { it.id == restore.itemId }
                initialRow -> 0
                else -> -1
            }
            MediaRowView(row, playlist, actions, target, onFocused)
        }
    }
}

@Composable
private fun MediaRowView(row: MediaRow, playlist: Playlist, actions: HomeActions, target: Int, onFocused: (Stream) -> Unit) {
    val rowState = rememberLazyListState()
    val requester = remember { FocusRequester() }
    var targetFocused by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (target >= 0) {
            rowState.scrollToItem(target)
            // The card may attach a frame or two late (rows/images still settling); retry briefly.
            targetFocused = false
            for (attempt in 0 until 12) {
                withFrameNanos { }
                if (targetFocused) break
                runCatching { requester.requestFocus() }
            }
            actions.restore.done()
        }
    }
    Column {
        SectionTitle(row.title, "${row.items.size}")
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
        ) {
            itemsIndexed(row.items) { i, s ->
                val mod = if (i == target) Modifier.focusRequester(requester) else Modifier
                val open = { if (row.direct) actions.resume(s) else actions.open(row.items, i) }
                val toggle = { actions.longPress(s, row.categoryName) }
                if (s.type == ContentType.LIVE) {
                    ChannelCard(s, open, toggle, { if (i == target) targetFocused = true; onFocused(s) }, mod)
                } else {
                    PosterCard(s, favoriteKey(playlist, s) in actions.favorites, open, toggle, { if (i == target) targetFocused = true; onFocused(s) }, mod, progress = actions.progress[favoriteKey(playlist, s)], badge = actions.badges[favoriteKey(playlist, s)])
                }
            }
        }
    }
}

@Composable
fun FavoritesSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val uiContext = LocalContext.current
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<Map<ContentType, Content>>>(Load.Loading, playlist.id) {
        value = loadCatching { allContent(repo, playlist) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(uiContext.getString(R.string.ui_favorites))
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Err -> ErrorState(s.message)
            is Load.Ok -> {
                // Off the main thread (and only when the inputs change): it walks the whole catalog.
                val rows = rememberBackground(s.value, actions.rules, actions.favorites) {
                    val favorites = s.value.flatMap { (type, content) -> visibleContentItems(playlist, type, content, actions.rules) }
                        .filter { favoriteKey(playlist, it) in actions.favorites }
                    listOf(
                        MediaRow(uiContext.getString(R.string.ui_channels), favorites.filter { it.type == ContentType.LIVE }),
                        MediaRow(uiContext.getString(R.string.ui_movies), favorites.filter { it.type == ContentType.MOVIE }),
                        MediaRow(uiContext.getString(R.string.ui_series), favorites.filter { it.type == ContentType.SERIES }),
                    ).filter { it.items.isNotEmpty() }
                }
                if (rows == null) {
                    LoadingState()
                } else if (rows.isEmpty()) {
                    EmptyState(Icons.Rounded.FavoriteBorder, uiContext.getString(R.string.ui_no_favorites), uiContext.getString(R.string.ui_favorites_hint))
                } else {
                    MediaRows(rows, playlist, actions, onFocused = {})
                }
            }
        }
    }
}

@Composable
fun SearchSection(repo: Repository, playlist: Playlist, actions: HomeActions, initialQuery: String? = null, type: ContentType) {
    val uiContext = LocalContext.current
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val all by produceState<Map<ContentType, Content>?>(null, playlist.id, type) {
        value = mapOf(type to runCatching { repo.content(playlist, type) }.getOrDefault(Content(emptyList(), emptyList())))
    }
    val q = query.trim()
    val rules = actions.rules
    // Scans every channel, movie and series per keystroke: run it off the main thread so typing stays smooth.
    val rows = rememberBackground(all, q, rules) {
        val source = all
        if (source == null || q.length < 2) {
            emptyList()
        } else {
            val hits = source.flatMap { (type, content) -> visibleContentItems(playlist, type, content, rules) }
                .filter { dev.iptv.player.data.TextSearch.matches(it.name, q) }
            listOf(
                MediaRow(uiContext.getString(R.string.ui_channels), hits.filter { it.type == ContentType.LIVE }.take(60)),
                MediaRow(uiContext.getString(R.string.ui_movies), hits.filter { it.type == ContentType.MOVIE }.take(60)),
                MediaRow(uiContext.getString(R.string.ui_series), hits.filter { it.type == ContentType.SERIES }.take(60)),
            ).filter { it.items.isNotEmpty() }
        }
    }.orEmpty()
    // Opening Search from the menu puts focus on the search field.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (actions.restore.initial) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
            actions.restore.done()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(uiContext.getString(when (type) { ContentType.LIVE -> R.string.ux_search_live; ContentType.MOVIE -> R.string.ux_search_movies; ContentType.SERIES -> R.string.ux_search_series }))
        val context = LocalContext.current
        val voiceIntent = remember(uiContext) {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiContext.resources.configuration.locales[0].toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, uiContext.getString(R.string.ui_search_prompt))
            }
        }
        val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
        }
        Row(Modifier.widthIn(max = 760.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(query, { query = it }, uiContext.getString(R.string.ui_search_prompt), Modifier.weight(1f).focusRequester(fieldFocus), leadingIcon = Icons.Rounded.Search)
            if (voiceAvailable) {
                Spacer(Modifier.width(10.dp))
                MicButton { runCatching { launcher.launch(voiceIntent) } }
            }
        }
        Spacer(Modifier.height(20.dp))
        when {
            all == null -> LoadingState()
            q.length < 2 -> EmptyState(Icons.Rounded.Search, uiContext.getString(R.string.ui_start_search), uiContext.getString(R.string.ui_search_minimum))
            rows.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, uiContext.getString(R.string.ui_no_results), uiContext.getString(R.string.ui_search_no_match, q))
            else -> MediaRows(rows, playlist, actions, onFocused = {})
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    val uiContext = LocalContext.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp).pointerClick(onClick).semantics { contentDescription = uiContext.getString(R.string.ui_voice_search) },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = androidx.compose.ui.graphics.Color.White,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            androidx.tv.material3.Icon(Icons.Rounded.Mic, contentDescription = uiContext.getString(R.string.ui_voice_search))
        }
    }
}

private fun sortLabel(context: android.content.Context, sort: VodSort): String = context.getString(when (sort) {
    VodSort.DEFAULT -> R.string.ui_sort_default
    VodSort.RECENT -> R.string.ui_recent
    VodSort.RATING -> R.string.ux_tmdb_rating
    VodSort.AZ -> R.string.ui_sort_az
    VodSort.ZA -> R.string.ui_sort_za
    VodSort.YEAR -> R.string.ui_sort_year
})

/** Everything the Movies / Series browser shows for one selection; built off the main thread. */
private class VodView(
    val categories: List<BrowseCategory>,
    val selected: BrowseCategory,
    val raw: List<Stream>,
    val genres: List<String>,
    val genre: String?,
    val shown: List<Stream>,
)

private fun buildVodView(
    uiContext: android.content.Context,
    playlist: Playlist,
    type: ContentType,
    content: Content,
    rules: ContentRules,
    favorites: Set<String>,
    watchLater: Set<String>,
    history: List<dev.iptv.player.data.HistoryEntry>,
    selectedKey: String,
    sort: VodSort,
    genre: String?,
): VodView {
    val hist = history.filter { it.type == type && it.playlistId == playlist.id }
    val base = rules.filterBase(playlist, type, content)
    val visible = rules.unlockedItems(playlist, type, base.items)
    val specialLists = SpecialCategory.entries.associateWith { specialItems(it, playlist, visible, favorites, watchLater, hist) }
    val counts = base.items.groupingBy { it.categoryId }.eachCount()
    val knownIds = base.categories.mapTo(HashSet()) { it.id }
    val other = base.items.count { it.categoryId !in knownIds }
    val categories = SpecialCategory.entries.map { BrowseCategory(it.name, specialLabel(uiContext, it), specialLists[it].orEmpty().size, special = it) } +
        base.categories.filter { (counts[it.id] ?: 0) > 0 }.map { BrowseCategory("c:${it.id}", it.name, counts[it.id] ?: 0, providerId = it.id) } +
        (if (other > 0) listOf(BrowseCategory("c:$OTHER_CATEGORY", uiContext.getString(R.string.ui_other), other, providerId = OTHER_CATEGORY)) else emptyList())
    val selected = categories.firstOrNull { it.key == selectedKey } ?: categories.first()
    val selPid = selected.providerId
    val raw = when {
        selected.special != null -> specialLists[selected.special].orEmpty()
        selPid == OTHER_CATEGORY -> base.items.filter { it.categoryId !in knownIds }
        else -> base.items.filter { it.categoryId == selPid }
    }
    val genres = genresOf(raw).take(24)
    val genreSel = genre
    return VodView(categories, selected, raw, genres, genreSel, sortStreams(filterByGenre(raw, genreSel), sort))
}
