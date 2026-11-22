package dev.iptv.player.ui

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import dev.iptv.player.R

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.WatchLater
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
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
import dev.iptv.player.data.lastEpisodePerSeries
import kotlinx.coroutines.launch

enum class Section(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.play_section_home, Icons.Rounded.Home),
    LIVE(R.string.play_section_live, Icons.Rounded.LiveTv),
    MOVIES(R.string.play_section_movies, Icons.Rounded.Movie),
    SERIES(R.string.play_section_series, Icons.Rounded.VideoLibrary);

    val label: String
        @Composable get() = stringResource(labelRes)
}

// Without an explicit target, focus is lost after returning from the player and the first key
// lands wherever Compose picks, which feels random on a remote.
class FocusRestore(val itemId: String?, val initial: Boolean, val done: () -> Unit)
class HomeActions(
    val favorites: Set<String>,
    val restore: FocusRestore,
    val open: (List<Stream>, Int) -> Unit,
    val toggleFavorite: (Stream) -> Unit,
    val watchLater: Set<String> = emptySet(),
    val toggleWatchLater: (Stream) -> Unit = {},
    /** Resume a continue-watching item directly (episodes go to their series page). */
    val resume: (Stream) -> Unit = {},
    /** Watch progress 0..1 keyed by favoriteKey; used for progress bars and watched checks on cards. */
    val progress: Map<String, Float> = emptyMap(),
    /** Small label on series cards (last watched episode, e.g. "S2 B5") keyed like [progress]. */
    val badges: Map<String, String> = emptyMap(),
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
    initialSearchQuery: String? = null,
    /** (live channel list, index, 2 or 4): opens the multi-view screen. */
    onMultiView: (List<Stream>, Int, Int) -> Unit = { _, _, _ -> },
) {
    var section by rememberSaveable(initialSearchQuery) {
        mutableStateOf(
            if (initialSearchQuery != null) Section.MOVIES
            else Section.entries.firstOrNull { it.name == initialSection } ?: Section.HOME,
        )
    }
    var searchType by rememberSaveable(initialSearchQuery) { mutableStateOf<ContentType?>(if (initialSearchQuery != null) ContentType.MOVIE else null) }
    var recordings by rememberSaveable { mutableStateOf(false) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocus by rememberSaveable { mutableStateOf(true) }
    val navFocus = remember { Section.entries.map { FocusRequester() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())
    val watchLater by repo.store.watchLater.collectAsState(initial = emptySet())
    val history by repo.history.entries.collectAsState(initial = emptyList())
    val progress = remember(history) {
        history.filter { it.type != ContentType.LIVE }.associate { it.favKey to (if (it.finished) 1f else it.fraction) }
            .plus(lastEpisodePerSeries(history).mapValues { (_, e) -> if (e.finished) 1f else e.fraction })
    }
    val badges = remember(history) {
        lastEpisodePerSeries(history).filterValues { it.season > 0 }.mapValues { (_, e) -> context.getString(R.string.ui_series_badge, e.season, e.episode) }
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
        watchLater = watchLater,
        toggleWatchLater = { s -> scope.launch { repo.store.toggleWatchLater(favoriteKey(playlist, s)) } },
        resume = { s ->
            restoreId = s.id
            val series = s.seriesId
            if (series != null) onSeries(s.copy(id = series)) else if (s.type == ContentType.SERIES && s.url.isEmpty()) onSeries(s) else onPlay(listOf(s), 0)
        },
        progress = progress,
        badges = badges,
        rules = rules,
        longPress = { s, c -> target = ActionTarget.Channel(s, c) },
        categoryLongPress = { t, id, name -> target = ActionTarget.Category(t, id, name) },
        requireUnlock = { a -> if (rules.unlocked || hasPin.isEmpty()) a() else pendingUnlock = a },
    )

    // OK on a menu entry moves focus into that section (its own first-focus logic runs again);
    // picking the section already shown just steps right into it.
    val focusManager = LocalFocusManager.current
    var stepIntoContent by remember { mutableIntStateOf(0) }
    fun selectSection(s: Section) {
        searchType = null
        recordings = false
        if (s == section) {
            stepIntoContent++
        } else {
            restoreId = null
            initialFocus = true
            section = s
        }
    }
    LaunchedEffect(stepIntoContent) {
        if (stepIntoContent > 0) focusManager.moveFocus(FocusDirection.Right)
    }
    // Back walks outwards like other TV apps: content → menu → Home → "exit?" instead of closing the app at once.
    // Registered before the sections compose, so their own Back handlers (dialogs, edits) win.
    var drawerFocused by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler {
        when {
            searchType != null -> { searchType = null; initialFocus = true }
            recordings -> { recordings = false; initialFocus = true }
            !drawerFocused -> runCatching { navFocus[section.ordinal].requestFocus() }
            section != Section.HOME -> {
                section = Section.HOME
                restoreId = null
                initialFocus = false
                runCatching { navFocus[Section.HOME.ordinal].requestFocus() }
            }
            else -> confirmExit = true
        }
    }
    if (confirmExit) {
        ConfirmDialog(
            title = context.getString(R.string.ui_exit_title),
            message = playlist.name,
            confirmLabel = context.getString(R.string.ui_exit_confirm),
            onConfirm = { context.findActivity()?.finish() },
            onDismiss = { confirmExit = false },
        )
    }

    ModalNavigationDrawer(
        scrimBrush = Brush.horizontalGradient(listOf(Palette.Background.copy(alpha = 0.92f), Color.Transparent)),
        drawerContent = { value ->
            val open = value == DrawerValue.Open
            LaunchedEffect(open) {
                if (open) runCatching { navFocus[section.ordinal].requestFocus() }
            }
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(if (open) Palette.Surface else Palette.Background)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    // Entering the menu lands on the current section instead of the nearest item.
                    .focusProperties { enter = { navFocus[section.ordinal] } }
                    .onFocusChanged { drawerFocused = it.hasFocus },
                // Items keep the drawer's own 56 dp height: forcing 42 dp made the selected pill overlap its
                // neighbours and the logo. Sections sit centred in the free space (4 entries, plenty of room).
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(32.dp)
                    if (open) {
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
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
                Spacer(Modifier.weight(1f))
                Section.entries.forEach { s ->
                    NavigationDrawerItem(
                        selected = s == section,
                        onClick = { selectSection(s) },
                        leadingContent = { Icon(s.icon, contentDescription = null) },
                        modifier = Modifier.focusRequester(navFocus[s.ordinal]).pointerClick({ selectSection(s) }),
                    ) { Text(s.label) }
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    selected = false,
                    onClick = onSettings,
                    leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = context.getString(R.string.play_settings)) },
                    modifier = Modifier.pointerClick(onSettings),
                ) { Text(context.getString(R.string.play_settings)) }
                NavigationDrawerItem(
                    selected = false,
                    onClick = {
                        scope.launch { repo.refresh(playlist.id) }
                        onPlaylists()
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = context.getString(R.string.play_playlists)) },
                    modifier = Modifier.pointerClick({ scope.launch { repo.refresh(playlist.id) }; onPlaylists() }),
                ) { Text(context.getString(R.string.play_playlists)) }
            }
        },
    ) {
        // Home draws its backdrop edge to edge and pads its own content; other sections get the usual margins.
        val fullBleed = section == Section.HOME && !recordings && searchType == null
        Box(Modifier.fillMaxSize().then(if (fullBleed) Modifier else Modifier.padding(start = 84.dp, top = 20.dp, end = 28.dp))) {
            key(section, playlist.id) {
                when {
                    recordings -> RecordingsSection(repo, playlist, onPlay)
                    searchType != null -> SearchSection(repo, playlist, actions, initialQuery = initialSearchQuery, type = searchType!!)
                    else -> when (section) {
                        Section.HOME -> HomeSection(repo, playlist, actions)
                        Section.LIVE -> LiveSection(repo, playlist, actions, onSearch = { searchType = ContentType.LIVE; initialFocus = true }, onRecordings = { recordings = true })
                        Section.MOVIES -> VodSection(repo, playlist, ContentType.MOVIE, actions, onSearch = { searchType = ContentType.MOVIE; initialFocus = true })
                        Section.SERIES -> VodSection(repo, playlist, ContentType.SERIES, actions, onSearch = { searchType = ContentType.SERIES; initialFocus = true })
                    }
                }
            }
        }
    }

    HomeOverlays(repo, playlist, target, hasPin.isNotEmpty(), rules, favorites, watchLater, { target = null }, pendingUnlock, { pendingUnlock = null }, onMultiView)
}

@Composable
private fun HomeOverlays(
    repo: Repository,
    playlist: Playlist,
    target: ActionTarget?,
    hasPin: Boolean,
    rules: ContentRules,
    favorites: Set<String>,
    watchLater: Set<String>,
    dismiss: () -> Unit,
    pendingUnlock: (() -> Unit)?,
    clearUnlock: () -> Unit,
    onMultiView: (List<Stream>, Int, Int) -> Unit,
) {
    val historyEntries by repo.history.entries.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noPin = context.getString(R.string.play_pin_needed)
    when (target) {
        is ActionTarget.Channel -> {
            val s = target.stream
            val fav = favoriteKey(playlist, s) in favorites
            val cat = target.categoryName
            val locked = rules.isCategoryMarkedLocked(playlist, s.type, s.categoryId)
            val actions = buildList {
                add(CardAction(if (fav) context.getString(R.string.play_favorite_remove) else context.getString(R.string.play_favorite_add), Icons.Rounded.Favorite) {
                    scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, s)) }
                    dismiss()
                })
                if (s.type != ContentType.LIVE) {
                    val later = favoriteKey(playlist, s) in watchLater
                    add(CardAction(if (later) context.getString(R.string.ui_remove_watch_later) else context.getString(R.string.ui_add_watch_later), Icons.Rounded.WatchLater) {
                        scope.launch { repo.store.toggleWatchLater(favoriteKey(playlist, s)) }
                        dismiss()
                    })
                }
                if (historyEntries.any { it.playlistId == playlist.id && (it.favKey == favoriteKey(playlist, s) || (s.type == ContentType.SERIES && it.seriesId == s.id)) }) {
                    add(CardAction(context.getString(R.string.ui_remove_from_history), Icons.Rounded.DeleteSweep) {
                        scope.launch { repo.history.remove(playlist, s) }
                        dismiss()
                    })
                }
                add(CardAction(context.getString(R.string.play_hide), Icons.Rounded.VisibilityOff) {
                    scope.launch { repo.prefs.setChannelHidden(playlist, s, true) }
                    dismiss()
                })
                if (s.type == ContentType.LIVE) {
                    for (n in intArrayOf(2, 4)) {
                        add(CardAction(context.getString(R.string.play_multi_count, n), Icons.Rounded.GridView) {
                            dismiss()
                            scope.launch {
                                val content = runCatching { repo.content(playlist, ContentType.LIVE) }.getOrNull() ?: return@launch
                                val items = rules.unlockedItems(playlist, ContentType.LIVE, rules.filterBase(playlist, ContentType.LIVE, content).items)
                                    .filter { it.url.isNotEmpty() }
                                val idx = items.indexOfFirst { it.id == s.id }
                                if (idx >= 0) onMultiView(items, idx, n) else onMultiView(listOf(s) + items, 0, n)
                            }
                        })
                    }
                }
                if (cat != null) {
                    if (!hasPin) {
                        add(CardAction(noPin, enabled = false) {})
                    } else {
                        add(CardAction(if (locked) context.getString(R.string.play_category_unlock) else context.getString(R.string.play_category_lock), Icons.Rounded.Lock) {
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
                add(CardAction(context.getString(R.string.play_category_hide), Icons.Rounded.VisibilityOff) {
                    scope.launch { repo.prefs.setCategoryHidden(playlist, target.type, target.id, target.name, true) }
                    dismiss()
                })
                if (!hasPin) {
                    add(CardAction(noPin, enabled = false) {})
                } else {
                    add(CardAction(if (locked) context.getString(R.string.play_unlock) else context.getString(R.string.play_category_lock), Icons.Rounded.Lock) {
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

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
