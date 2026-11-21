package dev.iptv.player.ui

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

/** [direct] rows resume on click (continue watching) instead of opening the detail page. */
data class MediaRow(val title: String, val items: List<Stream>, val direct: Boolean = false)

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
    val state by produceState<Load<Content>>(Load.Loading, playlist.id, type) {
        value = loadCatching { repo.content(playlist, type) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> if (s.value.items.isEmpty()) {
            EmptyState(Icons.Rounded.Movie, if (type == ContentType.MOVIE) "Film bulunamadı" else "Dizi bulunamadı")
        } else {
            VodBrowser(repo, playlist, type, s.value, actions)
        }
    }
}

private fun buildVodRows(content: Content, sort: VodSort, genre: String?): List<MediaRow> {
    val items = filterByGenre(content.items, genre)
    if (items.isEmpty()) return emptyList()
    // Sorting replaces the category rows with one flat, sorted row.
    if (sort != VodSort.DEFAULT) return listOf(MediaRow("Sıralama: ${sort.label}", sortStreams(items, sort)))
    val base = buildRows(Content(content.categories, items))
    return listOf(MediaRow("Son eklenenler", latestOf(items, 30))) + base
}

@Composable
private fun VodBrowser(repo: Repository, playlist: Playlist, type: ContentType, content: Content, actions: HomeActions) {
    var sort by rememberSaveable { mutableStateOf(VodSort.DEFAULT) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val genres = remember(content) { genresOf(content.items).take(24) }
    val rows = remember(content, sort, genre) { buildVodRows(content, sort, genre) }
    var focused by remember { mutableStateOf<Stream?>(null) }
    val shown = focused ?: rows.firstOrNull()?.items?.firstOrNull()
    val info by produceState<VodInfo?>(null, shown) {
        value = null
        if (type == ContentType.MOVIE && shown != null) {
            delay(350)
            value = runCatching { repo.vodInfo(playlist, shown) }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (shown != null) {
            Hero(
                title = shown.name,
                image = info?.backdrop ?: shown.backdrop ?: shown.logo,
                meta = listOfNotNull(
                    info?.year ?: shown.year,
                    (info?.rating ?: shown.rating)?.let { "★ $it" },
                    info?.genre ?: shown.genre,
                    info?.duration,
                ),
                plot = info?.plot ?: shown.plot,
            )
        }
        // Chips cycle on OK so no dialog or dropdown is needed on a remote.
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlChip("Sırala", sort.label) { sort = VodSort.entries[(sort.ordinal + 1) % VodSort.entries.size] }
            if (genres.isNotEmpty()) {
                ControlChip("Tür", genre ?: "Tümü") {
                    val i = genre?.let { genres.indexOf(it) } ?: -1
                    genre = genres.getOrNull(i + 1)
                }
            }
        }
        if (rows.isEmpty()) {
            EmptyState(Icons.Rounded.SearchOff, "Bu türde içerik yok", "Başka bir tür seçin")
        } else {
            MediaRows(rows, playlist, actions, onFocused = { focused = it }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ControlChip(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label:", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
        itemsIndexed(rows, key = { i, row -> "$i|${row.title}" }) { r, row ->
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
                val open = { if (row.direct) actions.resume(s) else actions.open(row.items, i) }
                val toggle = { actions.toggleFavorite(s) }
                if (s.type == ContentType.LIVE) {
                    ChannelCard(s, open, toggle, { onFocused(s) }, mod)
                } else {
                    PosterCard(s, favoriteKey(playlist, s) in actions.favorites, open, toggle, { onFocused(s) }, mod, progress = actions.progress[favoriteKey(playlist, s)])
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
            val hits = source.filter { it.name.contains(q, ignoreCase = true) }
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
