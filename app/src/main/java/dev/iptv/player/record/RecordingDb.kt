package dev.iptv.player.record

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object RecStatus {
    const val SCHEDULED = "SCHEDULED"
    const val RECORDING = "RECORDING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/** One recording, planned, running or finished. [id] = "playlistId|streamId|startMs". Times are epoch millis. */
@Entity(tableName = "recording")
data class Recording(
    @PrimaryKey val id: String,
    val playlistId: String,
    val streamId: String,
    val channelName: String,
    val title: String,
    val startAt: Long,
    val stopAt: Long,
    val status: String,
    val filePath: String? = null,
    val bytes: Long = 0,
    val error: String? = null,
) {
    companion object {
        fun idOf(playlistId: String, streamId: String, startMs: Long) = "$playlistId|$streamId|$startMs"
    }
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording ORDER BY startAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recording")
    suspend fun all(): List<Recording>

    @Query("SELECT * FROM recording WHERE id = :id")
    suspend fun get(id: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(r: Recording)

    /** Updates only an existing row: a service finishing after deletion must not recreate it. */
    @Update
    suspend fun update(r: Recording): Int

    @Query("DELETE FROM recording WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE recording SET bytes = :bytes WHERE id = :id")
    suspend fun setBytes(id: String, bytes: Long)
}

@Database(entities = [Recording::class], version = 1, exportSchema = false)
abstract class RecordingDb : RoomDatabase() {
    abstract fun dao(): RecordingDao

    companion object {
        @Volatile private var instance: RecordingDb? = null

        fun get(context: Context): RecordingDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RecordingDb::class.java, "recordings.db").build().also { instance = it }
        }
    }
}
