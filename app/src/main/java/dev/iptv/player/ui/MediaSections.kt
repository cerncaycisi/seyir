package dev.iptv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.iptv.player.data.Content
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.VodInfo
import dev.iptv.player.data.favoriteKey
import kotlinx.coroutines.delay

data class MediaRow(val title: String, val items: List<Stream>)

private fun buildRows(content: Content): List<MediaRow> {
    val byCategory = content.items.groupBy { it.categoryId }
    val known = content.categories.mapNotNull { c -> byCategory[c.id]?.let { MediaRow(c.name, it) } }
    val knownIds = content.categories.mapTo(HashSet()) { it.id }
    val rest = content.items.filter { it.categoryId !in knownIds }
    return if (rest.isEmpty()) known else known + MediaRow("Diğer", rest)
}

private suspend fun allStreams(repo: Repository, playlist: Playlist): List<Stream> =
    ContentType.entries.flatMap { t -> runCatching { repo.content(playlist, t).items }.getOrDefault(emptyList()) }

@Composable
fun VodSection(repo: Repository, playlist: Playlist, type: ContentType, actions: HomeActions) {
    val state by produceState<Load<List<MediaRow>>>(Load.Loading, playlist.id, type) {
        value = loadCatching { buildRows(repo.content(playlist, type)) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyState(Icons.Rounded.Movie, if (type == ContentType.MOVIE) "Film bulunamadı" else "Dizi bulunamadı")
        } else {
            VodBrowser(repo, playlist, type, s.value, actions)
        }
    }
}

@Composable
private fun VodBrowser(repo: Repository, playlist: Playlist, type: ContentType, rows: List<MediaRow>, actions: HomeActions) {
    var focused by remember { mutableStateOf(rows.first().items.first()) }
    val info by produceState<VodInfo?>(null, focused) {
        value = null
        if (type == ContentType.MOVIE) {
            delay(350)
            value = runCatching { repo.vodInfo(playlist, focused) }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Hero(
            title = focused.name,
            image = info?.backdrop ?: focused.backdrop ?: focused.logo,
            meta = listOfNotNull(
                info?.year ?: focused.year,
                (info?.rating ?: focused.rating)?.let { "★ $it" },
                info?.genre ?: focused.genre,
                info?.duration,
            ),
            plot = info?.plot ?: focused.plot,
        )
        MediaRows(rows, playlist, actions, onFocused = { focused = it }, modifier = Modifier.weight(1f))
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
        itemsIndexed(rows) { r, row ->
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
    LaunchedEffect(target) {
        if (target >= 0) {
            rowState.scrollToItem(target)
            withFrameNanos { }
            runCatching { requester.requestFocus() }
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
                val open = { actions.open(row.items, i) }
                val toggle = { actions.toggleFavorite(s) }
                if (s.type == ContentType.LIVE) {
                    ChannelCard(s, open, toggle, { onFocused(s) }, mod)
                } else {
                    PosterCard(s, favoriteKey(playlist, s) in actions.favorites, open, toggle, { onFocused(s) }, mod)
                }
            }
        }
    }
}

@Composable
fun FavoritesSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val state by produceState<Load<List<Stream>>>(Load.Loading, playlist.id) {
        value = loadCatching { allStreams(repo, playlist) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Favoriler")
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Err -> ErrorState(s.message)
            is Load.Ok -> {
                val favorites = s.value.filter { favoriteKey(playlist, it) in actions.favorites }
                val rows = listOf(
                    MediaRow("Kanallar", favorites.filter { it.type == ContentType.LIVE }),
                    MediaRow("Filmler", favorites.filter { it.type == ContentType.MOVIE }),
                    MediaRow("Diziler", favorites.filter { it.type == ContentType.SERIES }),
                ).filter { it.items.isNotEmpty() }
                if (rows.isEmpty()) {
                    EmptyState(Icons.Rounded.FavoriteBorder, "Henüz favori yok", "Bir içeriğin üzerinde OK tuşunu basılı tutarak ekleyin")
                } else {
                    MediaRows(rows, playlist, actions, onFocused = {})
                }
            }
        }
    }
}

@Composable
fun SearchSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val all by produceState<List<Stream>?>(null, playlist.id) { value = allStreams(repo, playlist) }
    val q = query.trim()
    val rows = remember(all, q) {
        val source = all
        if (source == null || q.length < 2) {
            emptyList()
        } else {
            val hits = source.filter { dev.iptv.player.data.TextSearch.matches(it.name, q) }
            listOf(
                MediaRow("Kanallar", hits.filter { it.type == ContentType.LIVE }.take(60)),
                MediaRow("Filmler", hits.filter { it.type == ContentType.MOVIE }.take(60)),
                MediaRow("Diziler", hits.filter { it.type == ContentType.SERIES }.take(60)),
            ).filter { it.items.isNotEmpty() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Ara", "Kanallar, filmler ve diziler arasında")
        TvTextField(query, { query = it }, "Ne izlemek istersiniz?", Modifier.widthIn(max = 760.dp), leadingIcon = Icons.Rounded.Search)
        Spacer(Modifier.height(20.dp))
        when {
            all == null -> LoadingState()
            q.length < 2 -> EmptyState(Icons.Rounded.Search, "Aramaya başlayın", "En az 2 harf yazın")
            rows.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "Sonuç bulunamadı", "\"$q\" için eşleşen içerik yok")
            else -> MediaRows(rows, playlist, actions, onFocused = {})
        }
    }
}
