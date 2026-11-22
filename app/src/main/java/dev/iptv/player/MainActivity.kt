package dev.iptv.player

import android.content.Intent
import android.app.SearchManager
import android.widget.Toast
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.iptv.player.ui.DisplayPreferences
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.iptv.player.data.DeepLinks
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream
import dev.iptv.player.player.MultiViewScreen
import dev.iptv.player.player.PipState
import dev.iptv.player.player.PlayerScreen
import dev.iptv.player.ui.AboutScreen
import dev.iptv.player.ui.AddPlaylistScreen
import dev.iptv.player.ui.OnboardingScreen
import dev.iptv.player.ui.EditPlaylistScreen
import dev.iptv.player.ui.HomeScreen
import dev.iptv.player.ui.IptvTheme
import dev.iptv.player.ui.MovieScreen
import dev.iptv.player.ui.PlaylistsScreen
import dev.iptv.player.ui.SeriesScreen
import dev.iptv.player.ui.StatusText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // iptvplayer://movie|series/{id}?playlist={pid}, e.g. from the Google TV Watch Next row.
    private val deepLink = mutableStateOf<Uri?>(null)
    private val searchQuery = mutableStateOf<String?>(null)

    companion object {
        var launchSection: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val repository = (application as App).repository
        val prefs = (application as App).releasePrefs
        if (savedInstanceState == null) deepLink.value = intent?.data?.takeIf { it.scheme == DeepLinks.SCHEME }
        // `am start --es section LIVE` opens a section directly (used for on-device QA and launcher shortcuts).
        if (savedInstanceState == null) launchSection = intent?.getStringExtra("section")
        if (savedInstanceState == null) {
            intent?.getStringExtra("migrate_spectre_b64")?.takeIf { it.isNotBlank() }?.let { b64 ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    runCatching { migrateSpectreJson(repository, String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)) }
                }
            }
        }
        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEARCH) searchQuery.value = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        dev.iptv.player.epg.ReminderBus.handleIntent(intent)
        PipState.inPip.value = false
        // Picture-in-Picture state for the player screen (hides overlays while shrunk).
        addOnPictureInPictureModeChangedListener { info -> PipState.inPip.value = info.isInPictureInPictureMode }
        // Compose paints the full-screen background itself; drawing the window background too doubled
        // every frame's fill on weak TV GPUs (measured with the overdraw debugger on a TCL rtd6748).
        window.setBackgroundDrawable(null)
        setContent {
            DisplayPreferences(repository.settings) {
                IptvTheme {
                    AppNav(repository, prefs, deepLink, searchQuery)
                    dev.iptv.player.ui.ReminderHost()
                }
            }
        }
    }

    /** HOME while a live channel plays: keep it running in a PiP window instead of stopping it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.autoEnter) PipState.enter(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.data?.takeIf { it.scheme == DeepLinks.SCHEME }
        if (intent.action == Intent.ACTION_SEARCH) searchQuery.value = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        dev.iptv.player.epg.ReminderBus.handleIntent(intent)
    }
}

@Composable
private fun AppNav(repo: Repository, prefs: dev.iptv.player.data.ReleasePrefs, deepLink: MutableState<Uri?>, searchQuery: MutableState<String?>) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val playlists by repo.store.playlists.collectAsState(initial = null)
    val activeId by repo.store.activeId.collectAsState(initial = null)
    val accepted by prefs.onboardingAccepted.collectAsState(initial = null)
    val all = playlists ?: return
    val onboarded = accepted ?: return
    val startup by produceState<Startup?>(null) {
        val s = repo.settings
        val profiles = repo.profiles.list()
        val last = s.get(Setting.StartWithLastChannel)
        value = Startup(profiles, s.get(Setting.DefaultSection), if (last) s.get(Setting.LastChannel) else "")
    }
    val st = startup ?: return
    val active = all.firstOrNull { it.id == activeId } ?: all.firstOrNull()

    suspend fun visibleItems(playlist: Playlist, type: ContentType): List<Stream> {
        val content = repo.content(playlist, type)
        val rules = repo.prefs.rules.first().copy(unlocked = dev.iptv.player.data.ParentalSession.unlocked)
        return rules.filterBase(playlist, type, content).items.filter { rules.streamAllowed(playlist, it) }
    }

    fun play(playlist: Playlist, items: List<Stream>, index: Int) {
        PlayerQueue.playlist = playlist
        PlayerQueue.items = items
        PlayerQueue.index = index
        PlayerQueue.save(repo.appContext)
        nav.navigate("player")
    }

    // EPG reminder "İzle" / notification deep link: start the live channel.
    val tune by dev.iptv.player.epg.ReminderBus.tune.collectAsState()
    LaunchedEffect(tune, all) {
        val req = tune ?: return@LaunchedEffect
        dev.iptv.player.epg.ReminderBus.tune.value = null
        val playlist = all.firstOrNull { it.id == req.playlistId } ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        val items = runCatching { visibleItems(playlist, ContentType.LIVE) }.getOrNull() ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.id == req.streamId }
        if (idx < 0) return@LaunchedEffect
        if (nav.currentDestination?.route == "player") nav.popBackStack()
        play(playlist, items, idx)
    }

    val needPicker = st.profiles.size > 1 && !ProfileStore.pickedThisLaunch
    NavHost(
        nav,
        startDestination = if (!onboarded) "onboarding" else if (active == null) "playlists" else if (needPicker) "profiles" else "home",
        enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(150)) },
    ) {
        composable("onboarding") {
            OnboardingScreen(onAccept = {
                scope.launch {
                    prefs.acceptOnboarding()
                    nav.navigate(if (active == null) "playlists" else "home") { popUpTo(0) }
                }
            })
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        composable("profiles") {
            ProfilePicker(st.profiles, onPicked = { nav.goHome() })
        }
        composable("settings") {
            SettingsScreen(repo, onProfileSwitched = { nav.goHome() }, playlist = active)
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
                onDelete = { p -> scope.launch { repo.removePlaylist(p.id) } },
                onEdit = { p -> nav.navigate("edit/${Uri.encode(p.id)}") },
                onRefresh = { p ->
                    try {
                        repo.refresh(p.id)
                        repo.reload(p)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.message ?: context.getString(R.string.app_refresh_failed)
                    }
                },
                loadAccount = { p -> repo.accountInfo(p) },
            )
        }
        composable("edit/{id}") { entry ->
            val target = all.firstOrNull { it.id == entry.arguments?.getString("id") }
            if (target != null) EditPlaylistScreen(repo, target, onDone = { nav.popBackStack() })
        }
        composable("add") {
            AddPlaylistScreen(repo, onSaved = { nav.goHome() })
        }
        composable("home?query={query}", arguments = listOf(navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val query = entry.arguments?.getString("query")
            if (active == null) {
                StatusText(stringResource(R.string.app_loading))
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
                        if (query != null || deepLink.value != null || searchQuery.value != null) return@LaunchedEffect
                        val items = runCatching { visibleItems(active, ContentType.LIVE) }.getOrNull().orEmpty()
                        val i = items.indexOfFirst { it.id == sid }
                        if (i >= 0) play(active, items, i)
                    }
                }
                HomeScreen(
                    repo = repo,
                    playlist = active,
                    onPlay = { items, index -> play(active, items, index) },
                    onSeries = { s -> nav.navigate("series/${Uri.encode(s.id)}") },
                    onMovie = { s -> nav.navigate("movie/${Uri.encode(s.id)}") },
                    onPlaylists = { nav.navigate("playlists") },
                    onSettings = { nav.navigate("settings") },
                    initialSection = MainActivity.launchSection ?: st.defaultSection,
                    initialSearchQuery = query,
                    onMultiView = { items, index, n ->
                        PlayerQueue.playlist = active
                        PlayerQueue.items = items
                        PlayerQueue.index = index
                        PlayerQueue.save(repo.appContext)
                        nav.navigate("multiview/$n")
                    },
                )
            }
        }
        composable("movie/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            if (active != null) {
                MovieScreen(
                    repo, active, id,
                    onPlay = { items, index -> play(active, items, index) },
                    onMovie = { s -> nav.navigate("movie/${Uri.encode(s.id)}") },
                )
            }
        }
        composable("series/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            if (active != null) SeriesScreen(repo, active, id, onPlay = { items, index -> play(active, items, index) })
        }
        composable("player") {
            PlayerScreen(repo, onBack = { nav.popBackStack() }, onMultiView = { n -> nav.navigate("multiview/$n") })
        }
        composable("multiview/{count}") { entry ->
            MultiViewScreen(repo, entry.arguments?.getString("count")?.toIntOrNull() ?: 2, onBack = { nav.popBackStack() })
        }
    }

    val requestedSearch = searchQuery.value
    LaunchedEffect(requestedSearch, onboarded, active) {
        if (!onboarded || active == null || requestedSearch == null) return@LaunchedEffect
        nav.navigate("home?query=${Uri.encode(requestedSearch)}") { popUpTo(0) }
        searchQuery.value = null
    }

    val pending = deepLink.value
    LaunchedEffect(pending, all, activeId, onboarded) {
        val uri = pending ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        val id = uri.lastPathSegment ?: return@LaunchedEffect
        val playlist = all.firstOrNull { it.id == uri.getQueryParameter("playlist") } ?: return@LaunchedEffect
        val type = when (uri.host) {
            "live" -> ContentType.LIVE
            "movie" -> ContentType.MOVIE
            "series" -> ContentType.SERIES
            else -> { deepLink.value = null; return@LaunchedEffect }
        }
        val visible = runCatching { visibleItems(playlist, type) }.getOrNull().orEmpty()
        if (visible.none { it.id == id }) {
            deepLink.value = null
            Toast.makeText(context, R.string.app_content_unavailable, Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        if (activeId != playlist.id) {
            repo.store.setActive(playlist.id)
            return@LaunchedEffect
        }
        deepLink.value = null
        if (uri.host == "live") {
            // System search / external link to a live channel: open the player on it.
            val items = visible.filter { it.url.isNotEmpty() }
            val idx = items.indexOfFirst { it.id == id }
            if (idx < 0) return@LaunchedEffect
            nav.navigate("home") { popUpTo(0) }
            play(playlist, items, idx)
            return@LaunchedEffect
        }
        val route = when (uri.host) {
            "movie" -> "movie/${Uri.encode(id)}"
            "series" -> "series/${Uri.encode(id)}"
            else -> return@LaunchedEffect
        }
        nav.navigate("home") { popUpTo(0) }
        nav.navigate(route)
    }
}

private fun NavHostController.goHome() = navigate("home") { popUpTo(0) }

private class Startup(val profiles: List<Profile>, val defaultSection: String, val lastChannel: String) {
    companion object {
        /** Last-channel auto launch runs once per process. */
        @Volatile var handled = false
    }
}

private suspend fun migrateSpectreJson(repo: Repository, rawJson: String) {
    val playlists = repo.store.playlists.first()
    val activeId = repo.store.activeId.first()
    val playlist = playlists.firstOrNull { it.id == activeId } ?: playlists.firstOrNull() ?: return
    val live = repo.content(playlist, ContentType.LIVE).items
    val movies = repo.content(playlist, ContentType.MOVIE).items
    val series = repo.content(playlist, ContentType.SERIES).items

    fun norm(s: String) = s.trim().trimEnd('|').replace(Regex("\\s+"), " ").lowercase()
    fun findStream(pool: List<Stream>, title: String): Stream? {
        val t = title.trim()
        pool.firstOrNull { it.name.trim().equals(t, ignoreCase = true) }?.let { return it }
        val nt = norm(t)
        return pool.firstOrNull { norm(it.name) == nt }
            ?: pool.firstOrNull { dev.iptv.player.ui.titleWithoutYear(norm(it.name), it.year) == nt }
    }

    val obj = org.json.JSONObject(rawJson)
    fun arr(key: String): List<String> {
        val a = obj.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it)?.takeIf { s -> s.isNotBlank() } }
    }

    val favKeys = LinkedHashSet<String>()
    arr("liveFav").mapNotNull { findStream(live, it) }.forEach { favKeys += dev.iptv.player.data.favoriteKey(playlist, it) }
    arr("moviesFav").mapNotNull { findStream(movies, it) }.forEach { favKeys += dev.iptv.player.data.favoriteKey(playlist, it) }
    arr("seriesFav").mapNotNull { findStream(series, it) }.forEach { favKeys += dev.iptv.player.data.favoriteKey(playlist, it) }

    val now = System.currentTimeMillis()
    val historyEntries = ArrayList<dev.iptv.player.data.HistoryEntry>()
    var order = 0
    fun addHistory(stream: Stream, posMs: Long = 0L, durMs: Long = 0L, seriesId: String? = null) {
        val fk = dev.iptv.player.data.favoriteKey(playlist, stream)
        val scoped = repo.history.scoped(fk)
        if (historyEntries.any { it.key == scoped }) return
        historyEntries += dev.iptv.player.data.HistoryEntry(
            key = scoped,
            favKey = fk,
            playlistId = playlist.id,
            id = stream.id,
            type = stream.type,
            name = stream.name,
            logo = stream.logo,
            url = stream.url,
            categoryId = stream.categoryId,
            seriesId = seriesId,
            positionMs = posMs,
            durationMs = durMs,
            updatedAt = now - (order++ * 1000L),
        )
    }

    arr("liveRecent").mapNotNull { findStream(live, it) }.forEach { addHistory(it) }
    arr("moviesResume").mapNotNull { findStream(movies, it) }.forEach { addHistory(it, posMs = 120_000L, durMs = 5_400_000L) }

    val epArr = obj.optJSONArray("seriesEpisodes")
    val epByTitle = HashMap<String, org.json.JSONObject>()
    if (epArr != null) {
        for (i in 0 until epArr.length()) {
            val item = epArr.optJSONObject(i) ?: continue
            val sTitle = item.optString("series")
            if (sTitle.isNotBlank()) epByTitle[norm(sTitle)] = item
        }
    }
    for (title in arr("seriesRecent")) {
        val sStream = findStream(series, title) ?: continue
        val epObj = epByTitle[norm(title)] ?: epByTitle[norm(sStream.name)]
        if (epObj != null) {
            val season = epObj.optInt("season", 1).coerceAtLeast(1)
            val episode = epObj.optInt("episode", 1).coerceAtLeast(1)
            val hasSeekbar = epObj.optBoolean("hasSeekbar", true)
            val pos = if (hasSeekbar) 600_000L else 20_000L
            val dur = 2_400_000L
            val fk = dev.iptv.player.data.favoriteKey(playlist, sStream)
            val scoped = repo.history.scoped(fk)
            if (historyEntries.none { it.key == scoped }) {
                historyEntries += dev.iptv.player.data.HistoryEntry(
                    key = scoped,
                    favKey = fk,
                    playlistId = playlist.id,
                    id = sStream.id,
                    type = ContentType.SERIES,
                    name = sStream.name,
                    logo = sStream.logo,
                    url = sStream.url,
                    categoryId = sStream.categoryId,
                    seriesId = sStream.id,
                    season = season,
                    episode = episode,
                    positionMs = pos,
                    durationMs = dur,
                    updatedAt = now - (order++ * 1000L),
                )
            }
        } else {
            addHistory(sStream, seriesId = sStream.id)
        }
    }

    repo.store.resetAndSetFavorites(favKeys)
    repo.history.replaceAll(historyEntries)
    repo.settings.set(Setting.BrowsePrefs, "")
    android.util.Log.i("SeyirMigrate", "Migrated favs=${favKeys.size} history=${historyEntries.size}")
}
