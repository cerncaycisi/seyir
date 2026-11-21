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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Repository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddPlaylistScreen(repo: Repository, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var xtream by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val source = if (xtream) PlaylistSource.Xtream(server, username, password) else PlaylistSource.M3u(url)
        val valid = if (xtream) server.isNotBlank() && username.isNotBlank() && password.isNotBlank() else url.isNotBlank()
        if (!valid) {
            error = "Lütfen tüm alanları doldurun"
            return
        }
        val playlist = Playlist(UUID.randomUUID().toString(), name.ifBlank { if (xtream) server else "M3U listesi" }, source)
        busy = true
        error = null
        scope.launch {
            when (val result = loadCatching { repo.verify(playlist) }) {
                is Load.Ok -> {
                    repo.store.add(playlist)
                    onSaved()
                }
                is Load.Err -> error = result.message
                Load.Loading -> Unit
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 32.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Playlist ekle", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text("Bağlantı türünü seçin ve bilgileri girin. Bilgiler kaydetmeden önce doğrulanır.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TypeOption(Icons.Rounded.Dns, "Xtream Codes", "Sunucu, kullanıcı adı ve şifre", xtream, Modifier.weight(1f)) { xtream = true }
                TypeOption(Icons.Rounded.Link, "M3U bağlantısı", "Tek bir playlist adresi", !xtream, Modifier.weight(1f)) { xtream = false }
            }
            Spacer(Modifier.height(8.dp))
            TvTextField(name, { name = it }, "Ad (isteğe bağlı)", leadingIcon = Icons.Rounded.Label)
            if (xtream) {
                TvTextField(server, { server = it }, "Sunucu adresi (ör. http://sunucu.com:8080)", keyboardType = KeyboardType.Uri, leadingIcon = Icons.Rounded.Dns)
                TvTextField(username, { username = it }, "Kullanıcı adı", leadingIcon = Icons.Rounded.Person)
                TvTextField(password, { password = it }, "Şifre", password = true, leadingIcon = Icons.Rounded.Lock)
            } else {
                TvTextField(url, { url = it }, "M3U adresi (http://…)", keyboardType = KeyboardType.Uri, leadingIcon = Icons.Rounded.Link)
            }
            error?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Live.copy(alpha = 0.12f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Live)
                    Spacer(Modifier.width(10.dp))
                    Text(it, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = { if (!busy) save() }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
                Text(if (busy) "Kontrol ediliyor…" else "Kaydet ve bağlan")
            }
        }
    }
}

// One composable for both states so choosing a type does not drop focus.
@Composable
private fun TypeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.SurfaceHigh else Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(2.dp, if (selected) Palette.Primary else Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Palette.Primary), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) Palette.Primary else Palette.TextMuted, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Palette.Primary)
        }
    }
}
