package dev.iptv.player.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Repository
import kotlinx.coroutines.launch

/** Edits name and credentials / URL of an existing playlist. Changed credentials are verified before saving. */
@Composable
fun EditPlaylistScreen(repo: Repository, playlist: Playlist, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val source = playlist.source
    var name by rememberSaveable { mutableStateOf(playlist.name) }
    var server by rememberSaveable { mutableStateOf((source as? PlaylistSource.Xtream)?.server.orEmpty()) }
    var username by rememberSaveable { mutableStateOf((source as? PlaylistSource.Xtream)?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf((source as? PlaylistSource.Xtream)?.password.orEmpty()) }
    var url by rememberSaveable { mutableStateOf((source as? PlaylistSource.M3u)?.url.orEmpty()) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val newSource = when (source) {
            is PlaylistSource.Xtream -> PlaylistSource.Xtream(server.trim(), username.trim(), password.trim())
            is PlaylistSource.M3u -> PlaylistSource.M3u(url.trim())
        }
        val valid = when (newSource) {
            is PlaylistSource.Xtream -> newSource.server.isNotBlank() && newSource.username.isNotBlank() && newSource.password.isNotBlank()
            is PlaylistSource.M3u -> newSource.url.isNotBlank()
        }
        if (!valid) {
            error = "Lütfen tüm alanları doldurun"
            return
        }
        val updated = playlist.copy(name = name.ifBlank { playlist.name }, source = newSource)
        val credentialsChanged = newSource != source
        busy = true
        error = null
        scope.launch {
            val result = if (credentialsChanged) loadCatching { repo.verify(updated) } else Load.Ok(Unit)
            when (result) {
                is Load.Ok -> {
                    repo.store.update(updated)
                    if (credentialsChanged) repo.refresh(updated.id)
                    onDone()
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
            Text("Playlist düzenle", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text(
                "Bağlantı bilgileri değiştirilirse kaydetmeden önce doğrulanır.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            TvTextField(name, { name = it }, "Ad", leadingIcon = Icons.Rounded.Label)
            when (source) {
                is PlaylistSource.Xtream -> {
                    TvTextField(server, { server = it }, "Sunucu adresi", keyboardType = KeyboardType.Uri, leadingIcon = Icons.Rounded.Dns)
                    TvTextField(username, { username = it }, "Kullanıcı adı", leadingIcon = Icons.Rounded.Person)
                    TvTextField(password, { password = it }, "Şifre", password = true, leadingIcon = Icons.Rounded.Lock)
                }
                is PlaylistSource.M3u -> TvTextField(url, { url = it }, "M3U adresi (http://…)", keyboardType = KeyboardType.Uri, leadingIcon = Icons.Rounded.Link)
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (!busy) save() }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
                    Text(if (busy) "Kontrol ediliyor…" else "Kaydet")
                }
                OutlinedButton(onClick = { if (!busy) onDone() }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
                    Text("Vazgeç")
                }
            }
        }
    }
}
