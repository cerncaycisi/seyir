package dev.iptv.player.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Use the app language for background notifications and service errors, too. */
suspend fun SettingsStore.localizedContext(context: Context): Context {
    val language = get(Setting.Language)
    if (language != "tr" && language != "en") return context
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(Locale.forLanguageTag(language)))
    return context.createConfigurationContext(config)
}
