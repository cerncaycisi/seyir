package dev.iptv.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Keep the heading visible instead of applying TV's centered scrolling pivot to an action button. */
@OptIn(ExperimentalFoundationApi::class)
private object DetailBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0 -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailViewport(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailBringIntoView, content = content)
}
