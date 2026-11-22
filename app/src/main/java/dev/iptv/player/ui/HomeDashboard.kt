package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Refresh
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import dev.iptv.player.data.seriesDetails
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentRules
import dev.iptv.player.data.HistoryEntry
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.VodInfo
import dev.iptv.player.data.continueWatching
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.latestOf
import dev.iptv.player.data.recentStreams
import dev.iptv.player.data.movieDetails

private class HomeData(val contents: Map<ContentType, Content>)

private class Featured(val stream: Stream, val resuming: Boolean)

/** Landing page: a static hero plus continue watching, recent / favourite channels and the newest titles. */
@Composable
fun HomeSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val uiContext = LocalContext.current
    val refreshWork by remember(repo, playlist.id) {
        dev.iptv.player.data.RefreshWorker.observe(repo.appContext, playlist.id)
    }.collectAsState(initial = null)
    val refreshing = refreshWork?.state?.isFinished == false
    val refreshTick = refreshWork?.takeIf { it.state == androidx.work.WorkInfo.State.SUCCEEDED }?.id
    val refreshMessage = when (refreshWork?.state) {
        androidx.work.WorkInfo.State.SUCCEEDED -> uiContext.getString(R.string.ux_refresh_done)
        androidx.work.WorkInfo.State.FAILED -> uiContext.getString(R.string.ux_refresh_failed)
        else -> null
    }
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<HomeData>>(Load.Loading, playlist.id, refreshTick) {
        value = loadCatching {
            suspend fun items(type: ContentType) = runCatching { repo.content(playlist, type) }
            val results = ContentType.entries.associateWith { items(it) }
            // Fail only when nothing at all could be loaded.
            if (results.values.all { it.isFailure }) throw results.values.first().exceptionOrNull() ?: Exception(uiContext.getString(R.string.ui_content_failed))
            HomeData(results.mapValues { it.value.getOrDefault(Content(emptyList(), emptyList())) })
        }
    }
    // null until the first read: building rows from an empty history first and reshuffling them a moment later
    // dropped the focus that was being restored after returning from a detail page.
    val historyOrNull by repo.history.entries.collectAsState(initial = null)

    val history = historyOrNull
    var shownHero by remember { mutableStateOf<Featured?>(null) }
    Box(Modifier.fillMaxSize()) {
    shownHero?.let { HomeBackdrop(repo, playlist, it) }
    Column(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                refreshMessage?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted) }
            }
            PointerOutlinedButton(enabled = !refreshing, onClick = {
                dev.iptv.player.data.RefreshWorker.request(repo.appContext, playlist.id)
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(uiContext.getString(if (refreshing) R.string.ux_refreshing else R.string.ux_refresh), Modifier.padding(start = 8.dp))
            }
        }
        when (val s = if (history == null) Load.Loading else state) {
            Load.Loading -> LoadingState()
            is Load.Err -> ErrorState(s.message)
            is Load.Ok -> {
                val data = s.value
                // Built off the main thread: it scans every movie, series and channel of the playlist.
                val rows = rememberBackground(uiContext, data, history, actions.favorites, actions.watchLater, actions.rules) {
                    buildHomeRows(uiContext, playlist, data, history.orEmpty().filter { it.playlistId == playlist.id }, actions.favorites, actions.watchLater, actions.rules)
                }
                if (rows == null) {
                    LoadingState()
                    return@Column
                }
                if (rows.isEmpty()) {
                    EmptyState(Icons.Rounded.Home, uiContext.getString(R.string.ui_content_empty), uiContext.getString(R.string.ui_content_empty_hint))
                    return@Column
                }
                val featured = remember(rows) {
                    rows.firstOrNull { it.direct }?.items?.firstOrNull()?.let { Featured(it, true) }
                        ?: rows.firstOrNull { it.items.firstOrNull()?.type in setOf(ContentType.MOVIE, ContentType.SERIES) }?.items?.firstOrNull()?.let { Featured(it, false) }
                }
                // Netflix-style: the hero follows the focused card (channels keep the last title shown).
                var focusedVod by remember { mutableStateOf<Stream?>(null) }
                val resumeIds = remember(rows) { rows.filter { it.direct }.flatMap { r -> r.items.map { it.id } }.toSet() }
                val shown = focusedVod?.let { Featured(it, it.id in resumeIds) } ?: featured
                // The backdrop follows focus only once it settles: swapping a full-screen image on every D-pad step
                // made each frame miss its deadline on the TV.
                LaunchedEffect(shown?.stream?.id, shown?.resuming) {
                    if (shownHero != null) kotlinx.coroutines.delay(450)
                    shownHero = shown
                }
                Column(Modifier.fillMaxSize()) {
                    shown?.let { FeaturedHero(repo, playlist, it) }
                    MediaRows(rows, playlist, actions, onFocused = { if (it.type != ContentType.LIVE) focusedVod = it },
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
    }
}

private fun buildHomeRows(
    uiContext: android.content.Context,
    playlist: Playlist,
    data: HomeData,
    history: List<dev.iptv.player.data.HistoryEntry>,
    favorites: Set<String>,
    watchLater: Set<String>,
    rules: ContentRules,
): List<MediaRow> {
    val visible = data.contents.mapValues { (type, content) -> visibleContentItems(playlist, type, content, rules) }
    val recentLive = history.filter { it.type == ContentType.LIVE }.take(20)
        .mapNotNull { visibleHistoryStream(playlist, it, data.contents, rules) }
    val favLive = visible[ContentType.LIVE].orEmpty().filter { favoriteKey(playlist, it) in favorites }.take(40)
    val vod = visible[ContentType.MOVIE].orEmpty() + visible[ContentType.SERIES].orEmpty()
    val resume = recentStreams(continueWatching(history), vod, playlist.id)
    val resumeKeys = resume.mapTo(HashSet()) { favoriteKey(playlist, it) }
    val later = vod.filter { favoriteKey(playlist, it) in watchLater && favoriteKey(playlist, it) !in resumeKeys }.take(20)
    val live = (recentLive + favLive + visible[ContentType.LIVE].orEmpty())
        .filterNot { it.name.trimStart().startsWith("∞∞") }
        .distinctBy { it.id }.take(12)
    return listOf(
        MediaRow(uiContext.getString(R.string.ui_continue_watching), resume, direct = true),
        MediaRow(uiContext.getString(R.string.ui_live_tv), live),
        MediaRow(uiContext.getString(R.string.ui_cat_watch_later), later),
        MediaRow(uiContext.getString(R.string.ui_recent_movies), latestOf(visible[ContentType.MOVIE].orEmpty(), 30)),
        MediaRow(uiContext.getString(R.string.ui_recent_series), latestOf(visible[ContentType.SERIES].orEmpty(), 30)),
    ).filter { it.items.isNotEmpty() }
}

/** Details for the hero: provider data first, TMDB-filled movie/series details when available (cached). */
@Composable
private fun rememberHeroInfo(repo: Repository, playlist: Playlist, stream: Stream): VodInfo? {
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val info by produceState<VodInfo?>(null, stream.id) {
        // Short pause so scrolling along a row does not fetch details for every poster passed.
        kotlinx.coroutines.delay(300)
        value = when (stream.type) {
            ContentType.MOVIE -> runCatching { repo.movieDetails(playlist, stream) }.getOrNull()
            ContentType.SERIES -> runCatching { repo.seriesDetails(playlist, stream.id) }.getOrNull()?.let {
                VodInfo(plot = it.plot, backdrop = it.backdrop, genre = it.genre, duration = null, year = it.year, rating = it.rating)
            }
            else -> null
        }
    }
    return info
}

/** Edge-to-edge backdrop of the focused title with cinematic scrims (design v2). */
@Composable
private fun HomeBackdrop(repo: Repository, playlist: Playlist, featured: Featured) {
    val stream = featured.stream
    val info = rememberHeroInfo(repo, playlist, stream)
    val image = info?.backdrop ?: stream.backdrop ?: stream.logo
    val context = LocalContext.current
    // Image and scrims cover only the top-right area: full-screen layers cost the TV GPU every frame.
    Box(Modifier.fillMaxSize()) {
        // Offscreen layer: image + scrims are flattened into one cached texture, so scrolling rows above it
        // redraw one bitmap instead of three full-area layers (measured on the TCL TV).
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.62f)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
            if (image != null) {
                AsyncImage(
                model = coil.request.ImageRequest.Builder(context).data(image).crossfade(250).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Palette.Background, 0.12f to Palette.Background.copy(alpha = 0.85f), 0.45f to Palette.Background.copy(alpha = 0.3f), 0.75f to Color.Transparent,
            )))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Palette.Background.copy(alpha = 0.5f), 0.18f to Color.Transparent, 0.6f to Color.Transparent, 1f to Palette.Background,
            )))
        }
    }
}

@Composable
private fun FeaturedHero(repo: Repository, playlist: Playlist, featured: Featured) {
    val uiContext = LocalContext.current
    val stream = featured.stream
    val info = rememberHeroInfo(repo, playlist, stream)
    Column(Modifier.fillMaxWidth(0.52f).height(196.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (featured.resuming) {
            Text(uiContext.getString(R.string.ui_resume).uppercase(), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = Color(0xFFF2B544))
        }
        Text(stream.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Palette.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(info?.year ?: stream.year, info?.genre ?: stream.genre)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            (info?.rating ?: stream.rating)?.takeIf { it.isNotBlank() }?.let {
                Text("★ $it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF2B544))
            }
            meta.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, maxLines = 1) }
        }
        (info?.plot ?: stream.plot)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
