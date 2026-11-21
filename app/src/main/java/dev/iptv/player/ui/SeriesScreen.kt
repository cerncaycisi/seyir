package dev.iptv.player.ui

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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

@Composable
fun SeriesScreen(repo: Repository, playlist: Playlist, seriesId: String, onPlay: (List<Stream>, Int) -> Unit) {
    val state by produceState<Load<SeriesInfo>>(Load.Loading, seriesId) {
        value = loadCatching { repo.seriesInfo(playlist, seriesId) }
    }
    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Err -> ErrorState(s.message)
        is Load.Ok -> SeriesDetail(s.value, onPlay)
    }
}

@Composable
private fun SeriesDetail(info: SeriesInfo, onPlay: (List<Stream>, Int) -> Unit) {
    val seasons = remember(info) { info.episodes.groupBy { it.season }.toSortedMap() }
    var season by rememberSaveable { mutableIntStateOf(seasons.keys.firstOrNull() ?: 1) }
    var lastPlayed by rememberSaveable { mutableIntStateOf(0) }
    val episodes = remember(info, season) {
        seasons[season].orEmpty().map { e ->
            Stream(e.id, "S${season}B${e.number} · ${e.title}", info.cover, e.url, "", ContentType.SERIES)
        }
    }
    val episodeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { episodeFocus.requestFocus() }
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

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Text(
                info.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    info.year,
                    info.rating?.let { "★ $it" },
                    info.genre,
                    "${seasons.size} sezon",
                    "${info.episodes.size} bölüm",
                ).forEach { Pill(it) }
            }
            info.plot?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 16.dp).widthIn(max = 560.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            if (seasons.isEmpty()) {
                EmptyState(Icons.Rounded.VideoLibrary, "Bu dizide bölüm bulunamadı")
                return@Column
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(seasons.keys.toList()) { n -> SeasonChip("Sezon $n", n == season) { season = n } }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(episodes) { i, e ->
                    EpisodeRow(
                        number = seasons[season]?.getOrNull(i)?.number ?: (i + 1),
                        title = seasons[season]?.getOrNull(i)?.title ?: e.name,
                        onClick = {
                            lastPlayed = i
                            onPlay(episodes, i)
                        },
                        modifier = if (i == lastPlayed.coerceAtMost(episodes.lastIndex)) Modifier.focusRequester(episodeFocus) else Modifier,
                    )
                }
            }
        }
    }
}

// One composable for both states so selecting a season does not drop focus.
@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
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
private fun EpisodeRow(number: Int, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { focused = it.isFocused || it.hasFocus },
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
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
