package dev.iptv.player.ui

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.iptv.player.data.PhoneMode
import dev.iptv.player.data.RemoteListener
import dev.iptv.player.data.RemoteServer
import dev.iptv.player.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun qrImage(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Starts the LAN server while shown and displays URL + QR. In [PhoneMode.ADD] a successful add closes it
 * after a short confirmation and calls [onAdded].
 */
@Composable
fun PhoneConnectDialog(repo: Repository, mode: PhoneMode, onDismiss: () -> Unit, onAdded: () -> Unit = {}) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val added by rememberUpdatedState(onAdded)
    val closeFocus = remember { FocusRequester() }

    DisposableEffect(mode) {
        val server = RemoteServer.start(repo, mode, object : RemoteListener {
            override fun onPlaylistAdded(name: String) {
                scope.launch {
                    status = uiContext.getString(R.string.ui_phone_added, name)
                    delay(1200)
                    added()
                }
            }
            override fun onBackupExported() { scope.launch { status = uiContext.getString(R.string.ui_backup_exported) } }
            override fun onBackupRestored() { scope.launch { status = uiContext.getString(R.string.ui_backup_restored) } }
        })
        if (server == null) failed = true else url = server.url
        onDispose { server?.stop() }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier.width(720.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = url
            if (current != null) {
                val qr = remember(current) { qrImage(current) }
                Image(
                    bitmap = qr,
                    contentDescription = uiContext.getString(R.string.ui_qr_description),
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(230.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White),
                )
            } else {
                Box(Modifier.size(230.dp).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (mode == PhoneMode.ADD) uiContext.getString(R.string.ui_phone_playlist) else uiContext.getString(R.string.ui_phone_backup),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                )
                Text(uiContext.getString(R.string.ui_phone_step_one), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                Text(uiContext.getString(R.string.ui_phone_step_two), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                Text(
                    when {
                        failed -> uiContext.getString(R.string.ui_network_missing)
                        else -> url ?: uiContext.getString(R.string.ui_preparing)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) Palette.Live else Palette.Primary,
                    modifier = Modifier.semantics { contentDescription = uiContext.getString(R.string.ui_address) },
                )
                Text(
                    uiContext.getString(R.string.ui_phone_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(4.dp))
                Text(status ?: uiContext.getString(R.string.ui_phone_waiting), style = MaterialTheme.typography.titleSmall, color = if (status != null) Palette.Accent else Palette.TextMuted)
                Spacer(Modifier.height(8.dp))
                TvButton(uiContext.getString(R.string.ui_close), onDismiss, Modifier.fillMaxWidth().focusRequester(closeFocus))
            }
        }
    }
}
