package dev.iptv.player.ui

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
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.VodInfo
import dev.iptv.player.data.continueWatching
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.latestOf
import dev.iptv.player.data.movieDetails

private class HomeData(val live: List<Stream>, val movies: List<Stream>, val series: List<Stream>)

private class Featured(val stream: Stream, val resuming: Boolean)

/** Landing page: a static hero plus continue watching, recent / favourite channels and the newest titles. */
@Composable
fun HomeSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val state by produceState<Load<HomeData>>(Load.Loading, playlist.id) {
        value = loadCatching {
            suspend fun items(type: ContentType) = runCatching { repo.content(playlist, type).items }
            val results = ContentType.entries.associateWith { items(it) }
            // Fail only when nothing at all could be loaded.
            if (results.values.all { it.isFailure }) throw results.values.first().exceptionOrNull() ?: Exception("İçerik yüklenemedi")
            HomeData(
                live = results.getValue(ContentType.LIVE).getOrDefault(emptyList()),
                movies = results.getValue(ContentType.MOVIE).getOrDefault(emptyList()),
                series = results.getValue(ContentType.SERIES).getOrDefault(emptyList()),
            )
        }
    }
    val history by repo.history.entries.collectAsState(initial = emptyList())

    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> {
            val data = s.value
            val rows = remember(data, history, actions.favorites) { buildHomeRows(playlist, data, history.filter { it.playlistId == playlist.id }, actions.favorites) }
            if (rows.isEmpty()) {
                EmptyState(Icons.Rounded.Home, "Henüz içerik yok", "Playlist içeriği yüklendiğinde burada görünecek")
                return
            }
            val featured = remember(rows) {
                rows.firstOrNull { it.direct }?.items?.firstOrNull()?.let { Featured(it, true) }
                    ?: rows.firstOrNull { it.title.startsWith("Son eklenen") }?.items?.firstOrNull()?.let { Featured(it, false) }
            }
            Column(Modifier.fillMaxSize()) {
                featured?.let { FeaturedHero(repo, playlist, it) }
                MediaRows(rows, playlist, actions, onFocused = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun buildHomeRows(
    playlist: Playlist,
    data: HomeData,
    history: List<dev.iptv.player.data.HistoryEntry>,
    favorites: Set<String>,
): List<MediaRow> {
    val liveById = data.live.associateBy { it.id }
    val movieById = data.movies.associateBy { it.id }
    val resume = continueWatching(history).map { e -> if (e.type == ContentType.MOVIE) movieById[e.id] ?: e.toStream() else e.toStream() }
    val recentLive = history.filter { it.type == ContentType.LIVE }.take(20)
        .mapNotNull { e -> liveById[e.id] ?: if (liveById.isEmpty()) e.toStream() else null }
    val favLive = data.live.filter { favoriteKey(playlist, it) in favorites }.take(40)
    return listOf(
        MediaRow("İzlemeye devam et", resume, direct = true),
        MediaRow("Son izlenen kanallar", recentLive),
        MediaRow("Favori kanallar", favLive),
        MediaRow("Son eklenen filmler", latestOf(data.movies, 30)),
        MediaRow("Son eklenen diziler", latestOf(data.series, 30)),
    ).filter { it.items.isNotEmpty() }
}

@Composable
private fun FeaturedHero(repo: Repository, playlist: Playlist, featured: Featured) {
    val stream = featured.stream
    val info by produceState<VodInfo?>(null, stream.id) {
        value = if (stream.type == ContentType.MOVIE) runCatching { repo.movieDetails(playlist, stream) }.getOrNull() else null
    }
    Hero(
        title = stream.name,
        image = info?.backdrop ?: stream.backdrop ?: stream.logo,
        meta = listOfNotNull(
            if (featured.resuming) "Kaldığın yerden devam et" else "Son eklenen",
            info?.year ?: stream.year,
            (info?.rating ?: stream.rating)?.let { "★ $it" },
            info?.genre ?: stream.genre,
        ),
        plot = info?.plot ?: stream.plot,
    )
}
