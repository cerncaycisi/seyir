package dev.iptv.player.player

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.PlayerQueue
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.ui.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 2 (1x2) or 4 (2x2) live channels at once. Only the focused tile has sound; D-pad moves between tiles,
 * OK replaces the tile's channel via the channel list, BACK leaves and releases every player.
 * Channels come from the current [PlayerQueue] (live items only).
 */
@Composable
fun MultiViewScreen(repo: Repository, requested: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    if (PlayerQueue.items.isEmpty()) PlayerQueue.restore(context)
    val cfg by produceState<PlayerConfig?>(null) { value = PlayerConfig.load(repo.settings) }
    val channels = remember { PlayerQueue.items.filter { it.type == ContentType.LIVE && it.url.isNotEmpty() } }
    val c = cfg
    if (channels.size < 2) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    if (c == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val currentId = PlayerQueue.items.getOrNull(PlayerQueue.index)?.id
    val start = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    MultiViewContent(c, channels, start, MultiViewLayout.tileCount(requested), onBack)
}

@OptIn(UnstableApi::class)
@Composable
private fun MultiViewContent(cfg: PlayerConfig, channels: List<Stream>, start: Int, requested: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count = minOf(requested, channels.size)
    val slots = remember { mutableStateListOf<Int>().apply { addAll(MultiViewLayout.initialChannels(start, channels.size, requested)) } }
    var focused by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(true) }
    val errors = remember { mutableStateListOf<String?>().apply { repeat(count) { add(null) } } }

    val headers = remember { List(count) { RequestHeaders() } }
    val retries = remember { IntArray(count) }
    val degraded = remember { BooleanArray(count) }
    val players = remember {
        // Small buffers and a size cap per tile keep memory and hardware decoder load down.
        val tileCfg = cfg.copy(bufferSeconds = 8, matchFrameRate = false)
        List(count) { i ->
            buildPlayer(context, tileCfg, headers[i]).apply {
                volume = MultiViewLayout.volume(i, 0)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(MultiViewLayout.maxVideoWidth(count), MultiViewLayout.maxVideoHeight(count))
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
            }
        }
    }

    DisposableEffect(Unit) {
        val listeners = players.mapIndexed { i, p ->
            val l = object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    val decoder = e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
                    if (decoder && !degraded[i]) {
                        // Out of hardware decoders: retry once at the lowest quality.
                        degraded[i] = true
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(426, 240).build()
                        p.prepare()
                    } else if (decoder) {
                        errors[i] = context.getString(R.string.play_decoder_limit)
                    } else if (retries[i] < 3) {
                        retries[i]++
                        scope.launch { delay(3_000); p.prepare() }
                    } else {
                        errors[i] = context.getString(R.string.play_no_stream)
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        errors[i] = null
                        retries[i] = 0
                    }
                }
            }
            p.addListener(l)
            l
        }
        onDispose {
            players.forEachIndexed { i, p ->
                p.removeListener(listeners[i])
                p.release()
            }
        }
    }

    // (Re)load a tile whenever its channel changes.
    for (i in 0 until count) {
        val ch = channels[slots[i]]
        LaunchedEffect(i, ch.id) {
            errors[i] = null
            retries[i] = 0
            degraded[i] = false
            headers[i].value = ch.headers
            val p = players[i]
            p.setMediaItem(MediaItem.fromUri(dev.iptv.player.data.applyLiveStreamFormat(ch.url, cfg.liveStreamFormat)))
            p.prepare()
            p.play()
        }
    }
    LaunchedEffect(focused) { players.forEachIndexed { i, p -> p.volume = MultiViewLayout.volume(i, focused) } }
    LaunchedEffect(Unit) {
        delay(8_000)
        showWarning = false
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> players.forEach { it.pause() }
                Lifecycle.Event.ON_START -> players.forEach { it.play() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler { onBack() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(picking) { if (!picking) runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (picking || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { focused = MultiViewLayout.neighbor(focused, -1, 0, count); true }
                    Key.DirectionRight -> { focused = MultiViewLayout.neighbor(focused, 1, 0, count); true }
                    Key.DirectionUp -> { focused = MultiViewLayout.neighbor(focused, 0, -1, count); true }
                    Key.DirectionDown -> { focused = MultiViewLayout.neighbor(focused, 0, 1, count); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { picking = true; true }
                    else -> false
                }
            },
    ) {
        val cols = MultiViewLayout.columns(count)
        // Reserve a footer so the remote-control hint cannot cover the lower channel labels.
        Column(Modifier.fillMaxSize().padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 36.dp)) {
            for (r in 0 until MultiViewLayout.rows(count)) {
                Row(Modifier.weight(1f).fillMaxSize()) {
                    for (cIdx in 0 until cols) {
                        val i = r * cols + cIdx
                        // Touch: tap a tile to hear it, tap it again (or long-press) to change its channel.
                        val tileTouch = if (i < count) Modifier.pointerInput(i) {
                            detectTapGestures(
                                onTap = { if (picking) picking = false else if (focused == i) picking = true else focused = i },
                                onLongPress = { if (!picking) { focused = i; picking = true } },
                            )
                        } else Modifier
                        Box(Modifier.weight(1f).fillMaxSize().padding(3.dp).then(tileTouch)) {
                            if (i < count) Tile(players[i], channels[slots[i]].name, i == focused, errors[i])
                        }
                    }
                }
            }
        }

        if (showWarning) {
            Text(
                context.getString(R.string.play_multi_warning),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE6111620)).padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Gold,
            )
        } else {
            Text(
                context.getString(R.string.play_multi_controls),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
            )
        }

        if (picking) {
            ChannelListPanel(
                items = channels,
                currentIndex = slots[focused],
                playlistId = PlayerQueue.playlist?.id.orEmpty(),
                onPick = { slots[focused] = it; picking = false },
                onClose = { picking = false },
                modifier = Modifier.align(Alignment.CenterStart).swallowTaps(),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Tile(player: ExoPlayer, name: String, focused: Boolean, error: String?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xFF05070A))
            .border(BorderStroke(if (focused) 4.dp else 1.dp, if (focused) Palette.Primary else Color(0x33FFFFFF)), shape),
    ) {
        AndroidView(
            factory = { ctx ->
                createPlayerView(ctx).apply {
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            (if (focused) "♪ " else "") + name,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (error != null) {
            Text(
                error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6111620)).padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
        }
    }
}
