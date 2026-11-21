package dev.iptv.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.iptv.player.R

/** Choose a bounded recording without reserving space for an entire long programme. */
@Composable
fun RecordingDurationDialog(
    channelName: String,
    programmeEndSec: Long?,
    onSelectEnd: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val nowSec = System.currentTimeMillis() / 1000
    val actions = buildList {
        for (minutes in listOf(5, 15, 30, 60)) {
            add(CardAction(context.getString(R.string.play_record_duration_minutes, minutes)) {
                onSelectEnd(System.currentTimeMillis() / 1000 + minutes * 60L)
            })
        }
        if (programmeEndSec != null && programmeEndSec > nowSec) {
            add(CardAction(context.getString(R.string.play_record_until_end)) {
                onSelectEnd(programmeEndSec)
            })
        }
    }
    ActionCard(
        title = stringResource(R.string.play_record_duration_title),
        subtitle = channelName,
        actions = actions,
        onDismiss = onDismiss,
    )
}
