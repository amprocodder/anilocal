package com.anilocal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LibraryEntity::class, WatchProgressEntity::class, DownloadEntity::class, MalEntryEntity::class],
    version = 5,   // bumps MUST ship a Migration in DatabaseModule — this DB holds user data
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun progressDao(): ProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun malDao(): MalDao
}
