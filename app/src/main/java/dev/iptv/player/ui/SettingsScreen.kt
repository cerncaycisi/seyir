package dev.iptv.player.ui

import dev.iptv.player.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.iptv.player.data.ContentPrefs
import dev.iptv.player.data.ParentalSession
import dev.iptv.player.data.Profile
import dev.iptv.player.data.ProfileStore
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Setting
import kotlinx.coroutines.launch

private enum class Page(val labelRes: Int) {
    GENERAL(R.string.ui_general),
    PLAYER(R.string.ui_player),
    EPG(R.string.ui_epg),
    PARENTAL(R.string.ui_parental),
    PROFILES(R.string.ui_profiles),
    HIDDEN(R.string.ui_hidden),
    ABOUT(R.string.ui_about),
}

/** Editable view of one persisted setting; writes are optimistic so quick D-pad repeats stay in step. */
class Pref<T>(val value: T, val set: (T) -> Unit)

@Composable
fun <T> rememberPref(repo: Repository, setting: Setting<T>): Pref<T> {
    val scope = rememberCoroutineScope()
    val stored by repo.settings.flow(setting).collectAsState(initial = setting.default)
    var local by remember(setting) { mutableStateOf(stored) }
    LaunchedEffect(stored) { local = stored }
    return Pref(local) { v ->
        local = v
        scope.launch { repo.settings.set(setting, v) }
    }
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun SettingsScreen(repo: Repository, onProfileSwitched: () -> Unit) {
    val uiContext = LocalContext.current
    var page by rememberSaveable { mutableStateOf(Page.GENERAL) }
    val menuFocus = remember { Page.entries.map { FocusRequester() } }
    val first = menuFocus.first()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Row(Modifier.fillMaxSize().padding(start = 40.dp, top = 24.dp, end = 40.dp)) {
        Column(Modifier.width(220.dp).fillMaxHeight()) {
            ScreenTitle(uiContext.getString(R.string.ui_settings))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Page.entries.forEach { p ->
                    CategoryItem(
                        name = uiContext.getString(p.labelRes),
                        count = null,
                        selected = p == page,
                        onClick = { page = p },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused || it.hasFocus) page = p }
                            .focusRequester(menuFocus[p.ordinal])
                            .focusProperties {
                                up = menuFocus.getOrNull(p.ordinal - 1) ?: FocusRequester.Cancel
                                down = menuFocus.getOrNull(p.ordinal + 1) ?: FocusRequester.Cancel
                            },
                    )
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                Page.GENERAL -> GeneralPage(repo)
                Page.PLAYER -> PlayerPage(repo)
                Page.EPG -> EpgPage(repo)
                Page.PARENTAL -> ParentalPage(repo)
                Page.PROFILES -> ProfilesPage(repo, onProfileSwitched)
                Page.HIDDEN -> HiddenPage(repo)
                Page.ABOUT -> AboutPage()
            }
        }
    }
}

@Composable
private fun PageList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
        content = content,
    )
}

private fun sectionOptions(uiContext: android.content.Context) = Section.entries.map { it.name to uiContext.getString(it.labelRes) }
private fun languageOptions(uiContext: android.content.Context) = listOf("system" to uiContext.getString(R.string.ui_system), "tr" to "Türkçe", "en" to "English")
private fun aspectOptions(uiContext: android.content.Context) = listOf("FIT" to uiContext.getString(R.string.ui_fit), "FILL" to uiContext.getString(R.string.ui_fill), "ZOOM" to uiContext.getString(R.string.ui_zoom))
private val audioLangOptions = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "ar" to "العربية", "ru" to "Русский")
private fun subtitleLangOptions(uiContext: android.content.Context) = listOf("" to uiContext.getString(R.string.ui_off)) + audioLangOptions
private fun externalOptions(uiContext: android.content.Context) = listOf("" to uiContext.getString(R.string.ui_none), "vlc" to "VLC", "mx" to "MX Player")

@Composable
private fun GeneralPage(repo: Repository) {
    val uiContext = LocalContext.current
    val language = rememberPref(repo, Setting.Language)
    val clock = rememberPref(repo, Setting.Clock24h)
    val last = rememberPref(repo, Setting.StartWithLastChannel)
    val section = rememberPref(repo, Setting.DefaultSection)
    val refresh = rememberPref(repo, Setting.AutoRefreshHours)
    val tmdb = rememberPref(repo, Setting.TmdbEnabled)
    PageList {
        item { PickerRow(uiContext.getString(R.string.ui_language), languageOptions(uiContext), language.value, language.set) }
        item { ToggleRow(uiContext.getString(R.string.ui_clock_24), clock.value, clock.set) }
        item { ToggleRow(uiContext.getString(R.string.ui_start_last), last.value, last.set, subtitle = uiContext.getString(R.string.ui_start_last_hint)) }
        item { PickerRow(uiContext.getString(R.string.ui_default_section), sectionOptions(uiContext), section.value, section.set) }
        item {
            StepperRow(
                uiContext.getString(R.string.ui_auto_refresh), refresh.value, 0..72, 6,
                { if (it == 0) uiContext.getString(R.string.ui_off) else uiContext.getString(R.string.ui_refresh_hours, it) }, refresh.set,
                subtitle = uiContext.getString(R.string.ui_refresh_frequency),
            )
        }
        item { ToggleRow(uiContext.getString(R.string.ui_online_metadata), tmdb.value, tmdb.set) }
    }
}

@Composable
private fun PlayerPage(repo: Repository) {
    val uiContext = LocalContext.current
    val aspect = rememberPref(repo, Setting.AspectMode)
    val audio = rememberPref(repo, Setting.PreferredAudioLanguage)
    val sub = rememberPref(repo, Setting.PreferredSubtitleLanguage)
    val subScale = rememberPref(repo, Setting.SubtitleScale)
    val subStyle = rememberPref(repo, Setting.SubtitleStyle)
    val buffer = rememberPref(repo, Setting.BufferSeconds)
    val ua = rememberPref(repo, Setting.UserAgent)
    val reconnect = rememberPref(repo, Setting.AutoReconnect)
    val external = rememberPref(repo, Setting.ExternalPlayer)
    val fps = rememberPref(repo, Setting.MatchFrameRate)
    PageList {
        item { PickerRow(uiContext.getString(R.string.ui_aspect), aspectOptions(uiContext), aspect.value, aspect.set) }
        item { PickerRow(uiContext.getString(R.string.ui_audio_language), audioLangOptions, audio.value, audio.set) }
        item { PickerRow(uiContext.getString(R.string.ui_subtitle_language), subtitleLangOptions(uiContext), sub.value, sub.set) }
        item { PickerRow(uiContext.getString(R.string.sub_style_title), dev.iptv.player.player.SubtitleStyle.entries.map { it.key to uiContext.getString(it.labelRes) }, subStyle.value, subStyle.set) }
        item { dev.iptv.player.player.SubtitlePreview(dev.iptv.player.player.SubtitleStyle.fromKey(subStyle.value), subScale.value) }
        item { StepperRow(uiContext.getString(R.string.ui_subtitle_size), subScale.value, 50..200, 10, { uiContext.getString(R.string.ui_percentage, it) }, subScale.set) }
        item { StepperRow(uiContext.getString(R.string.ui_buffer), buffer.value, 5..60, 5, { uiContext.getString(R.string.ui_seconds, it) }, buffer.set) }
        item { TextSettingRow("User-Agent", ua.value, "IPTVPlayer/0.1", ua.set) }
        item { ToggleRow(uiContext.getString(R.string.ui_reconnect), reconnect.value, reconnect.set, subtitle = uiContext.getString(R.string.ui_reconnect_hint)) }
        item { PickerRow(uiContext.getString(R.string.ui_external_player), externalOptions(uiContext), external.value, external.set, subtitle = uiContext.getString(R.string.ui_external_hint)) }
        item { ToggleRow(uiContext.getString(R.string.ui_frame_rate), fps.value, fps.set, subtitle = uiContext.getString(R.string.ui_frame_rate_hint)) }
    }
}

@Composable
private fun EpgPage(repo: Repository) {
    val uiContext = LocalContext.current
    val url = rememberPref(repo, Setting.EpgUrlOverride)
    val offset = rememberPref(repo, Setting.EpgOffsetMinutes)
    PageList {
        item { TextSettingRow(uiContext.getString(R.string.ui_epg_url), url.value, uiContext.getString(R.string.ui_epg_default), url.set) }
        item {
            StepperRow(
                uiContext.getString(R.string.ui_time_offset), offset.value, -720..720, 30,
                { m ->
                    val sign = if (m > 0) "+" else if (m < 0) "−" else ""
                    val a = kotlin.math.abs(m)
                    if (m == 0) uiContext.getString(R.string.ui_none) else uiContext.getString(R.string.ui_offset_hours, sign, a / 60) + if (a % 60 != 0) " " + uiContext.getString(R.string.ui_offset_minutes, a % 60) else ""
                },
                offset.set,
                subtitle = uiContext.getString(R.string.ui_time_offset_hint),
            )
        }
    }
}

@Composable
private fun TextSettingRow(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
        TvTextField(text, { text = it; onChange(it) }, placeholder)
    }
}

// ---- Parental ------------------------------------------------------------------------------------

private sealed interface PinTask {
    data object Create : PinTask
    data class Verify(val then: () -> Unit) : PinTask
}

@Composable
private fun ParentalPage(repo: Repository) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs: ContentPrefs = repo.prefs
    val pinStored by repo.settings.flow(Setting.ParentalPin).collectAsState(initial = "")
    val hasPin = pinStored.isNotEmpty()
    val hideAdult = rememberPref(repo, Setting.HideAdult)
    var task by remember { mutableStateOf<PinTask?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    // Changing anything protected needs the PIN once per session.
    fun guarded(action: () -> Unit) {
        if (!hasPin || ParentalSession.unlocked) action() else task = PinTask.Verify(action)
    }

    PageList {
        item {
            SettingRow(
                if (hasPin) uiContext.getString(R.string.ui_pin_change) else uiContext.getString(R.string.ui_pin_set),
                subtitle = if (hasPin) uiContext.getString(R.string.ui_pin_set_hint) else uiContext.getString(R.string.ui_pin_unset_hint),
                icon = Icons.Rounded.Lock,
                onClick = { guarded { task = PinTask.Create } },
            )
        }
        if (hasPin) {
            item { SettingRow(uiContext.getString(R.string.ui_pin_remove), subtitle = uiContext.getString(R.string.ui_pin_unlock_hint), icon = Icons.Rounded.Delete, onClick = { guarded { confirmRemove = true } }) }
            item {
                SettingRow(
                    if (ParentalSession.unlocked) uiContext.getString(R.string.ui_relock) else uiContext.getString(R.string.ui_unlock_session),
                    icon = if (ParentalSession.unlocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    onClick = {
                        if (ParentalSession.unlocked) ParentalSession.unlocked = false else task = PinTask.Verify {}
                    },
                )
            }
        }
        item {
            ToggleRow(
                uiContext.getString(R.string.ui_hide_adult), hideAdult.value,
                { v -> guarded { hideAdult.set(v) } },
                subtitle = uiContext.getString(R.string.ui_hide_adult_hint),
            )
        }
        item {
            Text(
                uiContext.getString(R.string.ui_lock_category_hint),
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(8.dp),
            )
        }
    }

    when (val t = task) {
        PinTask.Create -> PinCreateDialog(
            onCreated = { pin ->
                scope.launch { prefs.setPin(pin) }
                ParentalSession.unlocked = true
                task = null
            },
            onDismiss = { task = null },
        )
        is PinTask.Verify -> PinVerifyDialog(prefs, onVerified = { task = null; t.then() }, onDismiss = { task = null })
        null -> Unit
    }
    if (confirmRemove) {
        ConfirmDialog(
            uiContext.getString(R.string.ui_pin_remove_confirm), uiContext.getString(R.string.ui_pin_remove_message), uiContext.getString(R.string.ui_remove),
            onConfirm = { scope.launch { prefs.removePin() }; ParentalSession.unlocked = false; confirmRemove = false },
            onDismiss = { confirmRemove = false },
        )
    }
}

// ---- Hidden / locked -----------------------------------------------------------------------------

@Composable
private fun HiddenPage(repo: Repository) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by repo.prefs.rules.collectAsState(initial = dev.iptv.player.data.ContentRules())
    val labels by repo.prefs.labels.collectAsState(initial = emptyMap())
    val pinStored by repo.settings.flow(Setting.ParentalPin).collectAsState(initial = "")
    val hasPin = pinStored.isNotEmpty()
    var verify by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun label(key: String) = labels[key] ?: key.substringAfterLast('|')
    fun kind(key: String) = when (key.split('|').getOrNull(1)) { "LIVE" -> uiContext.getString(R.string.ui_live_kind); "MOVIE" -> uiContext.getString(R.string.ui_movie_kind); "SERIES" -> uiContext.getString(R.string.ui_series_kind); else -> "" }

    val channels = rules.hiddenChannels.toList().sortedBy { label(it) }
    val cats = rules.hiddenCategories.toList().sortedBy { label(it) }
    val locked = rules.lockedCategories.toList().sortedBy { label(it) }

    PageList {
        if (channels.isEmpty() && cats.isEmpty() && locked.isEmpty()) {
            item { Text(uiContext.getString(R.string.ui_hidden_empty), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(8.dp)) }
        }
        if (channels.isNotEmpty() || cats.isNotEmpty()) {
            item { SettingRow(uiContext.getString(R.string.ui_show_all), subtitle = uiContext.getString(R.string.ui_hidden_count, channels.size + cats.size), icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.clearHidden() } }) }
        }
        items(channels, key = { "c$it" }) { k ->
            SettingRow(label(k), subtitle = uiContext.getString(R.string.ui_hidden_channel, kind(k)), icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.setChannelHiddenByKey(k, false) } })
        }
        items(cats, key = { "g$it" }) { k ->
            SettingRow(label(k), subtitle = uiContext.getString(R.string.ui_hidden_category, kind(k)), icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.setCategoryHiddenByKey(k, false) } })
        }
        items(locked, key = { "l$it" }) { k ->
            SettingRow(
                label(k), subtitle = uiContext.getString(R.string.ui_locked_category, kind(k)), icon = Icons.Rounded.Lock,
                onClick = {
                    val unlock = { scope.launch { repo.prefs.setCategoryLockedByKey(k, false) }; Unit }
                    if (hasPin && !ParentalSession.unlocked) verify = unlock else unlock()
                },
            )
        }
    }
    verify?.let { action ->
        PinVerifyDialog(repo.prefs, onVerified = { verify = null; action() }, onDismiss = { verify = null })
    }
}

// ---- Backup / About ------------------------------------------------------------------------------


@Composable
private fun AboutPage() {
    val uiContext = LocalContext.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(uiContext.getString(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(uiContext.getString(R.string.ui_version, version), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            uiContext.getString(R.string.ui_content_disclaimer),
            style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
        )
    }
}

// ---- Profiles ------------------------------------------------------------------------------------

@Composable
private fun ProfilesPage(repo: Repository, onSwitched: () -> Unit) {
    val uiContext = LocalContext.current
    val profiles by repo.profiles.profiles.collectAsState(initial = emptyList())
    val active by ProfileStore.activeIdFlow.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    val firstRow = remember { FocusRequester() }
    var returningFromEditor by remember { mutableStateOf(false) }
    BackHandler(enabled = editing != null) { editing = null }
    LaunchedEffect(editing) {
        if (editing != null) returningFromEditor = true
        else if (returningFromEditor) {
            returningFromEditor = false
            withFrameNanos { }
            // Returning from the editor: put focus back on the list.
            runCatching { firstRow.requestFocus() }
        }
    }

    val current = editing
    if (current == null) {
        PageList {
            items(profiles, key = { it.id }) { p ->
                SettingRow(
                    p.name,
                    modifier = if (p.id == profiles.firstOrNull()?.id) Modifier.focusRequester(firstRow) else Modifier,
                    subtitle = listOfNotNull(if (p.id == active) uiContext.getString(R.string.ui_selected) else null, if (p.pin.isNotEmpty()) uiContext.getString(R.string.ui_pin_protected) else null).joinToString(" · ").ifEmpty { null },
                    onClick = { editing = p },
                    trailing = { AvatarBubble(p.name, p.color, 36.dp) },
                )
            }
            item {
                SettingRow(uiContext.getString(R.string.ui_add_profile), icon = Icons.Rounded.Add, onClick = {
                    editing = Profile("", "", profiles.size % ProfileStore.avatarColors.size)
                })
            }
        }
    } else {
        ProfileEditor(repo, current, isActive = current.id == active, onDone = { editing = null }, onUse = { p -> ProfileStore.setActive(p.id); onSwitched() })
    }
}

@Composable
private fun ProfileEditor(repo: Repository, original: Profile, isActive: Boolean, onDone: () -> Unit, onUse: (Profile) -> Unit) {
    val uiContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = original.id.isEmpty()
    var name by remember { mutableStateOf(original.name) }
    var color by remember { mutableStateOf(original.color) }
    var pin by remember { mutableStateOf(original.pin) }
    var createPin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { start.requestFocus() }
    }

    fun build(): Profile {
        val finalName = name.trim().ifEmpty { uiContext.getString(R.string.ui_profile) }
        return if (isNew) ProfileStore.newProfile(finalName, color).copy(pin = pin) else original.copy(name = finalName, color = color, pin = pin)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(name.ifBlank { "?" }, color, 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(if (isNew) uiContext.getString(R.string.ui_new_profile) else uiContext.getString(R.string.ui_edit_profile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
        }
        Box(Modifier.focusRequester(start)) { TvTextField(name, { name = it.take(24) }, uiContext.getString(R.string.ui_profile_name)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStore.avatarColors.forEachIndexed { i, c ->
                ColorChip(Color(c), selected = i == color, description = uiContext.getString(R.string.ui_color, i + 1)) { color = i }
            }
        }
        SettingRow(
            if (pin.isEmpty()) uiContext.getString(R.string.ui_profile_pin_set) else uiContext.getString(R.string.ui_profile_pin_remove),
            subtitle = uiContext.getString(R.string.ui_profile_pin_hint),
            icon = Icons.Rounded.Lock,
            onClick = { if (pin.isEmpty()) createPin = true else pin = "" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(uiContext.getString(R.string.ui_save), { scope.launch { repo.profiles.save(build()); onDone() } }, icon = Icons.Rounded.Check, primary = true)
            if (!isNew && !isActive) TvButton(uiContext.getString(R.string.ui_use_profile), { scope.launch { repo.profiles.save(build()); onUse(original) } })
            if (!isNew && original.id != ProfileStore.DEFAULT_ID) TvButton(uiContext.getString(R.string.ui_delete), { confirmDelete = true }, icon = Icons.Rounded.Delete, destructive = true)
            TvButton(uiContext.getString(R.string.ui_cancel), onDone)
        }
    }
    if (createPin) PinCreateDialog(onCreated = { pin = dev.iptv.player.data.ParentalFilter.hashPin(it); createPin = false }, onDismiss = { createPin = false })
    if (confirmDelete) {
        ConfirmDialog(
            uiContext.getString(R.string.ui_profile_delete), uiContext.getString(R.string.ui_profile_delete_hint), uiContext.getString(R.string.ui_delete),
            onConfirm = { scope.launch { repo.profiles.delete(original.id); onDone() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ColorChip(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp).pointerClick(onClick).semantics { contentDescription = description },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(if (selected) 3.dp else 0.dp, Color.White), shape = CircleShape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Palette.Primary), shape = CircleShape),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) { Box(Modifier.size(34.dp)) }
}
