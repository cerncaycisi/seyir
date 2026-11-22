package dev.iptv.player.epg

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    // Blocking variants are used inside the ingest transaction (runInTransaction).
    @Insert
    fun insertProgrammes(items: List<Programme>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChannels(items: List<EpgChannel>)

    @Query("DELETE FROM programme")
    fun clearProgrammes()

    @Query("DELETE FROM epg_channel")
    fun clearChannels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMeta(meta: EpgMeta)

    @Query("SELECT value FROM epg_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Query("SELECT * FROM programme WHERE channelId = :channelId AND stop > :from AND start < :to ORDER BY start")
    suspend fun range(channelId: String, from: Long, to: Long): List<Programme>

    @Query("SELECT * FROM programme WHERE channelId = :channelId AND start <= :at AND stop > :at ORDER BY start DESC LIMIT 1")
    suspend fun at(channelId: String, at: Long): Programme?

    @Query("SELECT * FROM epg_channel")
    suspend fun channels(): List<EpgChannel>

    @Query("SELECT COUNT(*) FROM programme")
    suspend fun programmeCount(): Int
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reminder: Reminder)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): Reminder?

    @Query("SELECT * FROM reminder ORDER BY start")
    suspend fun all(): List<Reminder>

    @Query("SELECT id FROM reminder")
    fun observeIds(): Flow<List<String>>

    @Query("DELETE FROM reminder WHERE start < :before")
    suspend fun deleteBefore(before: Long)
}

@Database(
    entities = [Programme::class, EpgChannel::class, EpgMeta::class, Reminder::class],
    version = 2,
    exportSchema = false,
)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epg(): EpgDao
    abstract fun reminders(): ReminderDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE epg_channel ADD COLUMN icon TEXT")
            }
        }
    }
}
