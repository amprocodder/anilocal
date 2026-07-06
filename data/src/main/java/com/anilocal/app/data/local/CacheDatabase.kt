package com.anilocal.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * One generic key → JSON row. Namespaced keys ("detail:127230", "skip:5114:3", "extrepo:<url>", …)
 * keep this a single table shared by every cache consumer (see JsonCache for the namespace list),
 * so new cached shapes never need a schema change.
 */
@Entity(tableName = "kv_cache")
data class CacheEntryEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM kv_cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntryEntity)

    @Query("DELETE FROM kv_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Age-based sweep; rows whose key matches [keepPrefix] are kept regardless (tiny, non-refetchable offline). */
    @Query("DELETE FROM kv_cache WHERE updatedAt < :cutoff AND `key` NOT LIKE :keepPrefix")
    suspend fun prune(cutoff: Long, keepPrefix: String)
}

/**
 * Disposable cache database, deliberately SEPARATE from anilocal.db: everything in here is
 * re-fetchable, so destructive migration is always safe and a cache schema change can never wipe
 * user data (library/progress/downloads/MAL mirror).
 */
@Database(entities = [CacheEntryEntity::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
