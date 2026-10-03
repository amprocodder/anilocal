package com.anilocal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    suspend fun toggle(entity: LibraryEntity) {
        if (countByIdNow(entity.id) > 0) deleteById(entity.id) else insert(entity)
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT id, createdAt FROM downloads WHERE state = 1")
    suspend fun getCompletedRows(): List<CompletedDownloadRow>

    @Query("UPDATE downloads SET state = 2, progress = 0 WHERE id = :id AND state = 1 AND createdAt = :createdAt")
    suspend fun invalidateCompleted(id: String, createdAt: Long)

    @Query("SELECT DISTINCT animeId FROM downloads WHERE state = 1")
    fun observeDownloadedAnimeIds(): Flow<List<String>>

    @Query("SELECT episodeNumber FROM downloads WHERE animeId = :animeId AND state = 1")
    fun observeDownloadedEpisodes(animeId: String): Flow<List<Int>>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE id = :id AND (state != :state OR progress != :progress)")
    suspend fun updateState(id: String, state: Int, progress: Int)

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** One invalidation per sampled batch; unchanged rows do not generate writes. */
    @Transaction
    suspend fun applyChanges(updates: List<DownloadStateUpdate>, removedIds: List<String>) {
        updates.forEach { updateState(it.id, it.state, it.progress) }
        if (removedIds.isNotEmpty()) deleteByIds(removedIds)
    }

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

    @Transaction
    suspend fun replaceAll(entries: List<MalEntryEntity>) {
        clear()
        if (entries.isNotEmpty()) upsertAll(entries)
    }
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchProgressEntity>>

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE animeId = :animeId")
    suspend fun getById(animeId: String): WatchProgressEntity?

    @Transaction
    suspend fun upsertIfChanged(entity: WatchProgressEntity) {
        if (getById(entity.animeId)?.sameProgressAs(entity) != true) upsert(entity)
    }

    @Query("DELETE FROM watch_progress WHERE animeId = :animeId")
    suspend fun deleteById(animeId: String)
}
