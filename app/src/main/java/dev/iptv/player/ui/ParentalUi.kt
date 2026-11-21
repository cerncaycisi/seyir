package dev.iptv.player.ui

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
fun PinVerifyDialog(prefs: ContentPrefs, title: String = "PIN girin", onVerified: () -> Unit, onDismiss: () -> Unit) {
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
                    error = "Yanlış PIN"
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** Verifies against an arbitrary stored (hashed) PIN, e.g. a profile PIN. Does not unlock the parental session. */
@Composable
fun PinCheckDialog(stored: String, title: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    PinDialog(
        title = title,
        error = error,
        onComplete = { pin -> if (ParentalFilter.verifyPin(stored, pin)) onVerified() else error = "Yanlış PIN" },
        onDismiss = onDismiss,
    )
}

/** Two-step PIN creation (enter, then confirm). */
@Composable
fun PinCreateDialog(title: String = "Yeni PIN belirleyin", onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    PinDialog(
        title = if (first == null) title else "PIN'i tekrar girin",
        subtitle = "4 haneli bir sayı",
        error = error,
        onComplete = { pin ->
            val f = first
            when {
                f == null -> { first = pin; error = null }
                f == pin -> onCreated(pin)
                else -> { first = null; error = "PIN'ler eşleşmedi, tekrar deneyin" }
            }
        },
        onDismiss = onDismiss,
    )
}
