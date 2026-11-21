package dev.iptv.player.epg

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One XMLTV programme. Times are UTC epoch seconds exactly as in the feed (EpgOffsetMinutes is applied on read). */
@Entity(tableName = "programme", indices = [Index(value = ["channelId", "start"])])
data class Programme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** XMLTV `<channel>`; [names] are all `<display-name>` values joined by newlines. */
@Entity(tableName = "epg_channel")
data class EpgChannel(@PrimaryKey val id: String, val name: String, val names: String = name) {
    val allNames: List<String> get() = names.split('\n').filter { it.isNotBlank() }
}

@Entity(tableName = "epg_meta")
data class EpgMeta(@PrimaryKey val key: String, val value: String)

/** A reminder for a future programme. [id] = "playlistId|streamId|start". */
@Entity(tableName = "reminder")
data class Reminder(
    @PrimaryKey val id: String,
    val playlistId: String,
    val streamId: String,
    val channelName: String,
    val title: String,
    val start: Long,
    val stop: Long,
) {
    companion object {
        fun idOf(playlistId: String, streamId: String, start: Long) = "$playlistId|$streamId|$start"
    }
}

sealed interface EpgStatus {
    data object Idle : EpgStatus
    data class Refreshing(val programmes: Int) : EpgStatus
    data class Error(val message: String) : EpgStatus
}
