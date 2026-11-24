package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density

/**
 * The screens were laid out for a 960 × 540 dp TV canvas. A landscape phone is about as wide but only
 * ~360–410 dp tall, so the UI is drawn on a slightly denser grid until its short side reaches
 * [PHONE_TARGET_SHORT_SIDE_DP]. Tablets and larger windows keep the device density.
 */
const val PHONE_TARGET_SHORT_SIDE_DP = 460f
private const val PHONE_MIN_SCALE = 0.78f

@Composable
fun phoneDensity(base: Density): Density {
    val config = LocalConfiguration.current
    val shortSide = minOf(config.screenWidthDp, config.screenHeightDp).toFloat()
    // Below 320 dp the window is a PiP thumbnail or a split screen: the overlays are hidden there anyway.
    val scale = if (shortSide >= 320f && shortSide < PHONE_TARGET_SHORT_SIDE_DP) {
        (shortSide / PHONE_TARGET_SHORT_SIDE_DP).coerceAtLeast(PHONE_MIN_SCALE)
    } else {
        1f
    }
    return Density(base.density * scale, base.fontScale.coerceIn(0.85f, 1.15f))
}

/** Root container: keeps the UI out of the display cutout and above the on-screen keyboard. */
@Composable
fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .imePadding(),
    ) {
        content()
    }
}
