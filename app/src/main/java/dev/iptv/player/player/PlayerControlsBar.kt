package dev.iptv.player.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import dev.iptv.player.R
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.pointerClick

private data class QuickControl(val label: String, val icon: ImageVector, val action: () -> Unit)

/** Visible, labelled controls for both the remote and pointer; media keys still work outside this row. */
@Composable
internal fun PlayerControlsBar(
    live: Boolean,
    playing: Boolean,
    active: Boolean,
    onToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onChannels: () -> Unit,
    onSettings: (PlayerPage) -> Unit,
    onRecord: () -> Unit,
    recording: Boolean,
    canReturnToLive: Boolean,
    onReturnToLive: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val buttons = buildList {
        if (!live) add(QuickControl(stringResource(R.string.ux_back_ten), Icons.Rounded.Replay10, onSeekBack))
        add(QuickControl(stringResource(if (playing) R.string.ux_pause else R.string.ux_play),
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, onToggle))
        if (!live) add(QuickControl(stringResource(R.string.ux_forward_ten), Icons.Rounded.Forward10, onSeekForward))
        if (live) add(QuickControl(stringResource(R.string.play_channel_list), Icons.AutoMirrored.Rounded.List, onChannels))
        add(QuickControl(stringResource(R.string.play_audio), Icons.AutoMirrored.Rounded.VolumeUp) { onSettings(PlayerPage.Audio) })
        add(QuickControl(stringResource(R.string.play_subtitles), Icons.Rounded.Subtitles) { onSettings(PlayerPage.Subtitle) })
        add(QuickControl(stringResource(R.string.play_aspect), Icons.Rounded.AspectRatio) { onSettings(PlayerPage.Aspect) })
        if (live) add(QuickControl(stringResource(if (recording) R.string.play_stop_recording else R.string.play_record),
            if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, onRecord))
        if (canReturnToLive) add(QuickControl(stringResource(R.string.play_back_live), Icons.Rounded.LiveTv, onReturnToLive))
        add(QuickControl(stringResource(R.string.ux_more), Icons.Rounded.MoreHoriz) { onSettings(PlayerPage.Main) })
    }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
        itemsIndexed(buttons) { i, button ->
            val primary = i == if (live) 0 else 1
            Surface(
                onClick = button.action,
                modifier = Modifier.height(44.dp).then(if (primary) Modifier.focusRequester(first) else Modifier)
                    .pointerClick(button.action),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (primary) Color.White else Color(0xDD202939),
                    contentColor = if (primary) Color.Black else Color.White,
                    focusedContainerColor = Palette.Primary,
                    focusedContentColor = Color.White,
                ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(button.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(button.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
