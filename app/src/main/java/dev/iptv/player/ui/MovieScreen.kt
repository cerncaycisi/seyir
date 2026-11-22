package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.VodInfo
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.movieDetails
import kotlinx.coroutines.launch
import java.io.IOException

private class MovieData(val stream: Stream, val similar: List<Stream>, val resumeMs: Long)

@Composable
fun MovieScreen(
    repo: Repository,
    playlist: Playlist,
    movieId: String,
    onPlay: (List<Stream>, Int) -> Unit,
    onMovie: (Stream) -> Unit,
) {
    val uiContext = LocalContext.current
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<MovieData>>(Load.Loading, playlist.id, movieId) {
        value = loadCatching {
            val items = repo.content(playlist, ContentType.MOVIE).items
            val stream = items.firstOrNull { it.id == movieId } ?: throw IOException(uiContext.getString(R.string.ui_no_movie))
            val similar = items.filter { it.categoryId == stream.categoryId && it.id != stream.id }.take(30)
            val resume = repo.store.position(favoriteKey(playlist, stream))
            MovieData(stream, similar, if (resume >= dev.iptv.player.data.HistoryEntry.MIN_RESUME_MS) resume else 0L)
        }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> DetailViewport { MovieDetail(repo, playlist, s.value, onPlay, onMovie) }
    }
}

@Composable
private fun MovieDetail(
    repo: Repository,
    playlist: Playlist,
    data: MovieData,
    onPlay: (List<Stream>, Int) -> Unit,
    onMovie: (Stream) -> Unit,
) {
    val uiContext = LocalContext.current
    val stream = data.stream
    val key = favoriteKey(playlist, stream)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())
    val isFavorite = key in favorites
    val watchLater by repo.store.watchLater.collectAsState(initial = emptySet())
    val isLater = key in watchLater
    val progress by repo.history.entries.collectAsState(initial = emptyList())
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val info by produceState<VodInfo?>(null, stream.id) { value = runCatching { repo.movieDetails(playlist, stream) }.getOrNull() }
    val primary = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { primary.requestFocus() }
    }

    val image = info?.backdrop ?: stream.backdrop ?: info?.poster ?: stream.logo
    Box(Modifier.fillMaxSize()) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.65f).fillMaxHeight(0.8f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Background, 0.42f to Palette.Background, 1f to Palette.Background.copy(alpha = 0.25f)),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.8f to Palette.Background)))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp)) {
            Text(
                stream.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    info?.year ?: stream.year,
                    (info?.rating ?: stream.rating)?.let { "★ $it" },
                    info?.genre ?: stream.genre,
                    durationLabel(uiContext, info),
                ).forEach { Pill(it) }
            }
            (info?.plot ?: stream.plot)?.let {
                if (info?.plotLanguage == "en") Text(uiContext.getString(R.string.ux_plot_english), color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
                ExpandablePlot(stream.name, it, Modifier.padding(top = 12.dp).widthIn(max = 560.dp))
            }
            info?.director?.let { CreditLine(uiContext.getString(R.string.ui_director), it) }
            info?.cast?.let { CreditLine(uiContext.getString(R.string.ui_cast), it) }

            // One row of labelled actions, primary first (Netflix-like); no long-press needed.
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (data.resumeMs > 0) {
                    ActionButton(
                        uiContext.getString(R.string.ui_resume_position, formatPosition(data.resumeMs)),
                        Icons.Rounded.PlayArrow,
                        primary = true,
                        onClick = { onPlay(listOf(stream), 0) },
                        modifier = Modifier.focusRequester(primary),
                    )
                } else {
                    ActionButton(uiContext.getString(R.string.ui_play), Icons.Rounded.PlayArrow, primary = true, onClick = { onPlay(listOf(stream), 0) }, modifier = Modifier.focusRequester(primary))
                }
                if (data.resumeMs > 0) {
                    ActionButton(uiContext.getString(R.string.ui_restart), Icons.Rounded.Replay) {
                        scope.launch {
                            repo.store.savePosition(key, 0)
                            onPlay(listOf(stream), 0)
                        }
                    }
                }
                ActionButton(
                    if (isLater) uiContext.getString(R.string.ui_remove_watch_later) else uiContext.getString(R.string.ui_add_watch_later),
                    if (isLater) Icons.Rounded.Check else Icons.Rounded.WatchLater,
                ) { scope.launch { repo.store.toggleWatchLater(key) } }
                ActionButton(
                    if (isFavorite) uiContext.getString(R.string.ui_remove_favorites) else uiContext.getString(R.string.ui_add_favorites),
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                ) { scope.launch { repo.store.toggleFavorite(key) } }
                info?.trailer?.let { id -> ActionButton(uiContext.getString(R.string.ui_trailer), Icons.Rounded.Theaters) { openTrailer(context, id) } }
            }

            if (data.similar.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionTitle(uiContext.getString(R.string.ui_similar))
                val byKey = progress.associate { it.favKey to (if (it.finished) 1f else it.fraction) }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    items(data.similar, key = { it.id }) { s ->
                        PosterCard(
                            s,
                            favoriteKey(playlist, s) in favorites,
                            onClick = { onMovie(s) },
                            onLongClick = { scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, s)) } },
                            onFocused = {},
                            progress = byKey[favoriteKey(playlist, s)],
                        )
                    }
                }
            }
            if (repo.tmdb.configured) {
                Spacer(Modifier.height(8.dp))
                Text(
                    uiContext.getString(R.string.ui_tmdb_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun CreditLine(label: String, value: String) {
    Text(
        "$label: $value",
        modifier = Modifier.padding(top = 6.dp).widthIn(max = 560.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Palette.TextFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pill-shaped remote-friendly button shared by the movie and series pages. */
@Composable
internal fun ActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTapTarget).pointerClick(onClick),
        // Design v2: white primary, translucent secondary; focus turns any button white with a ring.
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color(0xFFF5F5F7) else Color(0x616E7078),
            contentColor = if (primary) Color(0xFF07080A) else Color(0xFFF5F5F7),
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF5F5F7)), inset = (-4).dp, shape = RoundedCornerShape(13.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.heightIn(min = MinTapTarget).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

internal fun formatPosition(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun durationLabel(uiContext: Context, info: VodInfo?): String? {
    info ?: return null
    info.durationSecs?.let { secs ->
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) uiContext.getString(R.string.ui_duration_hours, h, m) else uiContext.getString(R.string.ui_duration_minutes, m)
    }
    return info.duration
}

private fun openTrailer(context: Context, videoId: String) {
    fun launch(uri: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    try {
        launch("vnd.youtube:$videoId")
    } catch (_: ActivityNotFoundException) {
        runCatching { launch("https://www.youtube.com/watch?v=$videoId") }
    }
}
