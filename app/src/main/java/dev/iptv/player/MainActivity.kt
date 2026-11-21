package dev.iptv.player

import android.net.Uri
import android.os.Bundle
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
import dev.iptv.player.ui.AboutScreen
import dev.iptv.player.ui.AddPlaylistScreen
import dev.iptv.player.ui.OnboardingScreen
import dev.iptv.player.ui.HomeScreen
import dev.iptv.player.ui.IptvTheme
import dev.iptv.player.ui.PlaylistsScreen
import dev.iptv.player.ui.SeriesScreen
import dev.iptv.player.ui.StatusText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as App).repository
        val prefs = (application as App).releasePrefs
        setContent { IptvTheme { AppNav(repository, prefs) } }
    }
}

@Composable
private fun AppNav(repo: Repository, prefs: dev.iptv.player.data.ReleasePrefs) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val playlists by repo.store.playlists.collectAsState(initial = null)
    val activeId by repo.store.activeId.collectAsState(initial = null)
    val accepted by prefs.onboardingAccepted.collectAsState(initial = null)
    val all = playlists ?: return
    val onboarded = accepted ?: return
    val active = all.firstOrNull { it.id == activeId } ?: all.firstOrNull()

    fun play(playlist: Playlist, items: List<Stream>, index: Int) {
        PlayerQueue.playlist = playlist
        PlayerQueue.items = items
        PlayerQueue.index = index
        nav.navigate("player")
    }

    NavHost(nav, startDestination = if (!onboarded) "onboarding" else if (active == null) "playlists" else "home") {
        composable("onboarding") {
            OnboardingScreen(onAccept = {
                scope.launch {
                    prefs.acceptOnboarding()
                    nav.navigate(if (active == null) "playlists" else "home") { popUpTo(0) }
                }
            })
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
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
                HomeScreen(
                    repo = repo,
                    playlist = active,
                    onPlay = { items, index -> play(active, items, index) },
                    onSeries = { s -> nav.navigate("series/${Uri.encode(s.id)}") },
                    onPlaylists = { nav.navigate("playlists") },
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
