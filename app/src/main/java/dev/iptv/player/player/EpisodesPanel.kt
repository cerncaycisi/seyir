package dev.iptv.player.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.WATCHED_FRACTION
import dev.iptv.player.ui.pointerClick
import kotlinx.coroutines.android.awaitFrame

internal fun seasonNumbersOf(items: List<Stream>): List<Int> =
    items.map { it.season }.distinct().sorted()

internal fun episodesForSeason(items: List<Stream>, season: Int): List<IndexedValue<Stream>> =
    items.withIndex().filter { it.value.season == season }

internal fun formatEpisodePanelLabel(stream: Stream, fallbackNumber: Int): Pair<String, String> {
    val (_, subtitle) = splitPlayerTitle(stream.name)
    if (subtitle != null) {
        val parts = subtitle.split(" · ", limit = 2)
        val tag = parts[0].trim().ifEmpty { "B${stream.episode.takeIf { it > 0 } ?: fallbackNumber}" }
        val title = parts.getOrElse(1) { "" }.trim().ifEmpty { subtitle }
        return tag to title
    }
    return "B${stream.episode.takeIf { it > 0 } ?: fallbackNumber}" to stream.name
}

/** Right-side season & episode switcher opened from VOD series player controls. */
@Composable
internal fun EpisodesPanel(
    items: List<Stream>,
    currentIndex: Int,
    progressById: Map<String, Float>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val seasons = remember(items) { seasonNumbersOf(items) }
    var selectedSeason by remember { mutableIntStateOf(items.getOrNull(currentIndex)?.season ?: seasons.firstOrNull() ?: 1) }
    var prevSeason by remember { mutableIntStateOf(selectedSeason) }
    val seasonEpisodes = remember(items, selectedSeason) { episodesForSeason(items, selectedSeason) }
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val localIdx = seasonEpisodes.indexOfFirst { it.index == currentIndex }.coerceAtLeast(0)
        listState.scrollToItem((localIdx - 2).coerceAtLeast(0))
        awaitFrame()
        runCatching { initialFocus.requestFocus() }
    }
    LaunchedEffect(selectedSeason) {
        if (selectedSeason != prevSeason) {
            prevSeason = selectedSeason
            listState.scrollToItem(0)
        }
    }
    val panelBg = remember { Brush.horizontalGradient(listOf(Color(0xE607080A), Color(0xF507080A))) }
    Column(
        modifier
            .fillMaxHeight()
            .width(440.dp)
            .background(panelBg)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(stringResource(R.string.vod_episodes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.ui_episode_count, seasonEpisodes.size), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        if (seasons.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(seasons, key = { it }) { season ->
                    val selected = season == selectedSeason
                    Surface(
                        onClick = { selectedSeason = season },
                        modifier = Modifier.height(36.dp).pointerClick({ selectedSeason = season }),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) Amber else Color(0x24F5F5F7),
                            contentColor = if (selected) Color.Black else Ink,
                            focusedContainerColor = Ink,
                            focusedContentColor = Color.Black,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.ui_season, season), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val hasCurrent = remember(seasonEpisodes, currentIndex) { seasonEpisodes.any { it.index == currentIndex } }
        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(seasonEpisodes, key = { _, iv -> "${iv.index}|${iv.value.id}" }) { idx, iv ->
                val attachFocus = (iv.index == currentIndex) || (!hasCurrent && idx == 0)
                val (tag, title) = formatEpisodePanelLabel(iv.value, idx + 1)
                EpisodePanelRow(
                    tag = tag,
                    title = title,
                    playing = iv.index == currentIndex,
                    progress = progressById[iv.value.id],
                    onClick = { onPick(iv.index) },
                    modifier = if (attachFocus) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun EpisodePanelRow(
    tag: String,
    title: String,
    playing: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerClick(onClick)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (playing) Color(0x24F2B544) else Color(0x14F5F5F7),
            contentColor = Ink,
            focusedContainerColor = Ink,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            playing -> Amber
                            focused -> Color(0x1F000000)
                            else -> Color(0x26F5F5F7)
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (playing || focused) Color.Black else InkMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (playing || focused) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (focused) Color.Black else Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playing) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.vod_now_playing),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (focused) Color(0xFF9E6A00) else Amber,
                        maxLines = 1,
                    )
                } else if (progress != null && progress > 0.01f && progress < WATCHED_FRACTION) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (focused) Color(0x26000000) else Color(0x33F5F5F7)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(if (focused) Color.Black else Amber),
                        )
                    }
                }
            }
            if (!playing && progress != null && progress >= WATCHED_FRACTION) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.ui_watched),
                    tint = if (focused) Color.Black else Palette.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
