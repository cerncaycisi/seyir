package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R

/** Asked when something with saved progress is opened without an explicit resume / restart choice. Resume has focus. */
@Composable
fun ResumeDialog(positionMs: Long, onResume: () -> Unit, onRestart: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prompt = ctx.getString(R.string.ui_resume_prompt, formatPosition(positionMs))
    val resumeLabel = ctx.getString(R.string.ui_resume_continue)
    val restartLabel = ctx.getString(R.string.ui_restart)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(380.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
            TvButton(resumeLabel, onResume, Modifier.fillMaxWidth().focusRequester(focus), Icons.Rounded.PlayArrow, primary = true)
            TvButton(restartLabel, onRestart, Modifier.fillMaxWidth(), Icons.Rounded.Replay)
        }
    }
}
