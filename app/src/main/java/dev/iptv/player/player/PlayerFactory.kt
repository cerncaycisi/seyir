package dev.iptv.player.player

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.iptv.player.R
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore

/** Snapshot of the player-related settings, read once when the player screen opens. */
data class PlayerConfig(
    val aspectMode: String,
    val audioLanguage: String,
    val subtitleLanguage: String,
    val subtitleScale: Int,
    val subtitleStyle: String,
    val bufferSeconds: Int,
    val userAgent: String,
    val autoReconnect: Boolean,
    val externalPlayer: String,
    val matchFrameRate: Boolean,
) {
    companion object {
        suspend fun load(s: SettingsStore) = PlayerConfig(
            aspectMode = s.get(Setting.AspectMode),
            audioLanguage = s.get(Setting.PreferredAudioLanguage),
            subtitleLanguage = s.get(Setting.PreferredSubtitleLanguage),
            subtitleScale = s.get(Setting.SubtitleScale),
            subtitleStyle = s.get(Setting.SubtitleStyle),
            bufferSeconds = s.get(Setting.BufferSeconds),
            userAgent = s.get(Setting.UserAgent).ifBlank { "IPTVPlayer/0.1" },
            autoReconnect = s.get(Setting.AutoReconnect),
            externalPlayer = s.get(Setting.ExternalPlayer),
            matchFrameRate = s.get(Setting.MatchFrameRate),
        )
    }
}

/** Per-stream HTTP headers applied to every request the player makes (manifest, segments, files). */
class RequestHeaders {
    @Volatile
    var value: Map<String, String> = emptyMap()
}

/**
 * Software-emulated displays need video in the same compositor as Compose.
 * Real TVs retain SurfaceView for HDR, efficient decoding and frame-rate switching.
 */
@OptIn(UnstableApi::class)
fun createPlayerView(context: Context): PlayerView {
    val emulatedDisplay = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"
    return (if (emulatedDisplay) {
        LayoutInflater.from(context).inflate(R.layout.player_texture_view, null, false) as PlayerView
    } else {
        PlayerView(context)
    }).apply {
        useController = false
        keepScreenOn = true
        isFocusable = false
        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
    }
}

@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, cfg: PlayerConfig, headers: RequestHeaders): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(cfg.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val upstream = DefaultDataSource.Factory(context, http)
    val dataSource = ResolvingDataSource.Factory(upstream) { spec ->
        val h = headers.value
        if (h.isEmpty()) spec else spec.buildUpon().setHttpRequestHeaders(h + spec.httpRequestHeaders).build()
    }

    // PREFER: the FFmpeg extension (AC3/E-AC3/DTS...) is tried before platform decoders when it is on the classpath.
    val renderers = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)

    val bufferMs = cfg.bufferSeconds.coerceIn(5, 120) * 1000
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(bufferMs, bufferMs, minOf(2_500, bufferMs), minOf(5_000, bufferMs))
        // Keep a minute behind the playhead so a short live pause (or small rewind) resumes from memory, see Timeshift.
        .setBackBuffer(60_000, true)
        .build()

    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .apply {
            playWhenReady = true
            // We drive Surface.setFrameRate ourselves (see applyFrameRate) so the user setting fully controls it.
            if (cfg.matchFrameRate) videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                // "original" is resolved per title once tracks are known (PlayerScreen → originalAudioPick).
                if (cfg.audioLanguage.isNotBlank() && cfg.audioLanguage != dev.iptv.player.data.ORIGINAL_AUDIO) setPreferredAudioLanguage(cfg.audioLanguage)
                if (cfg.subtitleLanguage.isBlank() || cfg.subtitleLanguage.equals("off", true)) {
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    setPreferredTextLanguage(cfg.subtitleLanguage)
                    // Prefer the full subtitle over a "forced" (signs-only) track of the same language.
                    setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED)
                }
            }.build()
        }
}

/** Frame-rate matching (API 30+): asks the display to switch to the video's frame rate. */
@OptIn(UnstableApi::class)
fun applyFrameRate(view: PlayerView?, player: ExoPlayer) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || view == null) return
    val fps = player.videoFormat?.frameRate ?: return
    if (fps <= 0f) return
    val surface = (view.videoSurfaceView as? SurfaceView)?.holder?.surface ?: return
    if (!surface.isValid) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS)
        } else {
            surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }
}
