package dev.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import dev.iptv.player.data.ContentRules
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.ParentalSession
import dev.iptv.player.data.Setting
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Rounded.Home),
    LIVE("Canlı TV", Icons.Rounded.LiveTv),
    MOVIES("Filmler", Icons.Rounded.Movie),
    SERIES("Diziler", Icons.Rounded.VideoLibrary),
    FAVORITES("Favoriler", Icons.Rounded.Favorite),
    SEARCH("Ara", Icons.Rounded.Search),
}

// Without an explicit target, focus is lost after returning from the player and the first key
// lands wherever Compose picks, which feels random on a remote.
class FocusRestore(val itemId: String?, val initial: Boolean, val done: () -> Unit)
class HomeActions(
    val favorites: Set<String>,
    val restore: FocusRestore,
    val open: (List<Stream>, Int) -> Unit,
    val toggleFavorite: (Stream) -> Unit,
    /** Resume a continue-watching item directly (episodes go to their series page). */
    val resume: (Stream) -> Unit = {},
    /** Watch progress 0..1 keyed by favoriteKey; used for progress bars and watched checks on cards. */
    val progress: Map<String, Float> = emptyMap(),
    /** Hidden / locked / adult filtering state (see ContentRules). */
    val rules: ContentRules = ContentRules(),
    /** Long-press on a channel or poster: opens the action card. [categoryName] is null when the row is not a real category. */
    val longPress: (Stream, String?) -> Unit = { s, _ -> },
    /** Long-press on a category entry. */
    val categoryLongPress: (ContentType, String, String) -> Unit = { _, _, _ -> },
    /** Runs the action once the parental PIN was entered (or immediately if already unlocked / no PIN). */
    val requireUnlock: (() -> Unit) -> Unit = { it() },
)

private sealed interface ActionTarget {
    data class Channel(val stream: Stream, val categoryName: String?) : ActionTarget
    data class Category(val type: ContentType, val id: String, val name: String) : ActionTarget
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    repo: Repository,
    playlist: Playlist,
    onPlay: (List<Stream>, Int) -> Unit,
    onSeries: (Stream) -> Unit,
    onMovie: (Stream) -> Unit,
    onPlaylists: () -> Unit,
    onSettings: () -> Unit = {},
    initialSection: String? = null,
) {
    var section by rememberSaveable { mutableStateOf(Section.entries.firstOrNull { it.name == initialSection } ?: Section.HOME) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocus by rememberSaveable { mutableStateOf(true) }
    val navFocus = remember { Section.entries.map { FocusRequester() } }
    val scope = rememberCoroutineScope()
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())
    val history by repo.history.entries.collectAsState(initial = emptyList())
    val progress = remember(history) {
        history.filter { it.type != ContentType.LIVE }.associate { it.favKey to (if (it.finished) 1f else it.fraction) }
    }
    val baseRules by repo.prefs.rules.collectAsState(initial = ContentRules())
    val rules = baseRules.copy(unlocked = ParentalSession.unlocked)
    val hasPin by repo.settings.flow(Setting.ParentalPin).collectAsState(initial = "")
    var target by remember { mutableStateOf<ActionTarget?>(null) }
    var pendingUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }

    val actions = HomeActions(
        favorites = favorites,
        restore = FocusRestore(restoreId, initialFocus) {
            restoreId = null
            initialFocus = false
        },
        open = { items, index ->
            val s = items[index]
            restoreId = s.id
            val playable = items.filter { it.url.isNotEmpty() }
            when {
                s.type == ContentType.SERIES && s.url.isEmpty() -> onSeries(s)
                s.type == ContentType.MOVIE -> onMovie(s)
                else -> onPlay(playable, playable.indexOf(s))
            }
        },
        toggleFavorite = { s -> scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, s)) } },
        resume = { s ->
            restoreId = s.id
            val series = s.seriesId
            if (series != null) onSeries(s.copy(id = series)) else onPlay(listOf(s), 0)
        },
        progress = progress,
        rules = rules,
        longPress = { s, c -> target = ActionTarget.Channel(s, c) },
        categoryLongPress = { t, id, name -> target = ActionTarget.Category(t, id, name) },
        requireUnlock = { a -> if (rules.unlocked || hasPin.isEmpty()) a() else pendingUnlock = a },
    )

    ModalNavigationDrawer(
        scrimBrush = Brush.horizontalGradient(listOf(Palette.Background.copy(alpha = 0.92f), Color.Transparent)),
        drawerContent = { value ->
            val open = value == DrawerValue.Open
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(if (open) Palette.Surface else Palette.Background)
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    // Entering the menu lands on the current section instead of the nearest item.
                    .focusProperties { enter = { navFocus[section.ordinal] } },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.padding(start = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(32.dp)
                    if (open) {
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Seyir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
                Section.entries.forEach { s ->
                    NavigationDrawerItem(
                        selected = s == section,
                        onClick = { section = s },
                        leadingContent = { Icon(s.icon, contentDescription = null) },
                        modifier = Modifier.focusRequester(navFocus[s.ordinal]).pointerClick({ section = s }),
                    ) { Text(s.label) }
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    selected = false,
                    onClick = onSettings,
                    leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = "Ayarlar") },
                    modifier = Modifier.pointerClick(onSettings),
                ) { Text("Ayarlar") }
                NavigationDrawerItem(
                    selected = false,
                    onClick = {
                        scope.launch { repo.refresh(playlist.id) }
                        onPlaylists()
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Playlistler") },
                    modifier = Modifier.pointerClick({ scope.launch { repo.refresh(playlist.id) }; onPlaylists() }),
                ) { Text("Playlistler") }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) {
            key(section, playlist.id) {
                when (section) {
                    Section.HOME -> HomeSection(repo, playlist, actions)
                    Section.LIVE -> LiveSection(repo, playlist, actions)
                    Section.MOVIES -> VodSection(repo, playlist, ContentType.MOVIE, actions)
                    Section.SERIES -> VodSection(repo, playlist, ContentType.SERIES, actions)
                    Section.FAVORITES -> FavoritesSection(repo, playlist, actions)
                    Section.SEARCH -> SearchSection(repo, playlist, actions)
                }
            }
        }
    }

    HomeOverlays(repo, playlist, target, hasPin.isNotEmpty(), rules, favorites, { target = null }, pendingUnlock, { pendingUnlock = null })
}

@Composable
private fun HomeOverlays(
    repo: Repository,
    playlist: Playlist,
    target: ActionTarget?,
    hasPin: Boolean,
    rules: ContentRules,
    favorites: Set<String>,
    dismiss: () -> Unit,
    pendingUnlock: (() -> Unit)?,
    clearUnlock: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val noPin = "Kilitlemek için önce Ayarlar > Ebeveyn kontrolü'nden PIN belirleyin"
    when (target) {
        is ActionTarget.Channel -> {
            val s = target.stream
            val fav = favoriteKey(playlist, s) in favorites
            val cat = target.categoryName
            val locked = rules.isCategoryMarkedLocked(playlist, s.type, s.categoryId)
            val actions = buildList {
                add(CardAction(if (fav) "Favoriden çıkar" else "Favori ekle", Icons.Rounded.Favorite) {
                    scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, s)) }
                    dismiss()
                })
                add(CardAction("Gizle", Icons.Rounded.VisibilityOff) {
                    scope.launch { repo.prefs.setChannelHidden(playlist, s, true) }
                    dismiss()
                })
                if (cat != null) {
                    if (!hasPin) {
                        add(CardAction(noPin, enabled = false) {})
                    } else {
                        add(CardAction(if (locked) "Kategori kilidini kaldır" else "Kategoriyi kilitle", Icons.Rounded.Lock) {
                            scope.launch { repo.prefs.setCategoryLocked(playlist, s.type, s.categoryId, cat, !locked) }
                            dismiss()
                        })
                    }
                }
            }
            ActionCard(s.name, null, actions, onDismiss = dismiss)
        }
        is ActionTarget.Category -> {
            val locked = rules.isCategoryMarkedLocked(playlist, target.type, target.id)
            val actions = buildList {
                add(CardAction("Kategoriyi gizle", Icons.Rounded.VisibilityOff) {
                    scope.launch { repo.prefs.setCategoryHidden(playlist, target.type, target.id, target.name, true) }
                    dismiss()
                })
                if (!hasPin) {
                    add(CardAction(noPin, enabled = false) {})
                } else {
                    add(CardAction(if (locked) "Kilidi kaldır" else "Kategoriyi kilitle", Icons.Rounded.Lock) {
                        scope.launch { repo.prefs.setCategoryLocked(playlist, target.type, target.id, target.name, !locked) }
                        dismiss()
                    })
                }
            }
            ActionCard(target.name, null, actions, onDismiss = dismiss)
        }
        null -> Unit
    }
    if (pendingUnlock != null) {
        PinVerifyDialog(repo.prefs, onVerified = { val a = pendingUnlock; clearUnlock(); a() }, onDismiss = clearUnlock)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Text)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted) }
        Spacer(Modifier.height(12.dp))
    }
}
