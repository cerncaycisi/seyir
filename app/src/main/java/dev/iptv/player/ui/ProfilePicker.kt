package dev.iptv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.Profile
import dev.iptv.player.data.ProfileStore

/** "Kim izliyor?" screen shown at start when there is more than one profile. */
@Composable
fun ProfilePicker(profiles: List<Profile>, onPicked: () -> Unit) {
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    val first = remember { FocusRequester() }
    val lastActive = ProfileStore.activeProfileId
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun pick(p: Profile) {
        ProfileStore.setActive(p.id)
        ProfileStore.pickedThisLaunch = true
        onPicked()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(44.dp)
        Spacer(Modifier.height(16.dp))
        Text("Kim izliyor?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            profiles.forEachIndexed { i, p ->
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    onClick = { if (p.pin.isNotEmpty()) pinFor = p else pick(p) },
                    modifier = Modifier
                        .width(128.dp)
                        .pointerClick({ if (p.pin.isNotEmpty()) pinFor = p else pick(p) })
                        .then(if (p.id == lastActive || (i == 0 && profiles.none { it.id == lastActive })) Modifier.focusRequester(first) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Palette.SurfaceHigh,
                        contentColor = Palette.TextMuted,
                        focusedContentColor = Color.White,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box { AvatarBubble(p.name, p.color, 72.dp) }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p.pin.isNotEmpty()) {
                                Icon(Icons.Rounded.Lock, contentDescription = "PIN korumalı", modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    pinFor?.let { p ->
        PinCheckDialog(p.pin, "${p.name} için PIN", onVerified = { pinFor = null; pick(p) }, onDismiss = { pinFor = null })
    }
}
