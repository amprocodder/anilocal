package com.anilocal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library ORDER BY title")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT COUNT(*) FROM library WHERE id = :id")
    fun countById(id: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM library WHERE id = :id")
    suspend fun countByIdNow(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LibraryEntity)

    @Delete
    suspend fun delete(entity: LibraryEntity)

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT DISTINCT animeId FROM downloads WHERE state = 1")
    fun observeDownloadedAnimeIds(): Flow<List<String>>

    @Query("SELECT episodeNumber FROM downloads WHERE animeId = :animeId AND state = 1")
    fun observeDownloadedEpisodes(animeId: String): Flow<List<Int>>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE id = :id")
    suspend fun updateState(id: String, state: Int, progress: Int)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MalDao {
    @Query("SELECT * FROM mal_list WHERE status = :status ORDER BY title")
    fun observeByStatus(status: String): Flow<List<MalEntryEntity>>

    @Query("SELECT * FROM mal_list ORDER BY title")
    fun observeAll(): Flow<List<MalEntryEntity>>

    @Upsert
    suspend fun upsertAll(entries: List<MalEntryEntity>)

    @Query("DELETE FROM mal_list")
    suspend fun clear()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchProgressEntity>>

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)
}
