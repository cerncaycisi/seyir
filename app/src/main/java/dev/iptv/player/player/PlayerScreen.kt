package dev.iptv.player.player

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.PlayerQueue
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.EpgEntry
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Setting
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import dev.iptv.player.data.movieDetails
import dev.iptv.player.data.seriesDetails
import dev.iptv.player.ui.DescriptionDialog
import dev.iptv.player.epg.Catchup
import dev.iptv.player.record.RecStatus
import dev.iptv.player.record.RecordingDb
import dev.iptv.player.record.RecordingManager
import dev.iptv.player.ui.Palette
import dev.iptv.player.ui.Pill
import dev.iptv.player.ui.formatClock
import dev.iptv.player.ui.RecordingDurationDialog
import dev.iptv.player.ui.PointerButton
import dev.iptv.player.ui.pointerClick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Panel { None, Settings, Channels, Tracks, Episodes }

private const val MAX_RECONNECTS = 10
private const val NEXT_EPISODE_WINDOW_MS = 10_000L

private fun requestHeadersFor(stream: Stream): Map<String, String> = stream.headers

private data class RecordingTarget(val stream: Stream, val title: String, val programmeEndSec: Long?)

private class ViewHolder { var view: PlayerView? = null }

@Composable
fun PlayerScreen(repo: Repository, onBack: () -> Unit, onMultiView: (Int) -> Unit = {}) {
    // Process death: the in-memory queue is gone, rebuild it from disk (saved by PlayerQueue.save).
    if (PlayerQueue.items.isEmpty()) PlayerQueue.restore(LocalContext.current)
    val items = PlayerQueue.items
    val playlist = PlayerQueue.playlist
    if (items.isEmpty() || playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val cfg by produceState<PlayerConfig?>(null) { value = PlayerConfig.load(repo.settings) }
    // 0 = deciding, 1 = handed to an external player, 2 = play internally
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val c = cfg
    if (c == null || mode != 2) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        if (c != null && mode == 0) {
            LaunchedEffect(Unit) {
                val stream = items[PlayerQueue.index.coerceIn(0, items.lastIndex)]
                if (launchExternalPlayer(context, c.externalPlayer, stream)) {
                    mode = 1
                    onBack()
                } else {
                    mode = 2
                }
            }
        }
        return
    }
    PlayerContent(repo, c, onBack, onMultiView)
}

/** Opens [stream] in the app named by the ExternalPlayer setting; false when unset or not installed. */
private fun launchExternalPlayer(context: Context, setting: String, stream: Stream): Boolean {
    // The Settings screen stores "", "vlc" or "mx"; a full package name is accepted too.
    val pkg = when (val v = setting.trim()) {
        "vlc" -> "org.videolan.vlc"
        "mx" -> "com.mxtech.videoplayer.ad"
        else -> v
    }
    if (pkg.isEmpty() || !pkg.contains('.')) return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(stream.url), "video/*")
        setPackage(pkg)
        putExtra("title", stream.name)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(repo: Repository, cfg: PlayerConfig, onBack: () -> Unit, onMultiView: (Int) -> Unit) {
    val items = PlayerQueue.items
    val playlist = PlayerQueue.playlist!!
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by rememberSaveable { mutableIntStateOf(PlayerQueue.index.coerceIn(0, items.lastIndex)) }
    var previousIndex by rememberSaveable { mutableIntStateOf(-1) }
    val current = items[index]
    val live = current.type == ContentType.LIVE

    val headers = remember { RequestHeaders() }
    val player = remember { buildPlayer(context, cfg, headers) }
    val viewHolder = remember { ViewHolder() }

    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playRequested by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var controlsActive by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(PlayerPage.Main) }
    var buffering by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    val playerFavorites by repo.store.favorites.collectAsState(initial = emptySet())
    var showInfo by remember { mutableStateOf(false) }
    var titleInfo by remember(current.id) { mutableStateOf<String?>(null) }
    var pauseCast by remember(current.id) { mutableStateOf<String?>(null) }
    var vodInfo by remember(current.id) { mutableStateOf<dev.iptv.player.data.VodInfo?>(null) }
    var titleInfoLoading by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id, playRequested, showInfo) {
        if (live || (playRequested && !showInfo) || titleInfo != null) return@LaunchedEffect
        titleInfoLoading = true
        try {
            titleInfo = try {
                if (current.type == ContentType.SERIES && current.seriesId != null) {
                    val info = repo.seriesDetails(playlist, current.seriesId)
                    pauseCast = info.cast
                    vodInfo = dev.iptv.player.data.VodInfo(plot = info.plot, backdrop = info.backdrop, genre = info.genre, duration = null, year = info.year, rating = info.rating, cast = info.cast, director = info.director)
                    listOfNotNull(if (info.plotLanguage == "en") context.getString(R.string.ux_plot_english) else null, info.plot, info.genre, info.cast?.let { context.getString(R.string.ui_cast) + ": " + it },
                        info.director?.let { context.getString(R.string.ui_director) + ": " + it }).joinToString("\n\n")
                } else {
                    val info = repo.movieDetails(playlist, current)
                    pauseCast = info.cast
                    vodInfo = info
                    listOfNotNull(if (info.plotLanguage == "en") context.getString(R.string.ux_plot_english) else null, info.plot, info.genre, info.cast?.let { context.getString(R.string.ui_cast) + ": " + it },
                        info.director?.let { context.getString(R.string.ui_director) + ": " + it }).joinToString("\n\n")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { "" }
        } finally { titleInfoLoading = false }
    }

    var panel by remember { mutableStateOf(Panel.None) }
    var controlFocus by remember { mutableStateOf(PlayerControlFocus.PlayPause) }
    var panelOpenedFromControls by remember { mutableStateOf(false) }
    LaunchedEffect(controlsActive, panel) {
        if (panel == Panel.None) {
            panelOpenedFromControls = false
            if (!controlsActive) controlFocus = PlayerControlFocus.PlayPause
        }
    }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var trackParams by remember { mutableStateOf<TrackSelectionParameters>(player.trackSelectionParameters) }
    // Default audio = the title's original language (setting "original"); a manual choice in the player wins.
    var audioAuto by remember(current.id) { mutableStateOf(true) }
    var originalLanguage by remember(current.id) { mutableStateOf<String?>(null) }
    // The pick waits for the TMDB answer (bounded) so a Turkish title never starts on its English dub first.
    var languageResolved by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id) {
        if (live || cfg.audioLanguage != dev.iptv.player.data.ORIGINAL_AUDIO) return@LaunchedEffect
        originalLanguage = try {
            kotlinx.coroutines.withTimeoutOrNull(4_000) {
                val series = current.seriesId
                if (series != null) {
                    val show = repo.content(playlist, ContentType.SERIES).items.firstOrNull { it.id == series }
                    val showTmdbId = show?.tmdbId ?: runCatching { repo.seriesInfo(playlist, series).tmdbId }.getOrNull()
                    repo.tmdb.originalLanguage(show?.name ?: current.name, ContentType.SERIES, show?.year, tmdbId = showTmdbId)
                } else {
                    val playedSecs = runCatching {
                        repo.history.entries.first().firstOrNull {
                            it.playlistId == playlist.id && it.type == ContentType.MOVIE && it.id == current.id
                        }?.durationMs?.takeIf { it > 0 }?.let { (it / 1000L).toInt() }
                    }.getOrNull()
                    val panel = runCatching { repo.vodInfo(playlist, current) }.getOrNull()
                    repo.tmdb.originalLanguage(
                        current.name,
                        ContentType.MOVIE,
                        panel?.year ?: current.year,
                        tmdbId = panel?.tmdbId ?: current.tmdbId,
                        durationSecs = playedSecs ?: panel?.durationSecs,
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        languageResolved = true
    }
    LaunchedEffect(tracks, originalLanguage, languageResolved, audioAuto) {
        if (live || !audioAuto || !languageResolved || cfg.audioLanguage != dev.iptv.player.data.ORIGINAL_AUDIO) return@LaunchedEffect
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        val pick = originalAudioPick(audio.map { (g, i) -> g.getTrackFormat(i).language }, originalLanguage) ?: return@LaunchedEffect
        val (g, i) = audio[pick]
        if (!g.isTrackSelected(i)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
    }
    // Default subtitle = the full track in the preferred language (not a forced one); a manual choice wins.
    var subtitleAuto by remember(current.id) { mutableStateOf(true) }
    LaunchedEffect(tracks, subtitleAuto) {
        val pref = cfg.subtitleLanguage
        if (live || !subtitleAuto || pref.isBlank() || pref.equals("off", true)) return@LaunchedEffect
        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        val pick = subtitlePick(text.map { (g, i) -> g.getTrackFormat(i).let { f -> f.language to (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) } }, pref)
            ?: return@LaunchedEffect
        val (g, i) = text[pick]
        if (!g.isTrackSelected(i)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
    }
    var aspect by rememberSaveable { mutableStateOf(cfg.aspectMode) }
    var subtitleScale by rememberSaveable { mutableIntStateOf(cfg.subtitleScale) }
    var subtitleStyleKey by rememberSaveable { mutableStateOf(cfg.subtitleStyle) }
    var speed by remember { mutableStateOf(1f) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    val externalSubs = remember { mutableStateListOf<SubtitleConfiguration>() }
    var nextSeconds by remember { mutableIntStateOf(0) }
    var nextCancelledFor by rememberSaveable { mutableIntStateOf(-1) }
    val nextVisible = nextSeconds > 0

    val aspectMode = AspectMode.fromKey(aspect)

    // Picture-in-Picture: while a live channel plays, HOME shrinks the player (MainActivity.onUserLeaveHint).
    val inPip by PipState.inPip.collectAsState()
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(live) {
        PipState.autoEnter = live
        onDispose { PipState.autoEnter = false }
    }
    LaunchedEffect(inPip) { if (inPip) { panel = Panel.None; controlsActive = false; overlay = false } }

    // Recording (DVR) state for the current channel.
    val recordings by remember { RecordingDb.get(context).dao().observeAll() }.collectAsState(initial = emptyList())
    val activeRecording = if (live) recordings.firstOrNull { it.status == RecStatus.RECORDING && it.playlistId == playlist.id && it.streamId == current.id } else null
    var recordingTarget by remember { mutableStateOf<RecordingTarget?>(null) }

    // Live pause / timeshift: catch-up when the playlist offers it, otherwise ExoPlayer's back buffer (see buildPlayer).
    var shiftStartSec by remember { mutableStateOf<Long?>(null) }
    var pausedAtMs by remember { mutableLongStateOf(0L) }
    var behindSec by remember { mutableLongStateOf(0L) }
    val holdPause = remember { BooleanArray(1) } // true while the pause comes from the lifecycle, not the user
    val canReturnToLive = live && (shiftStartSec != null || behindSec >= Timeshift.BEHIND_LIVE_SHOW_SEC)

    fun showOverlay() {
        overlay = true
        overlayTick++
    }
    fun openSettings(page: PlayerPage = PlayerPage.Main, fromControls: Boolean = false) {
        settingsPage = page
        panelOpenedFromControls = fromControls
        panel = Panel.Settings
    }
    fun openPanelFromControls(target: Panel, control: PlayerControlFocus, page: PlayerPage = PlayerPage.Main) {
        controlFocus = control
        panelOpenedFromControls = true
        if (target == Panel.Settings) settingsPage = page
        panel = target
    }
    fun closePanel() {
        val fromControls = panelOpenedFromControls
        panelOpenedFromControls = false
        panel = Panel.None
        if (fromControls) {
            controlsActive = true
            showOverlay()
        } else {
            overlay = false
        }
    }
    fun dismissPanel() {
        panel = Panel.None
        panelOpenedFromControls = false
        controlFocus = PlayerControlFocus.PlayPause
    }
    fun toast(message: String) = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    fun returnToLive() {
        pausedAtMs = 0
        if (shiftStartSec != null) {
            shiftStartSec = null
            player.setMediaItem(MediaItem.Builder().setUri(current.url).setMediaMetadata(MediaMetadata.Builder().setTitle(current.name).build()).build())
            player.prepare()
        } else {
            player.seekToDefaultPosition()
        }
        player.play()
        behindSec = 0
    }
    fun toggleRecording() {
        scope.launch {
            val running = activeRecording
            if (running != null) {
                RecordingManager.stop(context, running.id)
                toast(context.getString(R.string.play_stopping_recording))
            } else {
                val now = System.currentTimeMillis() / 1000
                val prog = epg.firstOrNull { it.start <= now && it.end > now }
                recordingTarget = RecordingTarget(current, prog?.title.orEmpty(), prog?.end)
            }
        }
    }
    fun zapTo(target: Int) {
        if (target == index) return
        previousIndex = index
        index = target
    }
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
        if (!item.id.startsWith("rec:")) repo.history.record(playlist, item, pos, duration)
    }

    fun buildItem(s: Stream): MediaItem = MediaItem.Builder()
        .setUri(s.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).build())
        .setSubtitleConfigurations(externalSubs.toList())
        .build()

    fun scheduleReconnect(cause: PlaybackException) {
        val attempt = reconnectAttempt
        if (attempt >= MAX_RECONNECTS) {
            reconnectAttempt = 0
            error = describe(context, cause)
            return
        }
        reconnectAttempt = attempt + 1
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(minOf(30_000L, 1_000L shl attempt))
            player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val liveNow = items[index].type == ContentType.LIVE
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                } else if (liveNow && cfg.autoReconnect && e.isRetriable()) {
                    scheduleReconnect(e)
                } else {
                    error = describe(context, e)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    reconnectAttempt = 0
                }
                if (state == Player.STATE_ENDED && items[index].type != ContentType.LIVE) {
                    saveProgress()
                    // Series: the countdown card owns the transition; a cancelled card stays on the last frame.
                    if (index < items.lastIndex && nextCancelledFor != index) index++
                }
                if (state == Player.STATE_ENDED && items[index].type == ContentType.LIVE && shiftStartSec != null) returnToLive()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                PipState.setVideoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playRequested = playWhenReady
                val cur = items[index]
                if (cur.type != ContentType.LIVE || holdPause[0]) return
                if (!playWhenReady) {
                    pausedAtMs = System.currentTimeMillis()
                    return
                }
                val pausedAt = pausedAtMs
                pausedAtMs = 0
                if (pausedAt <= 0 || shiftStartSec != null || Timeshift.looksLikeReplay(cur.url)) return
                if (!Timeshift.useCatchup(System.currentTimeMillis() - pausedAt, Catchup.available(playlist, cur))) return
                // Long pause on a channel with an archive: continue from where it was paused instead of jumping to live.
                val (from, to) = Timeshift.catchupRange(pausedAt)
                val url = Catchup.urlFor(playlist, cur, from, to, System.currentTimeMillis() / 1000) ?: return
                shiftStartSec = from
                player.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(cur.name).build()).build())
                player.prepare()
                player.play()
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onTrackSelectionParametersChanged(p: TrackSelectionParameters) {
                trackParams = p
            }

            override fun onRenderedFirstFrame() {
                if (cfg.matchFrameRate) applyFrameRate(viewHolder.view, player)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Media3 session: remote media keys and Google Assistant. Declared after the player effect so it is released first.
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).setId("iptv-player").build()
        onDispose { session.release() }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { holdPause[0] = true; saveProgress(); player.pause() }
                Lifecycle.Event.ON_START -> {
                    player.play()
                    // The play-state callback arrives asynchronously; keep ignoring it for a moment.
                    scope.launch { delay(600); holdPause[0] = false; pausedAtMs = 0 }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(index) {
        // Every index change (zap, channel panel, number keys, next episode) lands here: keep the restorable queue current.
        PlayerQueue.index = index
        PlayerQueue.save(context)
        error = null
        epg = emptyList()
        reconnectJob?.cancel()
        reconnectAttempt = 0
        nextSeconds = 0
        shiftStartSec = null
        pausedAtMs = 0
        behindSec = 0
        externalSubs.clear()
        speed = 1f
        player.setPlaybackSpeed(1f)
        headers.value = requestHeadersFor(current)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverrides().build()
        val start = if (live) 0L else repo.store.position(favoriteKey(playlist, current))
        player.setMediaItem(buildItem(current), start)
        player.prepare()
        player.play()
        showOverlay()
        if (live) repo.history.record(playlist, current)
        // UX hook: remember the last live channel for "Açılışta son kanal".
        if (live) repo.scope.launch { repo.settings.set(dev.iptv.player.data.Setting.LastChannel, dev.iptv.player.data.LastChannel.encode(playlist.id, current.id)) }
        if (live) epg = runCatching { repo.shortEpg(playlist, current) }.getOrDefault(emptyList())
    }

    LaunchedEffect(overlayTick, playing, overlay, panel) {
        if (!overlay || !playing || panel != Panel.None) return@LaunchedEffect
        delay(if (controlsActive) 8_000 else 5_000)
        controlsActive = false
        overlay = false
    }

    // VOD: after a few seconds of pause the controls give way to the title details (design v2).
    var pausedIdle by remember { mutableStateOf(false) }
    LaunchedEffect(overlayTick, playRequested, panel) {
        pausedIdle = false
        if (live || playRequested || panel != Panel.None) return@LaunchedEffect
        delay(4_000)
        controlsActive = false
        pausedIdle = true
    }

    LaunchedEffect(overlay) {
        while (overlay) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            buffered = player.bufferedPosition
            if (live) {
                val shift = shiftStartSec
                behindSec = if (shift != null) {
                    Timeshift.behindSec(System.currentTimeMillis() / 1000, shift, player.currentPosition)
                } else {
                    val timeline = player.currentTimeline
                    val target = if (timeline.isEmpty) null
                    else timeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET }
                    Timeshift.behindFromLiveOffset(player.currentLiveOffset.takeIf { it != C.TIME_UNSET }, target)
                }
            }
            delay(500)
        }
    }

    // Next-episode countdown for series: derived from the remaining playback time so pausing pauses it.
    LaunchedEffect(index) {
        if (items[index].type != ContentType.SERIES || index >= items.lastIndex) return@LaunchedEffect
        while (true) {
            delay(500)
            val d = player.duration
            val remaining = d - player.currentPosition
            nextSeconds = if (d > 0 && remaining in 0..NEXT_EPISODE_WINDOW_MS && nextCancelledFor != index &&
                player.playbackState != Player.STATE_IDLE
            ) ((remaining + 999) / 1000).toInt().coerceIn(1, 10) else 0
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            saveProgress()
            player.pause()
            onBack()
        }
    }

    fun commitDigits() {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n in 1..items.size) zapTo(n - 1)
    }
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(2_000)
            commitDigits()
        }
    }

    BackHandler {
        when {
            panel != Panel.None -> closePanel()
            controlsActive -> {
                controlsActive = false
                overlay = false
                controlFocus = PlayerControlFocus.PlayPause
            }
            else -> {
                saveProgress()
                onBack()
            }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(panel, nextVisible, controlsActive) {
        if (panel == Panel.None && !nextVisible && !controlsActive) runCatching { focus.requestFocus() }
    }

    val blocking = panel != Panel.None || nextVisible || recordingTarget != null

    val subtitleSearch = remember(index) {
        if (!OpenSubtitles.enabled || live) null else object : SubtitleSearch {
            override suspend fun search(): List<SubtitleHit> {
                val langs = listOf(cfg.subtitleLanguage.lowercase(), "en").filter { it.length in 2..3 && it != "off" }.distinct().ifEmpty { listOf("en") }
                // Exact match by TMDB id (episodes: show id + season + episode); title search only as a fallback.
                val id = runCatching {
                    val series = current.seriesId
                    if (series != null) {
                        val show = repo.content(playlist, ContentType.SERIES).items.firstOrNull { it.id == series }
                        show?.tmdbId?.takeIf { it > 0 }
                            ?: runCatching { repo.seriesInfo(playlist, series).tmdbId }.getOrNull()?.takeIf { it > 0 }
                            ?: show?.let { repo.tmdb.lookup(it.name, ContentType.SERIES, it.year)?.tmdbId }
                    } else {
                        current.tmdbId?.takeIf { it > 0 } ?: repo.tmdb.lookup(current.name, ContentType.MOVIE, current.year)?.tmdbId
                    }
                }.getOrNull()?.takeIf { it > 0 }
                val exact = if (id != null) runCatching { OpenSubtitles.searchByTmdb(id, current.season, current.episode, langs) }.getOrDefault(emptyList()) else emptyList()
                return exact.ifEmpty { OpenSubtitles.search(splitPlayerTitle(current.name).first, current.year, langs) }
            }

            override suspend fun download(hit: SubtitleHit): Boolean {
                val file = OpenSubtitles.download(context.cacheDir, hit)
                externalSubs.add(
                    SubtitleConfiguration.Builder(Uri.fromFile(file))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(hit.language)
                        .setLabel("OpenSubtitles · ${hit.language.uppercase()}")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                )
                val pos = player.currentPosition
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                player.setMediaItem(buildItem(current), pos)
                player.prepare()
                player.play()
                return true
            }
        }
    }

    // Gömülü altyazıda tercih edilen dil (varsayılan Türkçe) yoksa OpenSubtitles'tan en çok indirilen
    // eşleşmeyi bir kez otomatik getir. Elle seçim yapıldıysa (subtitleAuto=false) dokunma.
    var autoSubTried by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(tracks, current.id) {
        val pref = normalizeLanguage(cfg.subtitleLanguage) ?: return@LaunchedEffect
        val search = subtitleSearch ?: return@LaunchedEffect
        if (live || autoSubTried || !subtitleAuto || tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO }) return@LaunchedEffect
        autoSubTried = true
        val hasPref = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .any { g -> (0 until g.length).any { normalizeLanguage(g.getTrackFormat(it).language) == pref } }
        if (hasPref) return@LaunchedEffect
        val hit = runCatching { search.search() }.getOrNull()
            ?.filter { normalizeLanguage(it.language) == pref }?.maxByOrNull { it.downloads } ?: return@LaunchedEffect
        runCatching { search.download(hit) }
    }


    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle both halves of Back before TV focus groups/native views can consume it.
                if (event.key == Key.Back && (panel != Panel.None || controlsActive)) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (panel != Panel.None) {
                            closePanel()
                        } else {
                            controlsActive = false
                            overlay = false
                            controlFocus = PlayerControlFocus.PlayPause
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (blocking) {
                    // Panels own the D-pad; MENU toggles the settings panel closed again.
                    if (code == KeyEvent.KEYCODE_MENU && panel == Panel.Settings) { closePanel(); return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_CAPTIONS) {
                    if (!live && code == KeyEvent.KEYCODE_CAPTIONS) { panelOpenedFromControls = false; panel = Panel.Tracks }
                    else openSettings(if (code == KeyEvent.KEYCODE_CAPTIONS) PlayerPage.Subtitle else PlayerPage.Main)
                    return@onPreviewKeyEvent true
                }
                if (controlsActive && event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp,
                        Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                    showOverlay()
                    return@onPreviewKeyEvent false
                }
                if (live) {
                    val digit = when (code) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
                        else -> -1
                    }
                    if (digit >= 0) {
                        // "0" on its own is the classic "previous channel" shortcut.
                        if (digit == 0 && digits.isEmpty()) {
                            if (previousIndex in items.indices) zapTo(previousIndex)
                        } else if (digits.length < 4) {
                            digits += digit
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (code == KeyEvent.KEYCODE_LAST_CHANNEL) {
                        if (previousIndex in items.indices) zapTo(previousIndex)
                        return@onPreviewKeyEvent true
                    }
                    if (code == KeyEvent.KEYCODE_GUIDE) { panelOpenedFromControls = false; panel = Panel.Channels; return@onPreviewKeyEvent true }
                    if (code == KeyEvent.KEYCODE_INFO) { showOverlay(); return@onPreviewKeyEvent true }
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> { zapTo((index + 1) % items.size); true }
                        Key.DirectionDown, Key.ChannelDown -> { zapTo((index - 1 + items.size) % items.size); true }
                        Key.DirectionLeft -> { panelOpenedFromControls = false; panel = Panel.Channels; true }
                        Key.DirectionRight -> { openSettings(); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (digits.isNotEmpty()) commitDigits() else { controlsActive = true; showOverlay() }
                            true
                        }
                        Key.MediaPlayPause -> { if (player.playWhenReady) player.pause() else player.play(); showOverlay(); true }
                        Key.MediaFastForward, Key.MediaSkipForward -> if (canReturnToLive) { returnToLive(); true } else false
                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            // OK on the paused info screen resumes, like other streaming apps.
                            if (pausedIdle) player.play() else controlsActive = true
                            showOverlay(); true
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            if (player.playWhenReady) player.pause() else player.play()
                            showOverlay(); true
                        }
                        Key.DirectionLeft, Key.MediaRewind -> { player.seekBack(); showOverlay(); true }
                        Key.DirectionRight, Key.MediaFastForward -> { player.seekForward(); showOverlay(); true }
                        Key.DirectionUp, Key.DirectionDown -> { controlsActive = true; showOverlay(); true }
                        else -> false
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                createPlayerView(ctx).apply {
                    // The Compose indicator also respects paused playback while buffering.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                    viewHolder.view = this
                }
            },
            update = { v ->
                v.resizeMode = when (aspectMode) {
                    AspectMode.Fit -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.Zoom -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                v.subtitleView?.applySubtitleStyle(SubtitleStyle.fromKey(subtitleStyleKey), subtitleScale)
                // Keep subtitles above the VOD controls / track sheet instead of under them.
                v.subtitleView?.setBottomPaddingFraction(
                    when {
                        live -> androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
                        panel == Panel.Tracks -> 0.5f
                        overlay && !pausedIdle && panel == Panel.None -> 0.26f
                        else -> androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
                    },
                )
            },
            modifier = (aspectMode.ratio?.let { Modifier.align(Alignment.Center).aspectRatio(it) } ?: Modifier.fillMaxSize())
                .pointerClick({ controlsActive = true; showOverlay() }),
        )

        if (error == null && reconnectAttempt > 0 && !inPip) {
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xE6111620)).padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                Text(stringResource(R.string.play_reconnecting, reconnectAttempt, MAX_RECONNECTS), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        error?.takeIf { !inPip }?.let {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6111620)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Live, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(context.getString(R.string.play_no_stream), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(it.substringBefore('\n'), style = MaterialTheme.typography.titleMedium, color = Palette.TextMuted)
                it.substringAfter('\n', "").takeIf { c -> c.isNotEmpty() }?.let { c ->
                    Text(c, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 4.dp))
                }
                if (live) {
                    Spacer(Modifier.height(16.dp))
                    Text(context.getString(R.string.play_channel_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
                Spacer(Modifier.height(16.dp))
                PointerButton(onClick = { error = null; reconnectAttempt = 0; player.prepare(); player.play(); showOverlay() }) {
                    Text(stringResource(R.string.ux_retry))
                }
            }
        }

        if (buffering && playRequested && error == null && reconnectAttempt == 0 && !inPip) {
            dev.iptv.player.ui.LoadingState(Modifier.align(Alignment.Center).size(56.dp))
        }

        if (digits.isNotEmpty() && !inPip) {
            Text(
                digits,
                modifier = Modifier.align(Alignment.TopStart).padding(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        if (activeRecording != null && !inPip) {
            Pill(
                context.getString(R.string.play_record_badge),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 68.dp),
                color = Palette.Live,
                contentColor = Color.White,
            )
        }

        if (!live && overlay && !pausedIdle && panel == Panel.None && !nextVisible && !inPip) {
            VodControls(
                name = current.name,
                playing = playRequested,
                position = position,
                buffered = buffered,
                duration = duration,
                speed = speed,
                active = controlsActive,
                onToggle = { if (player.playWhenReady) player.pause() else player.play(); showOverlay() },
                onSeek = { player.seekTo(it); showOverlay() },
                onInteraction = { controlsActive = true; showOverlay() },
                onPrevious = if (current.type == ContentType.SERIES && index > 0) ({ saveProgress(); nextSeconds = 0; index-- }) else null,
                onNext = if (current.type == ContentType.SERIES && index < items.lastIndex) ({ saveProgress(); nextSeconds = 0; index++ }) else null,
                onTracks = { openPanelFromControls(Panel.Tracks, PlayerControlFocus.Tracks) },
                onEpisodes = if (current.type == ContentType.SERIES && items.size > 1) ({ openPanelFromControls(Panel.Episodes, PlayerControlFocus.Episodes) }) else null,
                focusTarget = controlFocus,
            )
            Text(
                formatClock(System.currentTimeMillis() / 1000),
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 40.dp, vertical = 26.dp),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
        if (!live && pausedIdle && panel == Panel.None && !inPip) {
            PausedInfo(
                name = current.name,
                meta = listOfNotNull(vodInfo?.rating?.let { "★ $it" }, vodInfo?.year, vodInfo?.genre).joinToString(" · "),
                plot = vodInfo?.plot,
                cast = vodInfo?.cast ?: pauseCast,
                director = vodInfo?.director,
                position = position,
                duration = duration,
            )
        }

        if (live && overlay && panel == Panel.None && !nextVisible && !inPip) {
            val liveAudio = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_AUDIO, null)
            val liveSubs = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_TEXT, null)
            LiveControls(
                number = index + 1,
                stream = current,
                epg = epg,
                playing = playRequested,
                active = controlsActive,
                behindLabel = if (canReturnToLive) {
                    if (behindSec < 60) stringResource(R.string.play_behind_seconds, behindSec)
                    else stringResource(R.string.play_behind_minutes, behindSec / 60, behindSec % 60)
                } else null,
                recording = activeRecording != null,
                sleepLabel = if (sleepMinutes > 0) stringResource(R.string.play_sleep_remaining, sleepMinutes) else null,
                onToggle = { if (player.playWhenReady) player.pause() else player.play(); showOverlay() },
                onChannels = { openPanelFromControls(Panel.Channels, PlayerControlFocus.Channels) },
                onRecord = ::toggleRecording,
                onTracks = if (liveAudio.size > 1 || liveSubs.isNotEmpty()) ({ openPanelFromControls(Panel.Tracks, PlayerControlFocus.Tracks) }) else null,
                onMore = { openPanelFromControls(Panel.Settings, PlayerControlFocus.More) },
                focusTarget = controlFocus,
                onReturnToLive = if (canReturnToLive) ({ returnToLive(); showOverlay() }) else null,
                favorite = favoriteKey(playlist, current) in playerFavorites,
                onFavorite = { scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, current)) }; showOverlay() },
            )
        }

        if (nextVisible && panel == Panel.None && !inPip) {
            NextEpisodeCard(
                nextTitle = items[(index + 1).coerceAtMost(items.lastIndex)].name,
                seconds = nextSeconds,
                onPlayNow = { saveProgress(); nextSeconds = 0; if (index < items.lastIndex) index++ },
                onCancel = { nextCancelledFor = index; nextSeconds = 0 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp),
            )
        }

        when (panel) {
            Panel.Channels -> ChannelListPanel(
                items = items,
                currentIndex = index,
                playlistId = playlist.id,
                onPick = { zapTo(it); dismissPanel() },
                onClose = ::closePanel,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Panel.Settings -> PlayerSettingsPanel(
                initialPage = settingsPage,
                streamInfo = listOfNotNull(
                    player.videoFormat?.let { f -> "${f.width} × ${f.height}" + if (f.frameRate > 0) " · ${f.frameRate.toInt()} fps" else "" },
                    player.videoFormat?.sampleMimeType,
                    player.audioFormat?.let { f -> listOfNotNull(f.sampleMimeType, f.language, f.sampleRate.takeIf { it > 0 }?.let { "$it Hz" }).joinToString(" · ") },
                ).joinToString("\n\n"),
                live = live,
                aspect = aspectMode,
                onAspect = { m ->
                    aspect = m.key
                    repo.scope.launch { repo.settings.set(Setting.AspectMode, m.key) }
                },
                speed = speed,
                onSpeed = { speed = it; player.setPlaybackSpeed(it) },
                audio = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_AUDIO, null),
                onAudio = { audioAuto = false; player.selectTrack(C.TRACK_TYPE_AUDIO, it) },
                subtitles = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_TEXT, context.getString(R.string.play_off)),
                onSubtitle = { subtitleAuto = false; player.selectTrack(C.TRACK_TYPE_TEXT, it) },
                quality = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_VIDEO, null),
                onQuality = { player.selectTrack(C.TRACK_TYPE_VIDEO, it) },
                subtitleStyle = SubtitleStyle.fromKey(subtitleStyleKey),
                onSubtitleStyle = { style ->
                    subtitleStyleKey = style.key
                    repo.scope.launch { repo.settings.set(Setting.SubtitleStyle, style.key) }
                },
                subtitleScale = subtitleScale,
                onSubtitleScale = { s ->
                    subtitleScale = s
                    repo.scope.launch { repo.settings.set(Setting.SubtitleScale, s) }
                },
                sleepMinutes = sleepMinutes,
                onSleep = { sleepMinutes = it },
                subtitleSearch = subtitleSearch,
                onPreviousChannel = if (live && previousIndex in items.indices) ({ zapTo(previousIndex); panel = Panel.None }) else null,
                onChannelList = if (live) ({ panel = Panel.Channels }) else null,
                onBackToLive = if (canReturnToLive) ({ returnToLive(); panel = Panel.None }) else null,
                recording = activeRecording != null,
                onRecordToggle = if (live) ({ toggleRecording(); panel = Panel.None }) else null,
                onMultiView = if (live) ({ n -> saveProgress(); panel = Panel.None; onMultiView(n) }) else null,
                onPip = if (PipState.supported(context)) ({ panel = Panel.None; activity?.let { PipState.enter(it) } }) else null,
                onClose = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            Panel.Tracks -> AudioSubtitleSheet(
                audio = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_AUDIO, null),
                onAudio = { audioAuto = false; player.selectTrack(C.TRACK_TYPE_AUDIO, it) },
                subtitles = trackOptions(context, tracks, trackParams, C.TRACK_TYPE_TEXT, context.getString(R.string.play_off)),
                onSubtitle = { subtitleAuto = false; player.selectTrack(C.TRACK_TYPE_TEXT, it) },
                style = SubtitleStyle.fromKey(subtitleStyleKey),
                onStyle = { style ->
                    subtitleStyleKey = style.key
                    repo.scope.launch { repo.settings.set(Setting.SubtitleStyle, style.key) }
                },
                scale = subtitleScale,
                onScale = { s ->
                    subtitleScale = s
                    repo.scope.launch { repo.settings.set(Setting.SubtitleScale, s) }
                },
                onOnlineSearch = if (subtitleSearch != null) ({ openSettings(PlayerPage.Online) }) else null,
                onClose = ::closePanel,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Panel.Episodes -> {
                val history by repo.history.entries.collectAsState(initial = emptyList())
                val progressById = remember(history, current.seriesId, playlist.id) {
                    history.filter { it.seriesId == current.seriesId && it.playlistId == playlist.id }
                        .associate { it.id to (if (it.finished) 1f else it.fraction) }
                }
                EpisodesPanel(
                    items = items,
                    currentIndex = index,
                    progressById = progressById,
                    onPick = { targetIdx ->
                        if (targetIdx != index) {
                            saveProgress()
                            nextSeconds = 0
                            controlsActive = false
                            dismissPanel()
                            index = targetIdx
                        } else {
                            controlsActive = false
                            overlay = false
                            dismissPanel()
                        }
                    },
                    onClose = ::closePanel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Panel.None -> Unit
        }
    }
    if (showInfo) DescriptionDialog(current.name,
        titleInfo?.takeIf { it.isNotBlank() } ?: context.getString(if (titleInfoLoading) R.string.ux_info_loading else R.string.ux_info_unknown)) { showInfo = false }
    recordingTarget?.let { target ->
        RecordingDurationDialog(
            channelName = target.stream.name,
            programmeEndSec = target.programmeEndSec,
            onSelectEnd = { endSec ->
                recordingTarget = null
                scope.launch {
                    toast(RecordingManager.startNow(context, playlist, target.stream, target.title, endSec).message)
                }
            },
            onDismiss = { recordingTarget = null },
        )
    }
}

/** Network-ish failures worth retrying on a live stream. */
private fun PlaybackException.isRetriable() = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_TIMEOUT -> true
    else -> false
}
