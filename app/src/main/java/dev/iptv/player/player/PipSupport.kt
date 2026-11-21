package dev.iptv.player.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow

object PipMath {
    /** Android only accepts PiP aspect ratios between 1:2.39 and 2.39:1. Returns (numerator, denominator). */
    fun ratio(width: Int, height: Int, pixelRatio: Float = 1f): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val pr = if (pixelRatio > 0f) pixelRatio else 1f
        val w = (width * pr * 100).toInt().coerceAtLeast(1)
        val h = height * 100
        val r = w.toFloat() / h
        return when {
            r > 2.39f -> 239 to 100
            r < 1f / 2.39f -> 100 to 239
            else -> w to h
        }
    }
}

/** Process-wide Picture-in-Picture state shared between MainActivity and the player screen. */
object PipState {
    val inPip = MutableStateFlow(false)

    /** True while a live channel is playing: HOME then shrinks the player instead of leaving. */
    @Volatile var autoEnter = false
    @Volatile private var ratio: Pair<Int, Int> = 16 to 9

    fun setVideoSize(width: Int, height: Int, pixelRatio: Float) {
        ratio = PipMath.ratio(width, height, pixelRatio)
    }

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(activity: Activity): Boolean {
        if (!supported(activity) || activity.isInPictureInPictureMode) return false
        return try {
            val (n, d) = ratio
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(n, d)).build())
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
