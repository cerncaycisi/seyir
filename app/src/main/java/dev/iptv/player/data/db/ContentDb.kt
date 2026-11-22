package dev.iptv.player.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "categories", primaryKeys = ["playlistId", "type", "id"])
data class CategoryEntity(
    val playlistId: String,
    val type: String,
    val id: String,
    val name: String,
    val ord: Int,
)

@Entity(tableName = "streams", primaryKeys = ["playlistId", "type", "id"])
data class StreamEntity(
    val playlistId: String,
    val type: String,
    val id: String,
    val name: String,
    val logo: String?,
    val url: String,
    val categoryId: String,
    val epgId: String?,
    val rating: String?,
    val year: String?,
    val plot: String?,
    val backdrop: String?,
    val genre: String?,
    val headers: String?,
    val catchup: String?,
    val catchupDays: Int,
    val catchupSource: String?,
    val ord: Int,
    val tmdbId: Long? = null,
)

/** One row per playlist+type: when it was last fetched, plus the playlist's EPG URL (encrypted) for M3U lists. */
@Entity(tableName = "sync", primaryKeys = ["playlistId", "type"])
data class SyncEntity(
    val playlistId: String,
    val type: String,
    val fetchedAt: Long,
    val epgUrl: String?,
)

/** Lightweight projection used by the system-search provider (no URLs, so nothing secret is loaded). */
data class SearchProjection(
    val playlistId: String,
    val type: String,
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val year: String?,
    val categoryName: String?,
)

@Dao
abstract class ContentDao {
    @Query("SELECT s.playlistId, s.type, s.id, s.name, s.logo, s.categoryId, s.year, c.name AS categoryName FROM streams s LEFT JOIN categories c ON c.playlistId = s.playlistId AND c.type = s.type AND c.id = s.categoryId")
    abstract suspend fun searchProjection(): List<SearchProjection>

    @Query("SELECT COALESCE(MAX(fetchedAt), 0) FROM sync")
    abstract suspend fun lastFetched(): Long

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY ord")
    abstract suspend fun categories(playlistId: String, type: String): List<CategoryEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND type = :type ORDER BY ord")
    abstract suspend fun streams(playlistId: String, type: String): List<StreamEntity>

    @Query("SELECT * FROM sync WHERE playlistId = :playlistId AND type = :type")
    abstract suspend fun sync(playlistId: String, type: String): SyncEntity?

    @Query("SELECT epgUrl FROM sync WHERE playlistId = :playlistId AND epgUrl IS NOT NULL LIMIT 1")
    abstract suspend fun epgUrl(playlistId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertStreams(items: List<StreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSync(item: SyncEntity)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND type = :type")
    abstract suspend fun deleteCategories(playlistId: String, type: String)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId AND type = :type")
    abstract suspend fun deleteStreams(playlistId: String, type: String)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllCategories(playlistId: String)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllStreams(playlistId: String)

    @Query("DELETE FROM sync WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllSync(playlistId: String)

    /** Atomically replaces one playlist+type. Room batches the inserts inside this single transaction. */
    @Transaction
    open suspend fun replace(
        playlistId: String,
        type: String,
        categories: List<CategoryEntity>,
        streams: List<StreamEntity>,
        sync: SyncEntity,
    ) {
        deleteCategories(playlistId, type)
        deleteStreams(playlistId, type)
        insertCategories(categories)
        insertStreams(streams)
        upsertSync(sync)
    }

    @Transaction
    open suspend fun clear(playlistId: String) {
        deleteAllCategories(playlistId)
        deleteAllStreams(playlistId)
        deleteAllSync(playlistId)
    }
}

@Database(entities = [CategoryEntity::class, StreamEntity::class, SyncEntity::class], version = 2, exportSchema = false)
abstract class ContentDb : RoomDatabase() {
    abstract fun dao(): ContentDao

    companion object {
        @Volatile
        private var instance: ContentDb? = null

        fun get(context: Context): ContentDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ContentDb::class.java, "content.db")
                // The database is only a cache of what the provider serves, so a schema change just refetches.
                .setQueryExecutor(dev.iptv.player.util.BackgroundWork.dbExecutor)
                .setTransactionExecutor(dev.iptv.player.util.BackgroundWork.dbExecutor)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
