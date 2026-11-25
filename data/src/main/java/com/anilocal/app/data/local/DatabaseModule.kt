package com.anilocal.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Opens the two Room databases (see DataGraph). */
internal object DatabaseModule {

    /** v5: per-download request headers, so resumed downloads survive a process restart. */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN headersJson TEXT")
        }
    }

    fun database(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "anilocal.db")
            // This database holds user data. An unsupported schema must fail explicitly rather
            // than erase the library, progress, downloads, or MAL mirror during an update.
            .addMigrations(MIGRATION_4_5)
            .build()

    /** The disposable cache DB (see [CacheDatabase]) — destructive fallback is always safe here. */
    fun cacheDatabase(context: Context): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "anilocal-cache.db")
            .fallbackToDestructiveMigration()
            .build()
}
