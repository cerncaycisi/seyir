package dev.iptv.player.player

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.iptv.player.R

enum class SubtitleStyle(val key: String, val labelRes: Int) {
    Clean("clean", R.string.sub_style_clean),
    Soft("soft", R.string.sub_style_soft),
    Yellow("yellow", R.string.sub_style_yellow),
    Contrast("contrast", R.string.sub_style_contrast),
    Original("original", R.string.sub_style_original);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Clean
    }
}

/** Same renderer and styling for the preview and actual embedded/external text subtitles. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun SubtitleView.applySubtitleStyle(style: SubtitleStyle, scale: Int) {
    setApplyEmbeddedStyles(style == SubtitleStyle.Original)
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale.coerceIn(50, 200) / 100f)
    setBottomPaddingFraction(0.08f)
    setStyle(when (style) {
        SubtitleStyle.Original -> CaptionStyleCompat.DEFAULT
        else -> CaptionStyleCompat(
            if (style == SubtitleStyle.Yellow) Color.rgb(255, 224, 102) else Color.WHITE,
            if (style == SubtitleStyle.Contrast) 0xCC000000.toInt() else Color.TRANSPARENT,
            Color.TRANSPARENT,
            when (style) {
                SubtitleStyle.Soft -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                SubtitleStyle.Contrast -> CaptionStyleCompat.EDGE_TYPE_NONE
                else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            },
            Color.BLACK,
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
        )
    })
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun SubtitlePreview(style: SubtitleStyle, scale: Int, modifier: Modifier = Modifier) {
    val sample = stringResource(R.string.sub_style_preview)
    AndroidView(
        factory = { SubtitleView(it).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        update = {
            it.applySubtitleStyle(style, scale)
            it.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale.coerceIn(50, 200) / 100f)
            it.setCues(listOf(Cue.Builder().setText(sample).build()))
        },
        modifier = modifier.fillMaxWidth().height(90.dp).background(androidx.compose.ui.graphics.Color(0xFF283441)),
    )
}
