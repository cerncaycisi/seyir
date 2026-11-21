package dev.iptv.player.player

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val state = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Color(0xF2111620))
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text("Kanallar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${items.size} kanal  ·  OK  izle  ·  Geri  kapat", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { i, s -> "$i|${s.id}" }) { i, stream ->
                ChannelRow(
                    number = i + 1,
                    stream = stream,
                    favorite = false,
                    onClick = { onPick(i) },
                    onLongClick = {},
                    onFocused = {},
                    modifier = if (i == currentIndex) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}
