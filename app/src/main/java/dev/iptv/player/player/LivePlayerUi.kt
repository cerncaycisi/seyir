package dev.iptv.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.LogoTile
import dev.iptv.player.ui.formatClock

private val LiveRed = Color(0xFFE5352B)

/**
 * Canlı yayın kontrolleri (tasarım v2 dili, canlıya özgü içerik): kanal kimliği, şu anki programın ilerlemesi
 * ve "sonra", kanal listesi / kayıt / ses-altyazı / diğer. Zaman çubuğu yok; ↑↓ kanal değiştirir.
 */
@Composable
internal fun LiveControls(
    number: Int,
    stream: Stream,
    epg: List<EpgEntry>,
    playing: Boolean,
    active: Boolean,
    behindLabel: String?,
    recording: Boolean,
    sleepLabel: String?,
    onToggle: () -> Unit,
    onChannels: () -> Unit,
    onRecord: () -> Unit,
    onTracks: (() -> Unit)?,
    onMore: () -> Unit,
    onReturnToLive: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focusTarget: PlayerControlFocus = PlayerControlFocus.PlayPause,
    favorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    val channelsFocus = remember { FocusRequester() }
    val tracksFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    LaunchedEffect(active, focusTarget) {
        if (active) {
            withFrameNanos { }
            val target = when (focusTarget) {
                PlayerControlFocus.PlayPause, PlayerControlFocus.Episodes -> first
                PlayerControlFocus.Channels -> channelsFocus
                PlayerControlFocus.Tracks -> if (onTracks != null) tracksFocus else first
                PlayerControlFocus.More -> moreFocus
            }
            runCatching { target.requestFocus() }
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
        Row(Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoTile(stream.name, stream.logo, Modifier.size(width = 72.dp, height = 44.dp), MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = InkMuted)
                    Spacer(Modifier.width(10.dp))
                    Text(stream.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 520.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.play_live).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = Color.White, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LiveRed).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                sleepLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = InkMuted) }
            }
        }
        Text(formatClock(System.currentTimeMillis() / 1000), modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 40.dp, vertical = 28.dp),
            style = MaterialTheme.typography.titleLarge, color = Ink)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xC4000000))))
                .padding(start = 40.dp, end = 40.dp, top = 70.dp, bottom = 26.dp),
        ) {
            val now = epg.firstOrNull()
            if (now != null) {
                Text(now.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatClock(now.start)} – ${formatClock(now.end)}", style = MaterialTheme.typography.labelLarge, color = InkMuted)
                    epg.getOrNull(1)?.let { next ->
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.play_next_programme, formatClock(next.start), next.title), style = MaterialTheme.typography.labelLarge,
                            color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (now.end > now.start) {
                    Spacer(Modifier.height(10.dp))
                    val nowSec = System.currentTimeMillis() / 1000
                    val frac = ((nowSec - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40F5F5F7))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Amber))
                    }
                }
            } else {
                Text(stringResource(R.string.ui_no_programme_info), style = MaterialTheme.typography.titleMedium, color = InkMuted)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPauseButton(playing, onToggle, Modifier.focusRequester(first))
                Spacer(Modifier.width(12.dp))
                behindLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.Black,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Amber).padding(horizontal = 8.dp, vertical = 4.dp))
                    Spacer(Modifier.width(10.dp))
                }
                onReturnToLive?.let {
                    PillButton(stringResource(R.string.play_back_live), Icons.Rounded.LiveTv, it)
                    Spacer(Modifier.width(10.dp))
                }
                PillButton(stringResource(R.string.play_channel_list), Icons.AutoMirrored.Rounded.List, onChannels, Modifier.focusRequester(channelsFocus))
                Spacer(Modifier.width(10.dp))
                PillButton(
                    stringResource(if (recording) R.string.play_stop_recording else R.string.play_record),
                    if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                    onRecord,
                )
                onFavorite?.let {
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        stringResource(if (favorite) R.string.play_favorite_remove else R.string.play_favorite_add),
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        it,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(if (active) R.string.live_controls_active_hint else R.string.live_controls_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0x80F5F5F7),
                )
                Spacer(Modifier.width(16.dp))
                onTracks?.let {
                    RoundIconButton(Icons.Rounded.Subtitles, stringResource(R.string.vod_audio_subtitles), it, Modifier.focusRequester(tracksFocus))
                    Spacer(Modifier.width(10.dp))
                }
                RoundIconButton(Icons.Rounded.MoreHoriz, stringResource(R.string.ux_more), onMore, Modifier.focusRequester(moreFocus))
            }
        }
    }
}
