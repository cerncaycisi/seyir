package dev.iptv.player.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.player.PlayerConfig
import dev.iptv.player.player.RequestHeaders
import dev.iptv.player.player.buildPlayer
import dev.iptv.player.player.createPlayerView
import dev.iptv.player.player.describe

/** One small ExoPlayer shared by the live browser; follows the focused channel and is released with the screen. */
class LivePreview(private val context: Context, private val config: PlayerConfig) {
    private val headers = RequestHeaders()
    private var released = false
    val player: ExoPlayer = buildPlayer(context, config, headers)
    var channel by mutableStateOf<Stream?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) { ready = true; error = null }
            }
            override fun onPlayerError(e: PlaybackException) {
                ready = false
                error = describe(context, e).substringBefore('\n')
            }
        })
    }

    fun play(stream: Stream) {
        if (released || stream.url.isEmpty() || channel?.id == stream.id) return
        channel = stream
        ready = false
        error = null
        headers.value = stream.headers
        player.setMediaItem(MediaItem.Builder().setUri(stream.url).setMediaMetadata(MediaMetadata.Builder().setTitle(stream.name).build()).build())
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        if (released) return
        released = true
        player.release()
    }
}

/** Builds the preview player once the player settings are loaded; null until then. Released on leaving composition. */
@Composable
fun rememberLivePreview(repo: Repository): LivePreview? {
    val context = LocalContext.current
    val cfg by produceState<PlayerConfig?>(null) { value = PlayerConfig.load(repo.settings) }
    val preview = remember(cfg) { cfg?.let { LivePreview(context, it) } }
    DisposableEffect(preview) { onDispose { preview?.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, preview) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> runCatching { preview?.player?.pause() }
                Lifecycle.Event.ON_START -> runCatching { if (preview?.channel != null) preview.player.play() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return preview
}

/** 16:9 video of [preview]; the channel logo shows while buffering and an error text on failure. */
@Composable
fun LivePreviewVideo(preview: LivePreview?, stream: Stream, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier) {
        if (preview != null) {
            AndroidView(
                factory = { ctx -> createPlayerView(ctx).apply { player = preview.player } },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val playingThis = preview?.channel?.id == stream.id
        if (preview == null || !playingThis || !preview.ready) {
            LogoTile(stream.name, stream.logo, Modifier.fillMaxSize(), MaterialTheme.typography.headlineMedium)
        }
        val err = if (playingThis) preview?.error else null
        if (err != null) {
            Column(Modifier.fillMaxSize().background0().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Live, modifier = Modifier.size(24.dp))
                Text(context.getString(R.string.play_no_stream), style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
                Text(err, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Modifier.background0(): Modifier = this.then(Modifier.background(Color(0xCC080B10)))
