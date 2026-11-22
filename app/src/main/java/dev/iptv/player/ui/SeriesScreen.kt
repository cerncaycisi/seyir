package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.DisplayText
import dev.iptv.player.data.Repository
import dev.iptv.player.data.SeriesInfo
import dev.iptv.player.data.ContinueMode
import dev.iptv.player.data.seriesContinue
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.seriesDetails
import dev.iptv.player.data.tmdb.TmdbEpisodeInfo
import kotlinx.coroutines.launch

@Composable
fun SeriesScreen(repo: Repository, playlist: Playlist, seriesId: String, onPlay: (List<Stream>, Int) -> Unit) {
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<SeriesInfo>>(Load.Loading, seriesId) {
        value = loadCatching { repo.seriesDetails(playlist, seriesId) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> DetailViewport { SeriesDetail(repo, playlist, seriesId, s.value, onPlay) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesDetail(repo: Repository, playlist: Playlist, seriesId: String, info: SeriesInfo, onPlay: (List<Stream>, Int) -> Unit) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val seasons = remember(info) { info.episodes.groupBy { it.season }.toSortedMap() }
    // Every episode as a playable stream; ids and series id let history and deep links find them again.
    val all = remember(uiContext, info) {
        info.episodes.map { e ->
            Stream(
                id = e.id,
                name = uiContext.getString(R.string.ui_episode_name, info.name, e.season, e.number, DisplayText.episodeTitle(e.title, info.name).ifEmpty { uiContext.getString(R.string.ui_episode_number, e.number) }),
                logo = info.cover,
                url = e.url,
                categoryId = "",
                type = ContentType.SERIES,
                backdrop = e.still ?: info.backdrop,
                seriesId = seriesId,
                season = e.season,
                episode = e.number,
            )
        }
    }
    val seriesStream = remember(info) { Stream(seriesId, info.name, info.cover, "", "", ContentType.SERIES) }
    val favKey = favoriteKey(playlist, seriesStream)
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())
    val isFavorite = favKey in favorites
    val watchLater by repo.store.watchLater.collectAsState(initial = emptySet())
    val isLater = favKey in watchLater
    val history by repo.history.entries.collectAsState(initial = emptyList())
    val progressById = remember(history) {
        history.filter { it.seriesId == seriesId && it.playlistId == playlist.id }
            .associate { it.id to (if (it.finished) 1f else it.fraction) }
    }
    val resumeById = remember(history) {
        history.filter { it.seriesId == seriesId && it.playlistId == playlist.id && !it.finished && it.positionMs >= dev.iptv.player.data.HistoryEntry.MIN_RESUME_MS }
            .associate { it.id to it.positionMs }
    }
    var pendingEpisode by remember { mutableStateOf<Int?>(null) }
    val lastEntry = remember(history, all) {
        history.filter { it.seriesId == seriesId && it.playlistId == playlist.id && all.any { s -> s.id == it.id } }.maxByOrNull { it.updatedAt }
    }
    val target = remember(lastEntry, info) { seriesContinue(info.episodes, lastEntry?.id, lastEntry?.finished == true) }
    var chosenSeason by rememberSaveable { mutableIntStateOf(-1) }
    val season = when {
        chosenSeason >= 0 -> chosenSeason
        lastEntry != null && target != null -> info.episodes[target.index].season
        else -> seasons.keys.firstOrNull() ?: 1
    }
    val episodes = remember(info, season) { all.filterIndexed { i, _ -> info.episodes[i].season == season } }
    val primary = remember { FocusRequester() }
    val seasonKeys = remember(seasons) { seasons.keys.toList() }
    val seasonState = rememberLazyListState(seasonKeys.indexOf(season).coerceAtLeast(0))
    val selectedSeasonFocus = remember { SafeFocusRequester() }
    val selectedSeasonVisible by remember(seasonKeys, season) {
        derivedStateOf {
            season in seasonKeys && seasonState.layoutInfo.visibleItemsInfo.any { it.key == season }
        }
    }
    LaunchedEffect(seasons.isEmpty()) {
        withFrameNanos { }
        runCatching { primary.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        val image = info.backdrop ?: info.cover
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.65f).fillMaxHeight(0.75f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Background, 0.42f to Palette.Background, 1f to Palette.Background.copy(alpha = 0.25f)),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.75f to Palette.Background)))

        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Left: title, facts and the vertical button group (primary action first).
            Column(
                Modifier
                    .width(290.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .lazyFocusBoundary(
                        exitTarget = { dir ->
                            when (dir) {
                                FocusDirection.Right ->
                                    if (seasons.isEmpty()) FocusRequester.Cancel
                                    else if (selectedSeasonVisible) selectedSeasonFocus.target
                                    else FocusRequester.Default
                                FocusDirection.Left, FocusDirection.Up, FocusDirection.Down -> FocusRequester.Cancel
                                else -> FocusRequester.Default
                            }
                        },
                    )
                    .focusGroup(),
            ) {
                TitleLogoOrText(
                    title = info.name,
                    logoUrl = info.logo,
                    textStyle = MaterialTheme.typography.headlineLarge,
                    maxLogoHeight = 76.dp,
                    maxLogoWidth = 270.dp,
                    maxLines = 3,
                )
                MetaLine(info.rating, listOfNotNull(info.year, uiContext.getString(R.string.ui_season_count, seasons.size)), Modifier.padding(top = 10.dp))
                Text(uiContext.getString(R.string.ui_episode_count, info.episodes.size), Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                info.genre?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = Palette.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                info.plot?.let {
                    if (info.plotLanguage == "en") Text(uiContext.getString(R.string.ux_plot_english), color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
                    ExpandablePlot(info.name, it, Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                if (seasons.isNotEmpty()) {
                    val label = when (target?.mode) {
                        null -> uiContext.getString(R.string.ui_play)
                        ContinueMode.START -> uiContext.getString(R.string.ui_watch_episode, episodeTag(uiContext, info, target.index))
                        ContinueMode.RESUME -> uiContext.getString(R.string.ui_resume_episode, episodeTag(uiContext, info, target.index))
                        ContinueMode.NEXT -> uiContext.getString(R.string.ui_next_episode, episodeTag(uiContext, info, target.index))
                        ContinueMode.RESTART -> uiContext.getString(R.string.ui_restart_series)
                    }
                    ActionButton(label, Icons.Rounded.PlayArrow, primary = true, onClick = { onPlay(all, target?.index ?: 0) }, modifier = Modifier.fillMaxWidth().focusRequester(primary))
                    Spacer(Modifier.height(8.dp))
                }
                ActionButton(
                    if (isLater) uiContext.getString(R.string.ui_remove_watch_later) else uiContext.getString(R.string.ui_add_watch_later),
                    if (isLater) Icons.Rounded.Check else Icons.Rounded.WatchLater,
                    modifier = Modifier.fillMaxWidth(),
                ) { scope.launch { repo.store.toggleWatchLater(favKey) } }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    if (isFavorite) uiContext.getString(R.string.ui_remove_favorites) else uiContext.getString(R.string.ui_add_favorites),
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) { scope.launch { repo.store.toggleFavorite(favKey) } }
                if (repo.tmdb.configured) {
                    Text(uiContext.getString(R.string.ui_tmdb_notice), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                }
            }
            if (seasons.isEmpty()) {
                EmptyState(Icons.Rounded.VideoLibrary, uiContext.getString(R.string.ui_no_episodes), modifier = Modifier.weight(1f))
            } else {
                val targetEpIndex = remember(episodes, target) {
                    episodes.indexOfFirst { e -> e.id == target?.let { t -> all[t.index].id } }.coerceAtLeast(0)
                }
                val epState = remember(season) { androidx.compose.foundation.lazy.LazyListState(targetEpIndex) }
                val epEntryFocus = remember { SafeFocusRequester() }
                var lastFocusedEp by remember(season) { mutableStateOf<Int?>(null) }
                val epEntryIndex by remember(season, episodes, targetEpIndex) {
                    derivedStateOf {
                        visibleEntryIndex(
                            visibleIndices = epState.layoutInfo.visibleItemsInfo.map { it.index },
                            itemCount = episodes.size,
                            preferred = lastFocusedEp ?: targetEpIndex,
                        )
                    }
                }
                // Middle: seasons as a vertical list; OK / tap selects.
                LazyColumn(
                    Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .lazyFocusBoundary(
                            enterTarget = { if (selectedSeasonVisible) selectedSeasonFocus.target else FocusRequester.Default },
                            exitTarget = { dir ->
                                when (dir) {
                                    FocusDirection.Left -> primary
                                    FocusDirection.Right -> if (epEntryIndex >= 0) epEntryFocus.target else FocusRequester.Cancel
                                    FocusDirection.Up, FocusDirection.Down -> FocusRequester.Cancel
                                    else -> FocusRequester.Default
                                }
                            },
                        ),
                    state = seasonState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    items(seasonKeys, key = { it }) { n ->
                        SeasonChip(
                            uiContext.getString(R.string.ui_season, n),
                            n == season,
                            Modifier.fillMaxWidth().then(if (n == season) selectedSeasonFocus.attach() else Modifier),
                        ) { chosenSeason = n }
                    }
                }
                // Right: episodes of the selected season, enriched with TMDB episode stills/titles/runtimes.
                val tmdbEpisodes by produceState(emptyMap<Int, TmdbEpisodeInfo>(), info.name, info.tmdbId, season) {
                    value = emptyMap()
                    if (repo.tmdb.available()) {
                        value = runCatching {
                            // The TMDB match was made with the provider's title, which can differ from the shown (TMDB) name.
                            val item = repo.content(playlist, ContentType.SERIES).items.firstOrNull { it.id == seriesId }
                            val providerTitle = item?.name ?: info.name
                            repo.tmdb.seasonEpisodes(providerTitle, info.year, season, tmdbId = info.tmdbId ?: item?.tmdbId)
                        }.getOrDefault(emptyMap())
                    }
                }
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .lazyFocusBoundary(
                            enterTarget = { if (epEntryIndex >= 0) epEntryFocus.target else FocusRequester.Default },
                            exitTarget = { dir ->
                                when (dir) {
                                    FocusDirection.Left -> if (selectedSeasonVisible) selectedSeasonFocus.target else FocusRequester.Default
                                    FocusDirection.Right, FocusDirection.Up, FocusDirection.Down -> FocusRequester.Cancel
                                    else -> FocusRequester.Default
                                }
                            },
                        ),
                    state = epState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    // When every episode carries the same leftover name (provider "Hz Omer.S01.E01" style), fill from TMDB or "Bölüm N".
                    val cleaned = seasons[season].orEmpty().map { DisplayText.episodeTitle(it.title, info.name) }
                    val uninformative = cleaned.size > 1 && cleaned.distinct().size == 1
                    itemsIndexed(episodes, key = { _, e -> e.id }) { i, e ->
                        val ep = seasons[season]?.getOrNull(i)
                        val epNumber = ep?.number ?: (i + 1)
                        val tmdbEp = tmdbEpisodes[epNumber]
                        val epTitle = (if (uninformative) "" else cleaned.getOrNull(i).orEmpty())
                            .ifEmpty { tmdbEp?.name.orEmpty() }
                            .ifEmpty { uiContext.getString(R.string.ui_episode_number, epNumber) }
                        val stillUrl = tmdbEp?.still ?: ep?.still ?: info.backdrop
                        val durationLabel = tmdbEp?.runtimeMins?.let { uiContext.getString(R.string.ui_duration_minutes, it) }
                            ?: ep?.duration?.takeIf { it.isNotBlank() && it != "0" && it != "00:00:00" }
                        val allIndex = all.indexOfFirst { it.id == e.id }.coerceAtLeast(0)
                        EpisodeRow(
                            number = epNumber,
                            title = epTitle,
                            stillUrl = stillUrl,
                            durationLabel = durationLabel,
                            progress = progressById[e.id],
                            onClick = { if (resumeById[e.id] != null) pendingEpisode = allIndex else onPlay(all, allIndex) },
                            modifier = if (i == epEntryIndex) epEntryFocus.attach() else Modifier,
                            onFocused = { lastFocusedEp = i },
                        )
                    }
                }
            }
        }
    }
    pendingEpisode?.let { i ->
        val e = all[i]
        ResumeDialog(
            positionMs = resumeById[e.id] ?: 0L,
            onResume = { pendingEpisode = null; onPlay(all, i) },
            onRestart = {
                pendingEpisode = null
                scope.launch { repo.store.savePosition(favoriteKey(playlist, e), 0) }.invokeOnCompletion { onPlay(all, i) }
            },
            onDismiss = { pendingEpisode = null },
        )
    }
}

private fun episodeTag(uiContext: android.content.Context, info: SeriesInfo, index: Int) = info.episodes[index].let { uiContext.getString(R.string.ui_episode_tag, it.season, it.number) }

// One composable for both states so selecting a season does not drop focus.
@Composable
private fun SeasonChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Text else Palette.Surface,
            contentColor = if (selected) Palette.Background else Palette.TextMuted,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.lazyFocusBoundary(
    enterTarget: (FocusDirection) -> FocusRequester = { FocusRequester.Default },
    exitTarget: (FocusDirection) -> FocusRequester = { FocusRequester.Default },
): Modifier = focusProperties {
    enter = enterTarget
    exit = exitTarget
}

@Composable
private fun EpisodeRow(
    number: Int,
    title: String,
    stillUrl: String?,
    durationLabel: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val uiContext = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerClick(onClick)
            .onFocusChanged {
                val now = it.isFocused || it.hasFocus
                if (now && !focused) onFocused()
                focused = now
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface.copy(alpha = 0.7f),
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color(0xFFF5F5F7)), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(128.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.TextMuted)
                if (stillUrl != null) {
                    AsyncImage(
                        model = stillUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0x99000000))),
                )
                if (focused) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFF2B544)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
                if (progress != null && progress > 0.01f && progress < WATCHED_FRACTION) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x66000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(Palette.Accent),
                        )
                    }
                }
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (focused) Color.White else Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val epLabel = uiContext.getString(R.string.ui_episode_number, number)
                val metaLine = listOfNotNull(epLabel.takeUnless { title.equals(epLabel, ignoreCase = true) }, durationLabel)
                    .joinToString(" · ")
                if (metaLine.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        metaLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (progress != null && progress >= WATCHED_FRACTION) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = uiContext.getString(R.string.ui_watched),
                    tint = Palette.Accent,
                    modifier = Modifier.padding(end = 6.dp).size(22.dp),
                )
            }
        }
    }
}
