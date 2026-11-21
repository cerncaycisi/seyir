package dev.iptv.player.ui

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.iptv.player.data.ContentPrefs
import dev.iptv.player.data.ParentalFilter
import dev.iptv.player.data.ParentalSession
import kotlinx.coroutines.launch

/** Asks for the stored parental PIN. */
@Composable
fun PinVerifyDialog(prefs: ContentPrefs, title: String = stringResource(R.string.ui_pin_enter), onVerified: () -> Unit, onDismiss: () -> Unit) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    PinDialog(
        title = title,
        error = error,
        onComplete = { pin ->
            scope.launch {
                if (prefs.verifyPin(pin)) {
                    ParentalSession.unlocked = true
                    onVerified()
                } else {
                    error = uiContext.getString(R.string.ui_pin_wrong)
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** Verifies against an arbitrary stored (hashed) PIN, e.g. a profile PIN. Does not unlock the parental session. */
@Composable
fun PinCheckDialog(stored: String, title: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    val uiContext = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    PinDialog(
        title = title,
        error = error,
        onComplete = { pin -> if (ParentalFilter.verifyPin(stored, pin)) onVerified() else error = uiContext.getString(R.string.ui_pin_wrong) },
        onDismiss = onDismiss,
    )
}

/** Two-step PIN creation (enter, then confirm). */
@Composable
fun PinCreateDialog(title: String = stringResource(R.string.ui_pin_new), onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    val uiContext = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    PinDialog(
        title = if (first == null) title else uiContext.getString(R.string.ui_pin_repeat),
        subtitle = uiContext.getString(R.string.ui_pin_digits),
        error = error,
        onComplete = { pin ->
            val f = first
            when {
                f == null -> { first = pin; error = null }
                f == pin -> onCreated(pin)
                else -> { first = null; error = uiContext.getString(R.string.ui_pin_mismatch) }
            }
        },
        onDismiss = onDismiss,
    )
}
