package dev.iptv.player.player

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.ChannelRow
import dev.iptv.player.ui.Palette
import kotlinx.coroutines.android.awaitFrame

/** Left-hand channel list for zapping without leaving playback. Focus starts on the playing channel. */
@Composable
fun ChannelListPanel(
    items: List<Stream>,
    currentIndex: Int,
    playlistId: String,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val state = rememberLazyListState()
    val epg = dev.iptv.player.ui.rememberEpg()
    val nowSec by dev.iptv.player.ui.rememberNowSec(60_000)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    val panelBg = remember { Brush.horizontalGradient(listOf(Color(0xF507080A), Color(0xE607080A))) }
    Column(
        modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(panelBg)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(stringResource(R.string.play_channels), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.play_channels_hint, items.size), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { i, s -> "$i|${s.id}" }) { i, stream ->
                // What is on now, like the Live TV list (IBO shows it here too).
                @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue") // lint misses the assignment below
                val nowProg by androidx.compose.runtime.produceState<dev.iptv.player.epg.Programme?>(null, stream.id, nowSec / 60) {
                    value = runCatching { epg.nowPlaying(playlistId, stream, nowSec) }.getOrNull()
                }
                ChannelRow(
                    number = i + 1,
                    stream = stream,
                    favorite = false,
                    onClick = { onPick(i) },
                    onLongClick = {},
                    onFocused = {},
                    nowTitle = nowProg?.title,
                    progress = nowProg?.let { (nowSec - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                    modifier = if (i == currentIndex) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}
