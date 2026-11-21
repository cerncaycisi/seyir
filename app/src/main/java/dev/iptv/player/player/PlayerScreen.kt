package dev.iptv.player.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.PlayerQueue
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.LogoTile
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.Pill
import dev.iptv.player.ui.ProgressBar
import dev.iptv.player.ui.formatClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Icon
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Repository
import dev.iptv.player.data.favoriteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(repo: Repository, onBack: () -> Unit) {
    val items = PlayerQueue.items
    val playlist = PlayerQueue.playlist
    if (items.isEmpty() || playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(PlayerQueue.index.coerceIn(0, items.lastIndex)) }
    val current = items[index]
    val live = current.type == ContentType.LIVE

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVPlayer/0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply { playWhenReady = true }
    }

    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    fun showOverlay() { overlayTick++ }
    // Reads `index` (state-backed) instead of `current` so callbacks registered once never save a stale item.
    fun saveProgress() {
        val item = items[index]
        if (item.type == ContentType.LIVE) return
        val key = favoriteKey(playlist, item)
        val pos = player.currentPosition
        val duration = player.duration
        val tail = minOf(60_000L, duration / 20)
        val ended = player.playbackState == Player.STATE_ENDED || (duration > 0 && pos > duration - tail)
        repo.scope.launch { repo.store.savePosition(key, if (ended) 0 else pos) }
        repo.history.record(playlist, item, pos, duration)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                } else {
                    error = describe(e)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED && items[index].type != ContentType.LIVE) {
                    saveProgress()
                    if (index < items.lastIndex) index++
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { saveProgress(); player.pause() }
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(index) {
        error = null
        epg = emptyList()
        val start = if (live) 0L else repo.store.position(favoriteKey(playlist, current))
        player.setMediaItem(MediaItem.fromUri(current.url), start)
        player.prepare()
        player.play()
        showOverlay()
        if (live) repo.history.record(playlist, current)
        if (live) epg = runCatching { repo.shortEpg(playlist, current) }.getOrDefault(emptyList())
    }

    LaunchedEffect(overlayTick) {
        overlay = true
        delay(5_000)
        overlay = false
    }

    LaunchedEffect(overlay) {
        while (overlay) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    BackHandler {
        saveProgress()
        onBack()
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val handled = if (live) {
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> { index = (index + 1) % items.size; true }
                        Key.DirectionDown, Key.ChannelDown -> { index = (index - 1 + items.size) % items.size; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (overlay) overlay = false else showOverlay(); true }
                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                            if (player.isPlaying) player.pause() else player.play()
                            showOverlay(); true
                        }
                        Key.DirectionLeft, Key.MediaRewind -> { player.seekBack(); showOverlay(); true }
                        Key.DirectionRight, Key.MediaFastForward -> { player.seekForward(); showOverlay(); true }
                        Key.DirectionUp, Key.DirectionDown -> { showOverlay(); true }
                        else -> false
                    }
                }
                handled
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        error?.let {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6111620)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Live, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text("Yayın açılamadı", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(it.substringBefore('\n'), style = MaterialTheme.typography.titleMedium, color = Palette.TextMuted)
                it.substringAfter('\n', "").takeIf { c -> c.isNotEmpty() }?.let { c ->
                    Text(c, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 4.dp))
                }
                if (live) {
                    Spacer(Modifier.height(16.dp))
                    Text("Kanal değiştirmek için ▲ ▼", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
            }
        }

        if (overlay) {
            Text(
                formatClock(System.currentTimeMillis() / 1000),
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 40.dp, vertical = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))))
                    .padding(start = 40.dp, end = 40.dp, top = 56.dp, bottom = 28.dp),
            ) {
                if (live) LiveOverlay(index + 1, current, epg) else VodOverlay(current.name, position, duration, playing)
            }
        }
    }
}

@Composable
private fun LiveOverlay(number: Int, stream: Stream, epg: List<EpgEntry>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile(stream.name, stream.logo, Modifier.size(width = 112.dp, height = 64.dp), MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("$number", color = Color(0x33FFFFFF), contentColor = Color.White)
                Text(stream.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Pill("CANLI", color = Palette.Live, contentColor = Color.White)
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
                    Text("Sonra  ${formatClock(next.start)}  ${next.title}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("▲ ▼  kanal değiştir    ·    OK  bilgi    ·    Geri  çıkış", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
}

@Composable
private fun VodOverlay(title: String, position: Long, duration: Long, playing: Boolean) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    Spacer(Modifier.height(20.dp))
    ProgressBar(if (duration > 0) position.toFloat() / duration else 0f)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black)
        }
        Spacer(Modifier.width(16.dp))
        Text("${time(position)} / ${time(duration)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.weight(1f))
        Text("◀ ▶  10 sn    ·    OK  oynat / duraklat", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
    }
}

private fun describe(e: PlaybackException): String {
    val reason = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sunucu yayını vermedi"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sunucuya bağlanılamadı"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Yayın bulunamadı"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Yayın verisi okunamadı"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Bu yayın biçimi cihazda desteklenmiyor"
        else -> "Beklenmeyen bir hata oluştu"
    }
    return "$reason\n${e.errorCodeName}"
}


private fun time(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
