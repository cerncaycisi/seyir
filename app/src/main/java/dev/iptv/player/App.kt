package dev.iptv.player

import android.app.Application
import dev.iptv.player.data.Playlist
import dev.iptv.player.data.Repository
import dev.iptv.player.data.Stream

class App : Application() {
    val repository by lazy { Repository(this) }
}

object PlayerQueue {
    var playlist: Playlist? = null
    var items: List<Stream> = emptyList()
    var index = 0
}
