package dev.iptv.player

import android.net.Uri
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.produceState
import dev.iptv.player.data.LastChannel
import dev.iptv.player.data.Profile
import dev.iptv.player.data.ProfileStore
import dev.iptv.player.data.Setting
import dev.iptv.player.data.ContentType
import dev.iptv.player.ui.ProfilePicker
import dev.iptv.player.ui.SettingsScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.player.PlayerScreen
import dev.iptv.player.ui.AddPlaylistScreen
import dev.iptv.player.ui.HomeScreen
import dev.iptv.player.ui.IptvTheme
import dev.iptv.player.ui.PlaylistsScreen
import dev.iptv.player.ui.SeriesScreen
import dev.iptv.player.ui.StatusText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val repository = (application as App).repository
        setContent { IptvTheme { AppNav(repository) } }
    }
}

@Composable
private fun AppNav(repo: Repository) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val playlists by repo.store.playlists.collectAsState(initial = null)
    val activeId by repo.store.activeId.collectAsState(initial = null)
    val all = playlists ?: return
    val startup by produceState<Startup?>(null) {
        val s = repo.settings
        val profiles = repo.profiles.list()
        val last = s.get(Setting.StartWithLastChannel)
        value = Startup(profiles, s.get(Setting.DefaultSection), if (last) s.get(Setting.LastChannel) else "")
    }
    val st = startup ?: return
    val active = all.firstOrNull { it.id == activeId } ?: all.firstOrNull()

    fun play(playlist: Playlist, items: List<Stream>, index: Int) {
        PlayerQueue.playlist = playlist
        PlayerQueue.items = items
        PlayerQueue.index = index
        nav.navigate("player")
    }

    val needPicker = st.profiles.size > 1 && !ProfileStore.pickedThisLaunch
    NavHost(
        nav,
        startDestination = if (active == null) "playlists" else if (needPicker) "profiles" else "home",
        enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(150)) },
    ) {
        composable("profiles") {
            ProfilePicker(st.profiles, onPicked = { nav.goHome() })
        }
        composable("settings") {
            SettingsScreen(repo, onProfileSwitched = { nav.goHome() })
        }
        composable("playlists") {
            PlaylistsScreen(
                playlists = all,
                activeId = active?.id,
                onSelect = { p ->
                    scope.launch { repo.store.setActive(p.id) }
                    nav.goHome()
                },
                onAdd = { nav.navigate("add") },
                onDelete = { p -> scope.launch { repo.store.remove(p.id); repo.refresh(p.id) } },
            )
        }
        composable("add") {
            AddPlaylistScreen(repo, onSaved = { nav.goHome() })
        }
        composable("home") {
            if (active == null) {
                StatusText("Yükleniyor…")
                // The collected list can lag behind a playlist that was just saved; check the store itself.
                LaunchedEffect(Unit) {
                    if (repo.store.playlists.first().isEmpty()) nav.navigate("playlists") { popUpTo(0) }
                }
            } else {
                LaunchedEffect(active.id) {
                    if (!Startup.handled) {
                        Startup.handled = true
                        val (pid, sid) = LastChannel.decode(st.lastChannel) ?: return@LaunchedEffect
                        if (pid != active.id) return@LaunchedEffect
                        val items = runCatching { repo.content(active, ContentType.LIVE).items }.getOrNull().orEmpty()
                        val i = items.indexOfFirst { it.id == sid }
                        if (i >= 0) play(active, items, i)
                    }
                }
                HomeScreen(
                    repo = repo,
                    playlist = active,
                    onPlay = { items, index -> play(active, items, index) },
                    onSeries = { s -> nav.navigate("series/${Uri.encode(s.id)}") },
                    onPlaylists = { nav.navigate("playlists") },
                    onSettings = { nav.navigate("settings") },
                    initialSection = st.defaultSection,
                )
            }
        }
        composable("series/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            if (active != null) SeriesScreen(repo, active, id, onPlay = { items, index -> play(active, items, index) })
        }
        composable("player") {
            PlayerScreen(repo, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.goHome() = navigate("home") { popUpTo(0) }

private class Startup(val profiles: List<Profile>, val defaultSection: String, val lastChannel: String) {
    companion object {
        /** Last-channel auto launch runs once per process. */
        @Volatile var handled = false
    }
}
