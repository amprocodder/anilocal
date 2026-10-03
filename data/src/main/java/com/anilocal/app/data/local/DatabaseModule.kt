package com.anilocal.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v5: per-download request headers, so resumed downloads survive a process restart. */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN headersJson TEXT")
        }
    }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "anilocal.db")
            // This database holds user data. An unsupported schema must fail explicitly rather
            // than erase the library, progress, downloads, or MAL mirror during an update.
            .addMigrations(MIGRATION_4_5)
            .build()

    /** The disposable cache DB (see [CacheDatabase]) — destructive fallback is always safe here. */
    @Provides @Singleton
    fun cacheDatabase(@ApplicationContext context: Context): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "anilocal-cache.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun libraryDao(db: AppDatabase): LibraryDao = db.libraryDao()
    @Provides fun progressDao(db: AppDatabase): ProgressDao = db.progressDao()
    @Provides fun downloadDao(db: AppDatabase): DownloadDao = db.downloadDao()
    @Provides fun malDao(db: AppDatabase): MalDao = db.malDao()
    @Provides fun cacheDao(db: CacheDatabase): CacheDao = db.cacheDao()
}
