package dev.iptv.player.player

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which third of the screen a double tap landed in. */
enum class TapZone { Start, Center, End }

class GestureFeedback(val icon: ImageVector, val text: String = "")

/**
 * Touch layer under the player overlays (above the video):
 * - tap: [onTap] (show / hide the controls, close a panel)
 * - double tap: [onDoubleTap] with the screen third (VOD: seek back / play-pause / seek forward); the
 *   returned [GestureFeedback] is flashed in the middle of the screen
 * - horizontal swipe: [onSwipe] (+1 = swipe left = next, -1 = previous; live zapping), or a relative
 *   seek through [onSeekBy] when [seekPosition] is given (VOD)
 * - vertical swipe: screen brightness on the left half, media volume on the right half
 *
 * Overlay controls placed after this layer get their touches first; empty areas fall through to it.
 */
@Composable
fun PlayerGestureLayer(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: ((TapZone) -> GestureFeedback?)? = null,
    onSwipe: ((Int) -> Unit)? = null,
    seekPosition: (() -> Pair<Long, Long>)? = null,
    onSeekBy: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var feedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedbackTick) {
        if (feedback == null) return@LaunchedEffect
        delay(800)
        feedback = null
    }
    fun show(icon: ImageVector, text: String) {
        feedback = GestureFeedback(icon, text)
        feedbackTick++
    }

    // The player owns the brightness only while it is open.
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    fun currentBrightness(): Float {
        val own = activity?.window?.attributes?.screenBrightness ?: -1f
        if (own >= 0f) return own
        val system = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return (system / 255f).coerceIn(0.01f, 1f)
    }

    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val swipe by rememberUpdatedState(onSwipe)
    val seekPos by rememberUpdatedState(seekPosition)
    val seekBy by rememberUpdatedState(onSeekBy)
    val hasDoubleTap = onDoubleTap != null
    val brightnessLabel = context.getString(R.string.gesture_brightness)
    val volumeLabel = context.getString(R.string.gesture_volume)

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(hasDoubleTap) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = if (hasDoubleTap) { offset ->
                        val zone = when {
                            offset.x < size.width / 3f -> TapZone.Start
                            offset.x > size.width * 2f / 3f -> TapZone.End
                            else -> TapZone.Center
                        }
                        doubleTap?.invoke(zone)?.let { show(it.icon, it.text) }
                    } else null,
                )
            }
            .pointerInput(Unit) {
                val axisSlop = 18.dp.toPx()
                val swipeDistance = 72.dp.toPx()
                var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
                var startX = 0f
                var dx = 0f
                var dy = 0f
                var level = 0f
                var volumeLevel = 0f
                var seekBase = 0L
                var seekDuration = 0L
                var seekDelta = 0L
                detectDragGestures(
                    onDragStart = { offset ->
                        axis = 0; dx = 0f; dy = 0f; startX = offset.x; seekDelta = 0L
                    },
                    onDragEnd = {
                        if (axis == 1) {
                            val s = swipe
                            val by = seekBy
                            if (by != null && seekPos != null) {
                                if (seekDelta != 0L) by(seekDelta)
                            } else if (s != null && abs(dx) >= swipeDistance) {
                                s(if (dx < 0) 1 else -1)
                            }
                        }
                        axis = 0
                    },
                    onDragCancel = { axis = 0 },
                ) { change, amount ->
                    dx += amount.x
                    dy += amount.y
                    if (axis == 0) {
                        if (abs(dx) < axisSlop && abs(dy) < axisSlop) return@detectDragGestures
                        axis = if (abs(dx) > abs(dy)) 1 else 2
                        if (axis == 1) {
                            seekPos?.invoke()?.let { (pos, dur) -> seekBase = pos; seekDuration = dur }
                        } else {
                            level = currentBrightness()
                            volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        }
                    }
                    change.consume()
                    if (axis == 1) {
                        if (seekPos != null && seekDuration > 0) {
                            // A full-width swipe moves 3 minutes.
                            val raw = (dx / size.width * 180_000f).toLong()
                            val target = (seekBase + raw).coerceIn(0L, seekDuration)
                            seekDelta = target - seekBase
                            show(
                                if (seekDelta < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                                "${if (seekDelta < 0) "−" else "+"}${clock(abs(seekDelta))}  ·  ${clock(target)}",
                            )
                        }
                        return@detectDragGestures
                    }
                    val step = -amount.y / (size.height * 0.75f)
                    if (startX < size.width / 2f) {
                        val window = activity?.window ?: return@detectDragGestures
                        level = (level + step).coerceIn(0.01f, 1f)
                        window.attributes = window.attributes.apply { screenBrightness = level }
                        show(Icons.Rounded.BrightnessMedium, "$brightnessLabel ${(level * 100).roundToInt()}%")
                    } else {
                        volumeLevel = (volumeLevel + step).coerceIn(0f, 1f)
                        val v = (volumeLevel * maxVolume).roundToInt()
                        if (v != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
                        }
                        show(Icons.AutoMirrored.Rounded.VolumeUp, "$volumeLabel ${(v * 100f / maxVolume).roundToInt()}%")
                    }
                }
            },
    ) {
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val f = feedback ?: return@AnimatedVisibility
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(f.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                if (f.text.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(f.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Blocks taps on an overlay panel's empty areas from reaching [PlayerGestureLayer] underneath. */
fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }
