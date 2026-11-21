package dev.iptv.player

import android.app.Application
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream

class App : Application() {
    val releasePrefs by lazy { dev.iptv.player.data.ReleasePrefs(this) }

    override fun onCreate() {
        super.onCreate()
        dev.iptv.player.util.CrashReporter.install(this)
    }

    val repository by lazy { Repository(this) }
}

object PlayerQueue {
    var playlist: Playlist? = null
    var items: List<Stream> = emptyList()
    var index = 0
}
