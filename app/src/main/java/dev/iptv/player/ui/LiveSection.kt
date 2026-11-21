package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.delay

private const val ALL = "*"

@Composable
fun LiveSection(repo: Repository, playlist: Playlist, actions: HomeActions) {
    val state by produceState<Load<Content>>(Load.Loading, playlist.id) {
        value = loadCatching { repo.content(playlist, ContentType.LIVE) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> LiveBrowser(repo, playlist, s.value, actions)
    }
}

@Composable
private fun LiveBrowser(repo: Repository, playlist: Playlist, content: Content, actions: HomeActions) {
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val counts = remember(content) { content.items.groupingBy { it.categoryId }.eachCount() }
    val categories = remember(content) {
        listOf(Category(ALL, "Tümü")) + content.categories.filter { (counts[it.id] ?: 0) > 0 }
    }
    val items = remember(content, selected) {
        if (selected == ALL) content.items else content.items.filter { it.categoryId == selected }
    }
    val categoryName = categories.firstOrNull { it.id == selected }?.name ?: "Tümü"
    val listState = rememberLazyListState()
    val itemFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf<Stream?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }

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
    LaunchedEffect(focused) {
        epg = emptyList()
        val f = focused ?: return@LaunchedEffect
        delay(350)
        epg = runCatching { repo.shortEpg(playlist, f) }.getOrDefault(emptyList())
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(190.dp).fillMaxHeight()) {
            ScreenTitle("Canlı TV")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(categories) { i, c ->
                    CategoryItem(
                        name = c.name,
                        count = if (c.id == ALL) content.items.size else counts[c.id],
                        selected = c.id == selected,
                        onClick = { selected = c.id },
                        modifier = if (i == 0) Modifier.focusRequester(categoryFocus) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Spacer(Modifier.height(4.dp))
            SectionTitle(categoryName, "${items.size} kanal")
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Tv, "Bu kategoride kanal yok")
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                ) {
                    itemsIndexed(items) { i, s ->
                        ChannelRow(
                            number = i + 1,
                            stream = s,
                            favorite = favoriteKey(playlist, s) in actions.favorites,
                            onClick = { actions.open(items, i) },
                            onLongClick = { actions.toggleFavorite(s) },
                            onFocused = { focused = s },
                            modifier = if (i == restoreIndex) Modifier.focusRequester(itemFocus) else Modifier,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        ChannelPreview(focused, epg, Modifier.width(260.dp).fillMaxHeight().padding(bottom = 24.dp))
    }
}

@Composable
private fun ChannelPreview(stream: Stream?, epg: List<EpgEntry>, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(16.dp)) {
        if (stream == null) {
            EmptyState(Icons.Rounded.LiveTv, "Bir kanal seçin", "Program bilgisi burada görünür")
        } else {
            LogoTile(stream.name, stream.logo, Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Pill("CANLI", color = Palette.Live, contentColor = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                stream.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            val now = epg.firstOrNull()
            if (now == null) {
                Text("Program bilgisi yok", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
            } else {
                ProgramBlock("ŞİMDİ", now, showProgress = true)
                epg.getOrNull(1)?.let {
                    Spacer(Modifier.height(16.dp))
                    ProgramBlock("SONRA", it, showProgress = false)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("OK  izle   ·   Basılı tut  favori", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
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
