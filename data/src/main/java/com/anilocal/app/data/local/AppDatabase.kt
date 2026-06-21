package com.anilocal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LibraryEntity::class, WatchProgressEntity::class, DownloadEntity::class, MalEntryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun progressDao(): ProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun malDao(): MalDao
}
