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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import dev.iptv.player.data.ChannelGroups
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
    /** User channel groups ("Gruplarım") of the active profile. */
    val groups: List<dev.iptv.player.data.ChannelGroup> = emptyList(),
    /** Long-press on a group entry in the Live category list (id, name). */
    val groupLongPress: (String, String) -> Unit = { _, _ -> },
    /** Focus requester for the active section pill in the top navigation bar. */
    val menuFocus: () -> FocusRequester = { FocusRequester.Default },
)

private sealed interface ActionTarget {
    data class Channel(val stream: Stream, val categoryName: String?) : ActionTarget
    data class Category(val type: ContentType, val id: String, val name: String) : ActionTarget
    /** "Add to group" picker for a live channel. */
    data class GroupPicker(val stream: Stream) : ActionTarget
    data class NewGroup(val stream: Stream) : ActionTarget
    data class Group(val id: String, val name: String) : ActionTarget
    data class RenameGroup(val id: String, val name: String) : ActionTarget
    data class ConfirmDeleteGroup(val id: String, val name: String) : ActionTarget
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
    val groups by repo.store.groups.collectAsState(initial = emptyList())
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
        groups = groups,
        groupLongPress = { id, name -> target = ActionTarget.Group(id, name) },
        menuFocus = { navFocus[section.ordinal] },
    )

    val refreshWork by remember(repo, playlist.id) {
        dev.iptv.player.data.RefreshWorker.observe(repo.appContext, playlist.id)
    }.collectAsState(initial = null)
    val refreshing = refreshWork?.state?.isFinished == false

    // OK on a menu entry moves focus into that section (its own first-focus logic runs again);
    // picking the section already shown just steps deterministically into its primary focus target.
    var contentFocusTrigger by remember { mutableIntStateOf(0) }
    fun selectSection(s: Section) {
        searchType = null
        recordings = false
        if (s == section) {
            contentFocusTrigger++
        } else {
            restoreId = null
            initialFocus = true
            section = s
        }
    }
    // Back walks outwards like other TV apps: content → top menu → Home → "exit?" instead of closing the app at once.
    // Registered before the sections compose, so their own Back handlers (dialogs, edits) win.
    var navBarFocused by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler {
        when {
            searchType != null -> { searchType = null; initialFocus = true }
            recordings -> { recordings = false; initialFocus = true }
            !navBarFocused -> {
                runCatching { navFocus[section.ordinal].requestFocus() }
            }
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

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        // Home and Live draw their backdrop edge to edge and pad their own content; other sections sit below the top bar.
        val fullBleed = (section == Section.HOME || section == Section.LIVE) && !recordings && searchType == null
        Box(
            Modifier
                .fillMaxSize()
                .then(if (fullBleed) Modifier else Modifier.padding(start = 48.dp, top = 74.dp, end = 48.dp)),
        ) {
            key(section, playlist.id) {
                when {
                    recordings -> RecordingsSection(repo, playlist, onPlay)
                    searchType != null -> SearchSection(repo, playlist, actions, initialQuery = initialSearchQuery, type = searchType!!, focusTrigger = contentFocusTrigger)
                    else -> when (section) {
                        Section.HOME -> HomeSection(repo, playlist, actions, focusTrigger = contentFocusTrigger)
                        Section.LIVE -> LiveSection(repo, playlist, actions, onSearch = { searchType = ContentType.LIVE; initialFocus = true }, onRecordings = { recordings = true }, focusTrigger = contentFocusTrigger)
                        Section.MOVIES -> VodSection(repo, playlist, ContentType.MOVIE, actions, onSearch = { searchType = ContentType.MOVIE; initialFocus = true }, focusTrigger = contentFocusTrigger)
                        Section.SERIES -> VodSection(repo, playlist, ContentType.SERIES, actions, onSearch = { searchType = ContentType.SERIES; initialFocus = true }, focusTrigger = contentFocusTrigger)
                    }
                }
            }
        }

        // Design v2 centered floating top pill menu with brand mark on the left and utilities on the right.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 14.dp)
                .focusProperties { enter = { navFocus[section.ordinal] } }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                        contentFocusTrigger++
                        true
                    } else false
                }
                .onFocusChanged { navBarFocused = it.hasFocus },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.width(240.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 1,
                )
                Text(
                    " · ${playlist.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xD910131A))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Section.entries.forEach { s ->
                        val activeTab = s == section && !recordings && searchType == null
                        TopNavPill(
                            label = s.label,
                            selected = activeTab,
                            onClick = { selectSection(s) },
                            modifier = Modifier
                                .focusRequester(navFocus[s.ordinal])
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    if (s.ordinal == 0) left = FocusRequester.Cancel
                                },
                        )
                    }
                }
            }
            Row(
                Modifier.width(240.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopNavIconPill(
                    icon = Icons.Rounded.Refresh,
                    description = context.getString(if (refreshing) R.string.ux_refreshing else R.string.ux_refresh),
                    enabled = !refreshing,
                    modifier = Modifier.focusProperties { up = FocusRequester.Cancel },
                    onClick = { dev.iptv.player.data.RefreshWorker.request(repo.appContext, playlist.id) },
                )
                TopNavIconPill(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    description = context.getString(R.string.play_playlists),
                    modifier = Modifier.focusProperties { up = FocusRequester.Cancel },
                    onClick = {
                        scope.launch { repo.refresh(playlist.id) }
                        onPlaylists()
                    },
                )
                TopNavIconPill(
                    icon = Icons.Rounded.Settings,
                    description = context.getString(R.string.play_settings),
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    onClick = onSettings,
                )
            }
        }
    }

    HomeOverlays(repo, playlist, target, hasPin.isNotEmpty(), rules, favorites, watchLater, { target = null }, pendingUnlock, { pendingUnlock = null }, onMultiView, groups, { target = it })
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
    groups: List<dev.iptv.player.data.ChannelGroup> = emptyList(),
    setTarget: (ActionTarget) -> Unit = {},
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
                if (s.type == ContentType.LIVE) {
                    add(CardAction(context.getString(R.string.ui_add_to_group), Icons.AutoMirrored.Rounded.PlaylistAdd) {
                        // No groups yet: go straight to naming one.
                        setTarget(if (groups.isEmpty()) ActionTarget.NewGroup(s) else ActionTarget.GroupPicker(s))
                    })
                }
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
        is ActionTarget.GroupPicker -> {
            val s = target.stream
            val key = favoriteKey(playlist, s)
            val actions = buildList {
                for (g in groups) {
                    val inside = key in g.keys
                    add(CardAction(g.name, if (inside) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank) {
                        scope.launch { repo.store.editGroups { ChannelGroups.toggle(it, g.id, key) } }
                        dismiss()
                    })
                }
                add(CardAction(context.getString(R.string.ui_new_group), Icons.Rounded.Add) { setTarget(ActionTarget.NewGroup(s)) })
            }
            ActionCard(s.name, context.getString(R.string.ui_groups_pick_hint), actions, onDismiss = dismiss)
        }
        is ActionTarget.NewGroup -> {
            val key = favoriteKey(playlist, target.stream)
            NameInputDialog(
                title = context.getString(R.string.ui_new_group).trimEnd('…'),
                placeholder = context.getString(R.string.ui_group_name),
                confirmLabel = context.getString(R.string.ui_create),
                onConfirm = { name ->
                    scope.launch { repo.store.editGroups { ChannelGroups.create(it, java.util.UUID.randomUUID().toString(), name, key) } }
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        is ActionTarget.Group -> {
            val idx = groups.indexOfFirst { it.id == target.id }
            val currentName = groups.getOrNull(idx)?.name ?: target.name
            val actions = buildList {
                add(CardAction(context.getString(R.string.ui_rename_group), Icons.Rounded.Edit) {
                    setTarget(ActionTarget.RenameGroup(target.id, currentName))
                })
                if (idx > 0) {
                    add(CardAction(context.getString(R.string.ui_move_up), Icons.Rounded.KeyboardArrowUp) {
                        scope.launch { repo.store.editGroups { ChannelGroups.move(it, target.id, -1) } }
                        dismiss()
                    })
                }
                if (idx in 0 until groups.lastIndex) {
                    add(CardAction(context.getString(R.string.ui_move_down), Icons.Rounded.KeyboardArrowDown) {
                        scope.launch { repo.store.editGroups { ChannelGroups.move(it, target.id, 1) } }
                        dismiss()
                    })
                }
                add(CardAction(context.getString(R.string.ui_delete_group), Icons.Rounded.Delete) {
                    setTarget(ActionTarget.ConfirmDeleteGroup(target.id, currentName))
                })
            }
            ActionCard(currentName, null, actions, onDismiss = dismiss)
        }
        is ActionTarget.RenameGroup -> {
            NameInputDialog(
                title = context.getString(R.string.ui_rename_group),
                placeholder = context.getString(R.string.ui_group_name),
                confirmLabel = context.getString(R.string.ui_save),
                initialValue = target.name,
                onConfirm = { newName ->
                    scope.launch { repo.store.editGroups { ChannelGroups.rename(it, target.id, newName) } }
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        is ActionTarget.ConfirmDeleteGroup -> {
            ConfirmDialog(
                context.getString(R.string.ui_delete_group),
                context.getString(R.string.ui_delete_group_message, target.name),
                context.getString(R.string.ui_delete),
                onConfirm = {
                    scope.launch { repo.store.editGroups { ChannelGroups.delete(it, target.id) } }
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        null -> Unit
    }
    if (pendingUnlock != null) {
        PinVerifyDialog(repo.prefs, onVerified = { val a = pendingUnlock; clearUnlock(); a() }, onDismiss = clearUnlock)
    }
}

@Composable
private fun TopNavPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x24F2B544) else Color.Transparent,
            contentColor = if (selected) Color(0xFFF2B544) else Palette.TextMuted,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Box(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopNavIconPill(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(38.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xD910131A),
            contentColor = Palette.TextMuted,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
            disabledContainerColor = Color(0x8010131A),
            disabledContentColor = Palette.TextFaint,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
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
