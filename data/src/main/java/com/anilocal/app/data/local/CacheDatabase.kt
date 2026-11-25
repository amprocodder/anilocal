package com.anilocal.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * One generic key → JSON row. Namespaced keys ("detail:127230", "skip:5114:3", "extrepo:<url>", …)
 * keep this a single table shared by every cache consumer (see JsonCache for the namespace list),
 * so new cached shapes never need a schema change.
 */
data class CacheEntryEntity(
    val key: String,
    val json: String,
    val updatedAt: Long,
)

class CacheDao internal constructor(private val db: Db) {
    suspend fun get(key: String): CacheEntryEntity? = db.read { d ->
        d.rawQuery("SELECT * FROM kv_cache WHERE `key` = ?", arrayOf(key)).mapRows(::row).firstOrNull()
    }

    suspend fun put(entry: CacheEntryEntity) = db.write(T) { d ->
        d.insertWithOnConflict(
            T,
            null,
            ContentValues().apply { put("key", entry.key); put("json", entry.json); put("updatedAt", entry.updatedAt) },
            SQLiteDatabase.CONFLICT_REPLACE,
        ) != -1L
    }

    suspend fun delete(key: String) = db.write(T) { d -> d.delete(T, "`key` = ?", arrayOf(key)) > 0 }

    /** All rows whose key matches [pattern] (a SQL LIKE, e.g. "bestsrc:%") — used to enumerate a
     *  namespace (the per-title source pins) that has no other index. */
    suspend fun entriesLike(pattern: String): List<CacheEntryEntity> = db.read { d ->
        d.rawQuery("SELECT * FROM kv_cache WHERE `key` LIKE ?", arrayOf(pattern)).mapRows(::row)
    }

    /** Age-based sweep; rows whose key matches [keepPrefix] are kept regardless (tiny, non-refetchable offline). */
    suspend fun prune(cutoff: Long, keepPrefix: String) = db.write(T) { d ->
        d.delete(T, "updatedAt < ? AND `key` NOT LIKE ?", arrayOf(cutoff.toString(), keepPrefix)) > 0
    }

    private fun row(c: Cursor) = CacheEntryEntity(c.str("key"), c.str("json"), c.long("updatedAt"))

    private companion object { const val T = "kv_cache" }
}

/**
 * Disposable cache database (`anilocal-cache.db`), deliberately SEPARATE from anilocal.db: everything
 * in here is re-fetchable, so a schema change simply drops and recreates it — it can never touch user
 * data (library/progress/downloads/MAL mirror).
 */
class CacheDatabase internal constructor(context: Context) {
    val cacheDao = CacheDao(
        Db(
            context,
            "anilocal-cache.db",
            Db.Schema(
                version = 1,
                identityHash = "e3e188d0f0fe5d97019da077dbd0178c",
                create = listOf(
                    "CREATE TABLE IF NOT EXISTS `kv_cache` (`key` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                ),
                destructive = true,
            ),
        ),
    )
}
