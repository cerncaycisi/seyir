package dev.iptv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.R

private data class OssNotice(val name: String, val license: String)

/** Hand-maintained list of direct dependencies (update when build.gradle.kts changes). */
private val ossNotices = listOf(
    OssNotice("AndroidX (Activity, Lifecycle, Navigation, DataStore)", "Apache-2.0"),
    OssNotice("Jetpack Compose & Compose for TV (tv-material)", "Apache-2.0"),
    OssNotice("Material Icons Extended", "Apache-2.0"),
    OssNotice("AndroidX Media3 (ExoPlayer, HLS, UI)", "Apache-2.0"),
    OssNotice("OkHttp / Okio (Square)", "Apache-2.0"),
    OssNotice("Coil (coil-kt)", "Apache-2.0"),
    OssNotice("Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization", "Apache-2.0"),
    OssNotice("AndroidX Room, WorkManager, TV Provider, SplashScreen", "Apache-2.0"),
    OssNotice("Jellyfin media3-ffmpeg-decoder (FFmpeg)", "GPL-3.0"),
    OssNotice("ZXing core", "Apache-2.0"),
    OssNotice("NanoHTTPD", "BSD-3-Clause"),
)

/** About / licenses. Route `about`; Settings links here. D-pad scrolls the license list. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    @Suppress("DEPRECATION")
    val versionCode = info?.versionCode ?: 0
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandMark(56.dp)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.about_version, info?.versionName ?: "?", versionCode),
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.about_tagline), color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Heading(stringResource(R.string.about_links))
            LinkLine(stringResource(R.string.about_source), Links.REPO)
            LinkLine(stringResource(R.string.about_privacy), Links.PRIVACY)
            LinkLine(stringResource(R.string.about_disclaimer), Links.DISCLAIMER)
            Spacer(Modifier.height(8.dp))
            Heading(stringResource(R.string.about_attribution))
            Text(stringResource(R.string.about_tmdb), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_opensubtitles), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.fillMaxSize()) {
            Heading(stringResource(R.string.about_licenses))
            Text(
                stringResource(R.string.about_license_notice),
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ossNotices.size) { i ->
                    val n = ossNotices[i]
                    Surface(
                        onClick = {},
                        modifier = if (i == 0) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Palette.Surface,
                            focusedContainerColor = Palette.SurfaceHigh,
                        ),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(n.name, color = Palette.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(n.license, color = Palette.Accent, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, color = Palette.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun LinkLine(label: String, url: String) {
    Column {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(url, color = Palette.Primary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
