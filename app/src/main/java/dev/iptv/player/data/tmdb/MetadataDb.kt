package dev.iptv.player.data.tmdb

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** No provider URLs or credentials: only title identity and its resolved public metadata. */
@Entity(tableName = "catalog_metadata", primaryKeys = ["playlistId", "type", "id"],
    indices = [Index(value = ["playlistId", "type", "resolved"])])
data class MetadataEntry(
    val playlistId: String,
    val type: String,
    val id: String,
    val name: String,
    val year: String?,
    val identity: String,
    val resolved: Boolean = false,
    val info: String? = null,
)

data class MetadataCatalogKey(val playlistId: String, val type: String)

@Dao
abstract class MetadataDao {
    @Query("SELECT * FROM catalog_metadata WHERE playlistId = :playlistId AND type = :type")
    abstract suspend fun entries(playlistId: String, type: String): List<MetadataEntry>

    @Query("SELECT * FROM catalog_metadata WHERE playlistId = :playlistId AND type = :type AND resolved = 1 AND info IS NOT NULL")
    abstract fun observeReady(playlistId: String, type: String): Flow<List<MetadataEntry>>

    @Query("SELECT * FROM catalog_metadata WHERE playlistId = :playlistId AND type = :type AND resolved = 0 LIMIT :limit")
    abstract suspend fun pending(playlistId: String, type: String, limit: Int): List<MetadataEntry>

    @Query("SELECT DISTINCT playlistId, type FROM catalog_metadata WHERE resolved = 0")
    abstract suspend fun pendingCatalogs(): List<MetadataCatalogKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entries: List<MetadataEntry>)

    @Query("DELETE FROM catalog_metadata WHERE playlistId = :playlistId AND type = :type AND id IN (:ids)")
    abstract suspend fun deleteItems(playlistId: String, type: String, ids: List<String>)

    @Query("DELETE FROM catalog_metadata WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: String)

    // A result finishing after a playlist refresh must not overwrite the replacement title.
    @Query("UPDATE catalog_metadata SET resolved = 1, info = :info WHERE playlistId = :playlistId AND type = :type AND id = :id AND identity = :identity")
    abstract suspend fun resolve(playlistId: String, type: String, id: String, identity: String, info: String?)

    @Transaction
    open suspend fun reconcile(playlistId: String, type: String, incoming: List<MetadataEntry>) {
        val existing = entries(playlistId, type).associate { it.id to it.identity }
        val plan = catalogSyncPlan(existing, incoming.associate { it.id to it.identity })
        plan.removed.toList().chunked(400).forEach { deleteItems(playlistId, type, it) }
        incoming.filter { it.id in plan.changed }.chunked(400).forEach { upsert(it) }
    }
}

@Database(entities = [MetadataEntry::class], version = 1, exportSchema = false)
abstract class MetadataDb : RoomDatabase() {
    abstract fun dao(): MetadataDao

    companion object {
        @Volatile private var instance: MetadataDb? = null
        fun get(context: Context): MetadataDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MetadataDb::class.java, "tmdb-catalog.db")
                .build().also { instance = it }
        }
    }
}
