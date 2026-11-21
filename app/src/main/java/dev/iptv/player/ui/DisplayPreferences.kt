package dev.iptv.player.ui

import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.iptv.player.data.Setting
import dev.iptv.player.data.SettingsStore
import java.util.Locale

val LocalClock24h = staticCompositionLocalOf { true }

/** Apply the selected language without recreating the activity or losing playback/navigation. */
@Composable
fun DisplayPreferences(settings: SettingsStore, content: @Composable () -> Unit) {
    val language by settings.flow(Setting.Language).collectAsState(initial = null)
    val clock24h by settings.flow(Setting.Clock24h).collectAsState(initial = true)
    val selected = language ?: return
    val base = LocalContext.current
    val system = LocalConfiguration.current
    val configuration = remember(system, selected) {
        Configuration(system).apply {
            if (selected == "tr" || selected == "en") {
                setLocales(LocaleList(Locale.forLanguageTag(selected)))
            }
        }
    }
    // Keep an Activity in the base-context chain for PiP, external players and window controls.
    val localized = remember(base, configuration) {
        ContextThemeWrapper(base, 0).apply { applyOverrideConfiguration(configuration) }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        LocalClock24h provides clock24h,
        content = content,
    )
}
