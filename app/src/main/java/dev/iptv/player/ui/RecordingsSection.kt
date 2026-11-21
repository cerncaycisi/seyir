package dev.iptv.player.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.record.RecStatus
import dev.iptv.player.record.Recording
import dev.iptv.player.record.RecordingDb
import dev.iptv.player.record.RecordingManager
import dev.iptv.player.record.RecordingMath
import dev.iptv.player.record.RecordingStorage
import kotlinx.coroutines.launch
import java.io.File

/** Queue entry that plays a finished recording from disk. Ids start with "rec:" so history/progress code can tell them apart. */
fun recordingStream(rec: Recording): Stream = Stream(
    id = "rec:${rec.id}",
    name = "${rec.channelName} · ${rec.title}".trim(' ', '·'),
    logo = null,
    url = Uri.fromFile(File(rec.filePath.orEmpty())).toString(),
    categoryId = "",
    type = ContentType.MOVIE,
)

@Composable
fun RecordingsSection(repo: Repository, playlist: Playlist, onPlay: (List<Stream>, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { RecordingDb.get(context).dao() }
    val recs by remember { dao.observeAll() }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf<Recording?>(null) }
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val free = remember(recs) { RecordingStorage.freeBytes(RecordingStorage.dir(context)) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { RecordingManager.recoverStale(context) }
    LaunchedEffect(recs.isNotEmpty()) {
        if (recs.isNotEmpty()) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }

    fun play(rec: Recording) {
        val path = rec.filePath
        if (path == null || !File(path).exists()) {
            message = "Kayıt dosyası bulunamadı"
            return
        }
        onPlay(listOf(recordingStream(rec)), 0)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Kayıtlar", "Boş alan: ${RecordingMath.formatBytes(free)}  ·  Kayıtlar uygulamaya özel depolamada tutulur")
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Gold, modifier = Modifier.padding(bottom = 8.dp)) }
        if (recs.isEmpty()) {
            EmptyState(
                Icons.Rounded.FiberManualRecord,
                "Henüz kayıt yok",
                "Canlı yayında Ayarlar panelinden \"Kaydı başlat\", Rehber'de ise gelecek programlarda \"Kaydı planla\" seçin",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 48.dp, start = 4.dp, end = 4.dp, top = 4.dp)) {
                itemsIndexed(recs, key = { _, r -> r.id }) { i, r ->
                    RecordingRow(
                        r,
                        onClick = { if (r.status == RecStatus.DONE) play(r) else menu = r },
                        onLongClick = { menu = r },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }

    menu?.let { r ->
        val actions = buildList {
            if (r.status == RecStatus.DONE) add(CardAction("Oynat", Icons.Rounded.PlayArrow) { menu = null; play(r) })
            if (r.status == RecStatus.RECORDING) add(CardAction("Kaydı durdur", Icons.Rounded.Stop) { menu = null; RecordingManager.stop(context, r.id) })
            if (r.status == RecStatus.SCHEDULED) add(CardAction("Planı iptal et", Icons.Rounded.Stop) { menu = null; scope.launch { RecordingManager.cancel(context, r) } })
            if (r.status != RecStatus.SCHEDULED) add(CardAction("Sil", Icons.Rounded.Delete, destructive = true) { menu = null; confirmDelete = r })
        }
        ActionCard(r.channelName, r.title, actions, onDismiss = { menu = null })
    }
    confirmDelete?.let { r ->
        ConfirmDialog(
            title = "Kaydı sil",
            message = "\"${r.title.ifBlank { r.channelName }}\" cihazdan kalıcı olarak silinecek.",
            confirmLabel = "Sil",
            onConfirm = { confirmDelete = null; scope.launch { RecordingManager.delete(context, r) } },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun RecordingRow(rec: Recording, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(64.dp).pointerClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Primary), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    rec.title.ifBlank { rec.channelName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildList {
                    add(rec.channelName)
                    add(formatDayTime(rec.startAt / 1000))
                    rec.error?.takeIf { rec.status != RecStatus.DONE || it.isNotBlank() }?.let { add(it) }
                }.joinToString("  ·  ")
                Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (rec.bytes > 0) {
                Text(RecordingMath.formatBytes(rec.bytes), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Spacer(Modifier.width(12.dp))
            }
            when (rec.status) {
                RecStatus.SCHEDULED -> Pill("PLANLANDI", color = Palette.Gold, contentColor = Color.Black)
                RecStatus.RECORDING -> Pill("KAYIT", color = Palette.Live, contentColor = Color.White)
                RecStatus.DONE -> Pill("HAZIR", color = Palette.Primary, contentColor = Color.White)
                else -> Pill("HATA")
            }
        }
    }
}
