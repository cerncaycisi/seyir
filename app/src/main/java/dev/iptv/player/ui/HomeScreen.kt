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
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
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
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.data.favoriteKey
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
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
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    repo: Repository,
    playlist: Playlist,
    onPlay: (List<Stream>, Int) -> Unit,
    onSeries: (Stream) -> Unit,
    onPlaylists: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocus by rememberSaveable { mutableStateOf(true) }
    val navFocus = remember { Section.entries.map { FocusRequester() } }
    val scope = rememberCoroutineScope()
    val favorites by repo.store.favorites.collectAsState(initial = emptySet())

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
            if (s.type == ContentType.SERIES && s.url.isEmpty()) onSeries(s) else onPlay(playable, playable.indexOf(s))
        },
        toggleFavorite = { s -> scope.launch { repo.store.toggleFavorite(favoriteKey(playlist, s)) } },
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
                            Text("IPTV Player", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Text)
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
                        modifier = Modifier.focusRequester(navFocus[s.ordinal]),
                    ) { Text(s.label) }
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    selected = false,
                    onClick = {
                        scope.launch { repo.refresh(playlist.id) }
                        onPlaylists()
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
                ) { Text("Playlistler") }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 84.dp, top = 20.dp, end = 28.dp)) {
            key(section, playlist.id) {
                when (section) {
                    Section.LIVE -> LiveSection(repo, playlist, actions)
                    Section.MOVIES -> VodSection(repo, playlist, ContentType.MOVIE, actions)
                    Section.SERIES -> VodSection(repo, playlist, ContentType.SERIES, actions)
                    Section.FAVORITES -> FavoritesSection(repo, playlist, actions)
                    Section.SEARCH -> SearchSection(repo, playlist, actions)
                }
            }
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
