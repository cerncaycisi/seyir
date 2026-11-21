package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.R

/** Public documents; update if the repository location changes. */
object Links {
    const val REPO = "https://github.com/OWNER/seyir"
    const val PRIVACY = "$REPO/blob/main/docs/PRIVACY.md"
    const val DISCLAIMER = "$REPO/blob/main/docs/DISCLAIMER.md"
}

/** First-run legal disclaimer. Designed for a ~960x540dp canvas; "accept" is focused by default. */
@Composable
fun OnboardingScreen(onAccept: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(56.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.onb_headline),
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onb_body, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.background(Palette.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Point(stringResource(R.string.onb_point_own))
                Point(stringResource(R.string.onb_point_resp))
                Point(stringResource(R.string.onb_point_privacy) + " " + Links.PRIVACY)
            }
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAccept, modifier = Modifier.focusRequester(focus)) {
                Text(stringResource(R.string.onb_accept), modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).background(Palette.Accent, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}
