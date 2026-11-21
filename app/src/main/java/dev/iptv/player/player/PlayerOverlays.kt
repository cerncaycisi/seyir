package dev.iptv.player.player

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.LogoTile
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.Pill
import dev.iptv.player.ui.ProgressBar
import dev.iptv.player.ui.formatClock
import dev.iptv.player.ui.pointerClick
import kotlinx.coroutines.android.awaitFrame

@Composable
internal fun LiveOverlay(number: Int, stream: Stream, epg: List<EpgEntry>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile(stream.name, stream.logo, Modifier.size(width = 112.dp, height = 64.dp), MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("$number", color = Color(0x33FFFFFF), contentColor = Color.White)
                Text(stream.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Pill(stringResource(R.string.play_live), color = Palette.Live, contentColor = Color.White)
            }
            val now = epg.firstOrNull()
            if (now != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(now.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    Text("${formatClock(now.start)} – ${formatClock(now.end)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
                if (now.end > now.start) {
                    Spacer(Modifier.height(10.dp))
                    val nowSec = System.currentTimeMillis() / 1000
                    ProgressBar((nowSec - now.start).toFloat() / (now.end - now.start), Modifier.widthIn(max = 480.dp))
                }
                epg.getOrNull(1)?.let { next ->
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.play_next_programme, formatClock(next.start), next.title), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun VodOverlay(title: String, position: Long, duration: Long, playing: Boolean) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    Spacer(Modifier.height(20.dp))
    ProgressBar(if (duration > 0) position.toFloat() / duration else 0f)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${time(position)} / ${time(duration)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.weight(1f))
        if (duration > 0) Text(stringResource(R.string.ux_remaining, time((duration - position).coerceAtLeast(0))), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
    }
}

/** Countdown card shown near the end of a series episode; stringResource(R.string.play_now) is focused, BACK cancels. */
@Composable
internal fun NextEpisodeCard(nextTitle: String, seconds: Int, onPlayNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onCancel() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    Column(
        modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF2111620)).padding(20.dp),
    ) {
        Text(stringResource(R.string.play_next_countdown, seconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(nextTitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardButton(stringResource(R.string.play_now), onPlayNow, Modifier.focusRequester(focus))
            CardButton(stringResource(R.string.play_cancel), onCancel)
        }
    }
}

@Composable
private fun CardButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.SurfaceHigh,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.padding(horizontal = 20.dp).height(44.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

internal fun describe(context: Context, e: PlaybackException): String {
    val reason = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> context.getString(R.string.play_error_server)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> context.getString(R.string.play_error_connect)
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> context.getString(R.string.play_error_missing)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> context.getString(R.string.play_error_parse)
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> context.getString(R.string.play_error_format)
        else -> context.getString(R.string.play_error_unknown)
    }
    return "$reason\n${e.errorCodeName}"
}

internal fun time(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
