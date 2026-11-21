package dev.iptv.player.ui

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
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import dev.iptv.player.data.PhoneMode
import dev.iptv.player.data.Profile
import dev.iptv.player.data.ProfileStore
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Setting
import kotlinx.coroutines.launch

private enum class Page(val label: String) {
    GENERAL("Genel"),
    PLAYER("Oynatıcı"),
    EPG("EPG"),
    PARENTAL("Ebeveyn kontrolü"),
    PROFILES("Profiller"),
    HIDDEN("Gizlenenler"),
    BACKUP("Yedekle / Geri yükle"),
    ABOUT("Hakkında"),
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
fun SettingsScreen(repo: Repository, onProfileSwitched: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.GENERAL) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Row(Modifier.fillMaxSize().padding(start = 40.dp, top = 24.dp, end = 40.dp)) {
        Column(Modifier.width(220.dp).fillMaxHeight()) {
            ScreenTitle("Ayarlar")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Page.entries.forEach { p ->
                    CategoryItem(
                        name = p.label,
                        count = null,
                        selected = p == page,
                        onClick = { page = p },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused || it.hasFocus) page = p }
                            .then(if (p == Page.GENERAL) Modifier.focusRequester(first) else Modifier),
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
                Page.BACKUP -> BackupPage(repo)
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

private val sectionOptions get() = Section.entries.map { it.name to it.label }
private val languageOptions = listOf("system" to "Sistem", "tr" to "Türkçe", "en" to "English")
private val aspectOptions = listOf("FIT" to "Sığdır", "FILL" to "Doldur", "ZOOM" to "Yakınlaştır")
private val audioLangOptions = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "ar" to "العربية", "ru" to "Русский")
private val subtitleLangOptions = listOf("" to "Kapalı") + audioLangOptions
private val externalOptions = listOf("" to "Yok", "vlc" to "VLC", "mx" to "MX Player")

@Composable
private fun GeneralPage(repo: Repository) {
    val language = rememberPref(repo, Setting.Language)
    val clock = rememberPref(repo, Setting.Clock24h)
    val last = rememberPref(repo, Setting.StartWithLastChannel)
    val section = rememberPref(repo, Setting.DefaultSection)
    val refresh = rememberPref(repo, Setting.AutoRefreshHours)
    val tmdb = rememberPref(repo, Setting.TmdbEnabled)
    PageList {
        item { PickerRow("Dil", languageOptions, language.value, language.set) }
        item { ToggleRow("24 saat biçimi", clock.value, clock.set) }
        item { ToggleRow("Açılışta son kanalı aç", last.value, last.set, subtitle = "Uygulama açılınca en son izlenen canlı kanal başlar") }
        item { PickerRow("Varsayılan bölüm", sectionOptions, section.value, section.set) }
        item {
            StepperRow(
                "Otomatik yenileme", refresh.value, 0..72, 6,
                { if (it == 0) "Kapalı" else "$it saatte bir" }, refresh.set,
                subtitle = "Playlist içeriğinin yenilenme sıklığı",
            )
        }
        item { ToggleRow("Film/dizi bilgilerini çevrimiçi getir", tmdb.value, tmdb.set) }
    }
}

@Composable
private fun PlayerPage(repo: Repository) {
    val aspect = rememberPref(repo, Setting.AspectMode)
    val audio = rememberPref(repo, Setting.PreferredAudioLanguage)
    val sub = rememberPref(repo, Setting.PreferredSubtitleLanguage)
    val subScale = rememberPref(repo, Setting.SubtitleScale)
    val buffer = rememberPref(repo, Setting.BufferSeconds)
    val ua = rememberPref(repo, Setting.UserAgent)
    val reconnect = rememberPref(repo, Setting.AutoReconnect)
    val external = rememberPref(repo, Setting.ExternalPlayer)
    val fps = rememberPref(repo, Setting.MatchFrameRate)
    PageList {
        item { PickerRow("Görüntü oranı", aspectOptions, aspect.value, aspect.set) }
        item { PickerRow("Ses dili", audioLangOptions, audio.value, audio.set) }
        item { PickerRow("Altyazı dili", subtitleLangOptions, sub.value, sub.set) }
        item { StepperRow("Altyazı boyutu", subScale.value, 50..200, 10, { "%$it" }, subScale.set) }
        item { StepperRow("Tampon süresi", buffer.value, 5..60, 5, { "$it sn" }, buffer.set) }
        item { TextSettingRow("User-Agent", ua.value, "IPTVPlayer/0.1", ua.set) }
        item { ToggleRow("Otomatik yeniden bağlan", reconnect.value, reconnect.set, subtitle = "Yayın koparsa kendiliğinden tekrar dener") }
        item { PickerRow("Harici oynatıcı", externalOptions, external.value, external.set, subtitle = "Seçilirse yayınlar o uygulamada açılır") }
        item { ToggleRow("Kare hızı eşleştirme", fps.value, fps.set, subtitle = "Ekran yenileme hızını videoya uydurur (destekleyen TV'lerde)") }
    }
}

@Composable
private fun EpgPage(repo: Repository) {
    val url = rememberPref(repo, Setting.EpgUrlOverride)
    val offset = rememberPref(repo, Setting.EpgOffsetMinutes)
    PageList {
        item { TextSettingRow("EPG adresi (XMLTV)", url.value, "Boş bırakılırsa playlist'in kendi EPG'si kullanılır", url.set) }
        item {
            StepperRow(
                "Saat kaydırma", offset.value, -720..720, 30,
                { m ->
                    val sign = if (m > 0) "+" else if (m < 0) "−" else ""
                    val a = kotlin.math.abs(m)
                    if (m == 0) "Yok" else "$sign${a / 60} sa" + if (a % 60 != 0) " ${a % 60} dk" else ""
                },
                offset.set,
                subtitle = "Program saatleri TV'de kayık görünüyorsa düzeltin",
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
                if (hasPin) "PIN'i değiştir" else "PIN belirle",
                subtitle = if (hasPin) "4 haneli PIN ayarlı" else "Kilitli kategoriler ve ayarlar için 4 haneli PIN",
                icon = Icons.Rounded.Lock,
                onClick = { guarded { task = PinTask.Create } },
            )
        }
        if (hasPin) {
            item { SettingRow("PIN'i kaldır", subtitle = "Kategori kilitleri de kaldırılır", icon = Icons.Rounded.Delete, onClick = { guarded { confirmRemove = true } }) }
            item {
                SettingRow(
                    if (ParentalSession.unlocked) "Kilitleri yeniden etkinleştir" else "Kilitli kategorileri bu oturumda aç",
                    icon = if (ParentalSession.unlocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    onClick = {
                        if (ParentalSession.unlocked) ParentalSession.unlocked = false else task = PinTask.Verify {}
                    },
                )
            }
        }
        item {
            ToggleRow(
                "Yetişkin içeriği gizle", hideAdult.value,
                { v -> guarded { hideAdult.set(v) } },
                subtitle = "XXX, Adult, 18+, Yetişkin gibi adlara sahip kanallar ve kategoriler listelenmez",
            )
        }
        item {
            Text(
                "Bir kategoriyi kilitlemek için Canlı TV'de kategorinin üzerinde OK tuşunu basılı tutun.",
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
            "PIN kaldırılsın mı?", "Kategori kilitleri de kaldırılır.", "Kaldır",
            onConfirm = { scope.launch { prefs.removePin() }; ParentalSession.unlocked = false; confirmRemove = false },
            onDismiss = { confirmRemove = false },
        )
    }
}

// ---- Hidden / locked -----------------------------------------------------------------------------

@Composable
private fun HiddenPage(repo: Repository) {
    val scope = rememberCoroutineScope()
    val rules by repo.prefs.rules.collectAsState(initial = dev.iptv.player.data.ContentRules())
    val labels by repo.prefs.labels.collectAsState(initial = emptyMap())
    val pinStored by repo.settings.flow(Setting.ParentalPin).collectAsState(initial = "")
    val hasPin = pinStored.isNotEmpty()
    var verify by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun label(key: String) = labels[key] ?: key.substringAfterLast('|')
    fun kind(key: String) = when (key.split('|').getOrNull(1)) { "LIVE" -> "Canlı"; "MOVIE" -> "Film"; "SERIES" -> "Dizi"; else -> "" }

    val channels = rules.hiddenChannels.toList().sortedBy { label(it) }
    val cats = rules.hiddenCategories.toList().sortedBy { label(it) }
    val locked = rules.lockedCategories.toList().sortedBy { label(it) }

    PageList {
        if (channels.isEmpty() && cats.isEmpty() && locked.isEmpty()) {
            item { Text("Gizlenmiş veya kilitlenmiş öğe yok.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(8.dp)) }
        }
        if (channels.isNotEmpty() || cats.isNotEmpty()) {
            item { SettingRow("Tümünü göster", subtitle = "${channels.size + cats.size} gizli öğe", icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.clearHidden() } }) }
        }
        items(channels, key = { "c$it" }) { k ->
            SettingRow(label(k), subtitle = "Gizli kanal · ${kind(k)} · göstermek için OK", icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.setChannelHiddenByKey(k, false) } })
        }
        items(cats, key = { "g$it" }) { k ->
            SettingRow(label(k), subtitle = "Gizli kategori · ${kind(k)} · göstermek için OK", icon = Icons.Rounded.Visibility, onClick = { scope.launch { repo.prefs.setCategoryHiddenByKey(k, false) } })
        }
        items(locked, key = { "l$it" }) { k ->
            SettingRow(
                label(k), subtitle = "Kilitli kategori · ${kind(k)} · kilidi kaldırmak için OK", icon = Icons.Rounded.Lock,
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
private fun BackupPage(repo: Repository) {
    var show by remember { mutableStateOf(false) }
    PageList {
        item {
            SettingRow(
                "Telefonla yedekle / geri yükle",
                subtitle = "QR kodu okutun; telefondan yedek dosyası indirin veya yükleyin",
                icon = Icons.Rounded.PhoneAndroid,
                onClick = { show = true },
            )
        }
        item {
            Text(
                "Yedek; playlistleri, ayarları ve favorileri içerir. Adres yalnızca pencere açıkken ve yerel ağda çalışır.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(8.dp),
            )
        }
    }
    if (show) PhoneConnectDialog(repo, PhoneMode.BACKUP, onDismiss = { show = false })
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Seyir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text("Sürüm $version", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Bu uygulama hiçbir içerik sağlamaz veya barındırmaz. Yalnızca kendi eklediğiniz, yasal olarak erişim hakkınız olan playlist'leri oynatır.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
        )
    }
}

// ---- Profiles ------------------------------------------------------------------------------------

@Composable
private fun ProfilesPage(repo: Repository, onSwitched: () -> Unit) {
    val profiles by repo.profiles.profiles.collectAsState(initial = emptyList())
    val active by ProfileStore.activeIdFlow.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    val firstRow = remember { FocusRequester() }
    BackHandler(enabled = editing != null) { editing = null }
    LaunchedEffect(editing == null) {
        if (editing == null) {
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
                    subtitle = listOfNotNull(if (p.id == active) "Etkin" else null, if (p.pin.isNotEmpty()) "PIN korumalı" else null).joinToString(" · ").ifEmpty { null },
                    onClick = { editing = p },
                    trailing = { AvatarBubble(p.name, p.color, 36.dp) },
                )
            }
            item {
                SettingRow("Profil ekle", icon = Icons.Rounded.Add, onClick = {
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
        val finalName = name.trim().ifEmpty { "Profil" }
        return if (isNew) ProfileStore.newProfile(finalName, color).copy(pin = pin) else original.copy(name = finalName, color = color, pin = pin)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(name.ifBlank { "?" }, color, 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(if (isNew) "Yeni profil" else "Profili düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
        }
        Box(Modifier.focusRequester(start)) { TvTextField(name, { name = it.take(24) }, "Profil adı") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStore.avatarColors.forEachIndexed { i, c ->
                ColorChip(Color(c), selected = i == color, description = "Renk ${i + 1}") { color = i }
            }
        }
        SettingRow(
            if (pin.isEmpty()) "Profil PIN'i belirle" else "Profil PIN'ini kaldır",
            subtitle = "İsteğe bağlı; profil seçilirken sorulur",
            icon = Icons.Rounded.Lock,
            onClick = { if (pin.isEmpty()) createPin = true else pin = "" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Kaydet", { scope.launch { repo.profiles.save(build()); onDone() } }, icon = Icons.Rounded.Check, primary = true)
            if (!isNew && !isActive) TvButton("Bu profili kullan", { scope.launch { repo.profiles.save(build()); onUse(original) } })
            if (!isNew && original.id != ProfileStore.DEFAULT_ID) TvButton("Sil", { confirmDelete = true }, icon = Icons.Rounded.Delete, destructive = true)
            TvButton("Vazgeç", onDone)
        }
    }
    if (createPin) PinCreateDialog(onCreated = { pin = dev.iptv.player.data.ParentalFilter.hashPin(it); createPin = false }, onDismiss = { createPin = false })
    if (confirmDelete) {
        ConfirmDialog(
            "Profil silinsin mi?", "Bu profilin favorileri ve izleme konumları artık görünmez.", "Sil",
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
