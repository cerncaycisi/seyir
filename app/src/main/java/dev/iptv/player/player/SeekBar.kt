package dev.iptv.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.ProgressBar

@Composable
internal fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, onInteraction: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    val shown = target ?: position
    val label = stringResource(R.string.ux_seek)
    Column(Modifier.fillMaxWidth()
        .onFocusChanged { focused = it.isFocused; if (!it.isFocused) target = null }
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) {
                        target = seekPosition(target ?: position, if (event.key == Key.DirectionRight) 10_000 else -10_000, duration)
                        onInteraction()
                    }
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyUp) { target?.let(onSeek); target = null; onInteraction() }
                    true
                }
                Key.Back -> if (target != null) { if (event.type == KeyEventType.KeyUp) target = null; true } else false
                else -> false
            }
        }
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..duration.coerceAtLeast(1).toFloat())
            setProgress { onSeek(it.toLong().coerceIn(0, duration.coerceAtLeast(0))); true }
        }
        .focusable(enabled = duration > 0)) {
        Box(Modifier.fillMaxWidth().height(28.dp)
            .border(if (focused) 2.dp else 0.dp, if (focused) Palette.Primary else Color.Transparent, RoundedCornerShape(8.dp))
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0 && size.width > 0) { onSeek((offset.x / size.width * duration).toLong().coerceIn(0, duration)); onInteraction() }
                }
            }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            ProgressBar(if (duration > 0) shown.toFloat() / duration else 0f)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${time(shown)} / ${time(duration)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(if (focused) stringResource(R.string.ux_seek_hint) else stringResource(R.string.ux_remaining, time((duration - position).coerceAtLeast(0))),
                style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
    }
}
