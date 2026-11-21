package dev.iptv.player.ui

import android.annotation.SuppressLint

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import dev.iptv.player.data.movieDetails

private class HomeData(val contents: Map<ContentType, Content>)

private class Featured(val stream: Stream, val resuming: Boolean)

/** Landing page: a static hero plus continue watching, recent / favourite channels and the newest titles. */
@Composable
fun HomeSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val uiContext = LocalContext.current
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<Load<HomeData>>(Load.Loading, playlist.id) {
        value = loadCatching {
            suspend fun items(type: ContentType) = runCatching { repo.content(playlist, type) }
            val results = ContentType.entries.associateWith { items(it) }
            // Fail only when nothing at all could be loaded.
            if (results.values.all { it.isFailure }) throw results.values.first().exceptionOrNull() ?: Exception(uiContext.getString(R.string.ui_content_failed))
            HomeData(results.mapValues { it.value.getOrDefault(Content(emptyList(), emptyList())) })
        }
    }
    val history by repo.history.entries.collectAsState(initial = emptyList())

    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> {
            val data = s.value
            val rows = remember(uiContext, data, history, actions.favorites, actions.rules) {
                buildHomeRows(uiContext, playlist, data, history.filter { it.playlistId == playlist.id }, actions.favorites, actions.rules)
            }
            if (rows.isEmpty()) {
                EmptyState(Icons.Rounded.Home, uiContext.getString(R.string.ui_content_empty), uiContext.getString(R.string.ui_content_empty_hint))
                return
            }
            val featured = remember(rows) {
                rows.firstOrNull { it.direct }?.items?.firstOrNull()?.let { Featured(it, true) }
                    ?: rows.firstOrNull { it.items.firstOrNull()?.type in setOf(ContentType.MOVIE, ContentType.SERIES) }?.items?.firstOrNull()?.let { Featured(it, false) }
            }
            Column(Modifier.fillMaxSize()) {
                featured?.let { FeaturedHero(repo, playlist, it) }
                MediaRows(rows, playlist, actions, onFocused = {}, modifier = Modifier.weight(1f))
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
    rules: ContentRules,
): List<MediaRow> {
    val visible = data.contents.mapValues { (type, content) -> visibleContentItems(playlist, type, content, rules) }
    val resume = continueWatching(history).mapNotNull { visibleHistoryStream(playlist, it, data.contents, rules) }
    val recentLive = history.filter { it.type == ContentType.LIVE }.take(20)
        .mapNotNull { visibleHistoryStream(playlist, it, data.contents, rules) }
    val favLive = visible[ContentType.LIVE].orEmpty().filter { favoriteKey(playlist, it) in favorites }.take(40)
    return listOf(
        MediaRow(uiContext.getString(R.string.ui_continue_watching), resume, direct = true),
        MediaRow(uiContext.getString(R.string.ui_recent_channels), recentLive),
        MediaRow(uiContext.getString(R.string.ui_favorite_channels), favLive),
        MediaRow(uiContext.getString(R.string.ui_recent_movies), latestOf(visible[ContentType.MOVIE].orEmpty(), 30)),
        MediaRow(uiContext.getString(R.string.ui_recent_series), latestOf(visible[ContentType.SERIES].orEmpty(), 30)),
    ).filter { it.items.isNotEmpty() }
}

@Composable
private fun FeaturedHero(repo: Repository, playlist: Playlist, featured: Featured) {
    val uiContext = LocalContext.current
    val stream = featured.stream
    // Lint misses the direct ProduceStateScope.value assignment in this producer.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val info by produceState<VodInfo?>(null, stream.id) {
        value = if (stream.type == ContentType.MOVIE) runCatching { repo.movieDetails(playlist, stream) }.getOrNull() else null
    }
    Hero(
        title = stream.name,
        image = info?.backdrop ?: stream.backdrop ?: stream.logo,
        meta = listOfNotNull(
            if (featured.resuming) uiContext.getString(R.string.ui_resume) else uiContext.getString(R.string.ui_recent),
            info?.year ?: stream.year,
            (info?.rating ?: stream.rating)?.let { "★ $it" },
            info?.genre ?: stream.genre,
        ),
        plot = info?.plot ?: stream.plot,
    )
}
