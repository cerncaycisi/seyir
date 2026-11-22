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
