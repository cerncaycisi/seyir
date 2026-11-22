package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Minimum touch target; also the height of a header button / picker row. */
val MinTapTarget = 48.dp

/**
 * "Label: value ▾" button that opens a [ListPickerDialog]. OK/tap opens the list, picking closes it.
 * Works with D-pad (focus ring), mouse and touch.
 */
@Composable
fun <T> DropdownButton(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val value = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val onClick = { open = true }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTapTarget).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.heightIn(min = MinTapTarget).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label:", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
    if (open) {
        ListPickerDialog(label, options, selected, onSelect = { onSelect(it); open = false }, onDismiss = { open = false })
    }
}

/**
 * Modal list of options with a check on the current one. Initial focus is on the current option; OK / tap selects,
 * BACK / back gesture / tap outside closes.
 */
@Composable
fun <T> ListPickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    width: Dp = 340.dp,
    maxListHeight: Dp = 340.dp,
) {
    val current = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.padding(horizontal = 8.dp))
            LazyColumn(Modifier.heightIn(max = maxListHeight), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(options) { i, (value, text) ->
                    val isCurrent = i == current
                    val pick = { onSelect(value) }
                    Surface(
                        onClick = pick,
                        modifier = Modifier.fillMaxWidth().heightIn(min = MinTapTarget).pointerClick(pick).then(if (isCurrent) Modifier.focusRequester(focus) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Palette.SurfaceHigh.copy(alpha = if (isCurrent) 1f else 0.0f),
                            contentColor = Palette.Text,
                            focusedContainerColor = Color(0xFFF5F5F7),
                            focusedContentColor = Color(0xFF07080A),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = MinTapTarget).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isCurrent) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
