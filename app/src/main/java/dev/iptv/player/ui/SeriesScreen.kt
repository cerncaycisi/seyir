package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import dev.iptv.player.data.Repository
import dev.iptv.player.data.SeriesInfo
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.seriesDetails
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
        is Load.Ok -> SeriesDetail(repo, playlist, seriesId, s.value, onPlay)
    }
}

private class ContinueTarget(val index: Int, val resuming: Boolean)

@Composable
private fun SeriesDetail(repo: Repository, playlist: Playlist, seriesId: String, info: SeriesInfo, onPlay: (List<Stream>, Int) -> Unit) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val seasons = remember(info) { info.episodes.groupBy { it.season }.toSortedMap() }
    // Every episode as a playable stream; ids and series id let history and deep links find them again.
    val all = remember(uiContext, info) {
        info.episodes.map { e ->
            Stream(e.id, uiContext.getString(R.string.ui_episode_name, info.name, e.season, e.number, e.title), info.cover, e.url, "", ContentType.SERIES, seriesId = seriesId)
        }
    }
    val seriesStream = remember(info) { Stream(seriesId, info.name, info.cover, "", "", ContentType.SERIES) }
    val favKey = favoriteKey(playlist, seriesStream)
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())
    val isFavorite = favKey in favorites
    val history by repo.history.entries.collectAsState(initial = emptyList())
    val progressById = remember(history) {
        history.filter { it.seriesId == seriesId && it.playlistId == playlist.id }
            .associate { it.id to (if (it.finished) 1f else it.fraction) }
    }
    val target = remember(history, all) {
        val last = history.filter { it.seriesId == seriesId && it.playlistId == playlist.id }.maxByOrNull { it.updatedAt }
        val idx = last?.let { l -> all.indexOfFirst { it.id == l.id } } ?: -1
        when {
            last == null || idx < 0 -> null
            !last.finished -> ContinueTarget(idx, true)
            idx < all.lastIndex -> ContinueTarget(idx + 1, false)
            else -> null
        }
    }
    var chosenSeason by rememberSaveable { mutableIntStateOf(-1) }
    val season = when {
        chosenSeason >= 0 -> chosenSeason
        target != null -> info.episodes[target.index].season
        else -> seasons.keys.firstOrNull() ?: 1
    }
    val episodes = remember(info, season) { all.filterIndexed { i, _ -> info.episodes[i].season == season } }
    val primary = remember { FocusRequester() }
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

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            Text(
                info.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    info.year,
                    info.rating?.let { "★ $it" },
                    info.genre,
                    uiContext.getString(R.string.ui_season_count, seasons.size),
                    uiContext.getString(R.string.ui_episode_count, info.episodes.size),
                ).forEach { Pill(it) }
            }
            info.plot?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 12.dp).widthIn(max = 560.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            if (seasons.isEmpty()) {
                EmptyState(Icons.Rounded.VideoLibrary, uiContext.getString(R.string.ui_no_episodes))
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp)) {
                val label = when {
                    target == null -> uiContext.getString(R.string.ui_play)
                    target.resuming -> uiContext.getString(R.string.ui_resume_episode, episodeTag(uiContext, info, target.index))
                    else -> uiContext.getString(R.string.ui_next_episode, episodeTag(uiContext, info, target.index))
                }
                ActionButton(
                    label,
                    Icons.Rounded.PlayArrow,
                    primary = true,
                    onClick = { onPlay(all, target?.index ?: 0) },
                    modifier = Modifier.focusRequester(primary),
                )
                ActionButton(
                    if (isFavorite) uiContext.getString(R.string.ui_in_favorites) else uiContext.getString(R.string.ui_add_favorites),
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                ) { scope.launch { repo.store.toggleFavorite(favKey) } }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(seasons.keys.toList()) { n -> SeasonChip(uiContext.getString(R.string.ui_season, n), n == season) { chosenSeason = n } }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.widthIn(max = 640.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(episodes, key = { _, e -> e.id }) { i, e ->
                    val ep = seasons[season]?.getOrNull(i)
                    EpisodeRow(
                        number = ep?.number ?: (i + 1),
                        title = ep?.title ?: e.name,
                        progress = progressById[e.id],
                        onClick = { onPlay(episodes, i) },
                    )
                }
            }
            if (repo.tmdb.configured) {
                Text(
                    uiContext.getString(R.string.ui_tmdb_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

private fun episodeTag(uiContext: android.content.Context, info: SeriesInfo, index: Int) = info.episodes[index].let { uiContext.getString(R.string.ui_episode_tag, it.season, it.number) }

// One composable for both states so selecting a season does not drop focus.
@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Text else Palette.Surface,
            contentColor = if (selected) Palette.Background else Palette.TextMuted,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EpisodeRow(number: Int, title: String, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val uiContext = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).pointerClick(onClick).onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface.copy(alpha = 0.7f),
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Primary), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (focused) Palette.Primary else Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (focused) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                } else {
                    Text("$number", style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Spacer(Modifier.width(12.dp))
                if (progress >= WATCHED_FRACTION) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = uiContext.getString(R.string.ui_watched), tint = Palette.Accent, modifier = Modifier.size(22.dp))
                } else if (progress > 0.01f) {
                    ProgressBar(progress, Modifier.width(64.dp))
                }
            }
        }
    }
}
