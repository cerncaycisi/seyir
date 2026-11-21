package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R
import kotlinx.coroutines.launch

@Composable
fun ExpandablePlot(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(text, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        PointerOutlinedButton(onClick = { open = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.ux_read_more))
        }
    }
    if (open) DescriptionDialog(title, text) { open = false }
}

/** A bounded, remote-scrollable reader. Back dismisses in one press. */
@Composable
fun DescriptionDialog(title: String, text: String, onDismiss: () -> Unit) {
    // A Dialog's native context may use the device language instead of the app override.
    val hint = androidx.compose.ui.platform.LocalContext.current.getString(R.string.ux_reader_hint)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(680.dp).heightIn(max = 440.dp)
            .background(Palette.Surface, RoundedCornerShape(20.dp)).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Palette.TextMuted, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)
                    .focusRequester(focus).onPreviewKeyEvent { event ->
                        if (event.key != Key.DirectionUp && event.key != Key.DirectionDown) false
                        else {
                            if (event.type == KeyEventType.KeyDown) scope.launch {
                                scroll.scrollTo((scroll.value + if (event.key == Key.DirectionDown) 100 else -100).coerceIn(0, scroll.maxValue))
                            }
                            true
                        }
                    }.focusable())
            Spacer(Modifier.height(16.dp))
            Text(hint, color = Palette.TextFaint, style = MaterialTheme.typography.labelMedium)
        }
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
    }
}
