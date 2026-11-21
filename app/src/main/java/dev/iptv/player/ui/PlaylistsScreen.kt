package dev.iptv.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    activeId: String?,
    onSelect: (Playlist) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var focusId by remember { mutableStateOf(activeId) }
    val listFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val focusTarget = playlists.firstOrNull { it.id == focusId }?.id ?: playlists.firstOrNull()?.id

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(44.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Playlistler", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(
                        "Kendi IPTV sağlayıcınızın M3U bağlantısını veya Xtream bilgilerini ekleyin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            val target = pendingDelete
            if (target != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(28.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Palette.Live, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("\"${target.name}\" silinsin mi?", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                    }
                    Text(
                        "Playlist ve bu playlist'e ait favoriler bu cihazdan kaldırılır. Aboneliğiniz etkilenmez.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                    Spacer(Modifier.height(24.dp))
                    // The safe choice comes first and holds focus, so a stray OK never deletes anything.
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = { focusId = target.id; pendingDelete = null },
                            modifier = Modifier.focusRequester(cancelFocus),
                        ) { Text("Vazgeç") }
                        Button(onClick = { focusId = null; onDelete(target); pendingDelete = null }) { Text("Sil") }
                    }
                }
                LaunchedEffect(target) { runCatching { cancelFocus.requestFocus() } }
                return@Column
            }

            LaunchedEffect(focusTarget, playlists.size) {
                withFrameNanos { }
                runCatching { listFocus.requestFocus() }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                items(playlists) { p ->
                    PlaylistRow(
                        playlist = p,
                        active = p.id == activeId,
                        onClick = { onSelect(p) },
                        onLongClick = { pendingDelete = p },
                        modifier = if (p.id == focusTarget) Modifier.focusRequester(listFocus) else Modifier,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onAdd,
                        modifier = if (focusTarget == null) Modifier.focusRequester(listFocus) else Modifier,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Playlist ekle")
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        Text(
                            "Silmek için bir playlist'in üzerinde OK tuşunu basılı tutun.",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val (icon, kind, detail) = when (val s = playlist.source) {
        is PlaylistSource.M3u -> Triple(Icons.Rounded.Link, "M3U", s.url)
        is PlaylistSource.Xtream -> Triple(Icons.Rounded.Dns, "Xtream", "${s.server} · ${s.username}")
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(68.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Primary), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tileBrush(playlist.name)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$kind · $detail", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (active) Pill("AKTİF", color = Palette.Primary, contentColor = Color.White)
        }
    }
}
