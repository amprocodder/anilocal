package com.anilocal.app.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow

// The app's queries, as plain SQL over [Db]. Every reactive query re-runs when a write to its table
// actually changes a row (see Db.write). Inserts use REPLACE: none of these tables has foreign keys
// or triggers, so a replace is indistinguishable from an upsert (and works on SQLite < 3.24).

private fun SQLiteDatabase.replace(table: String, values: ContentValues): Boolean =
    insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L

class LibraryDao internal constructor(private val db: Db) {
    fun observeAll(): Flow<List<LibraryEntity>> = db.observe(T) { d ->
        d.rawQuery("SELECT * FROM library ORDER BY title", null).mapRows(::row)
    }

    fun countById(id: String): Flow<Int> = db.observe(T) { d -> count(d, id) }

    suspend fun countByIdNow(id: String): Int = db.read { d -> count(d, id) }

    suspend fun insert(entity: LibraryEntity) = db.write(T) { d -> d.replace(T, values(entity)) }

    suspend fun delete(entity: LibraryEntity) = deleteById(entity.id)

    suspend fun deleteById(id: String) = db.write(T) { d -> d.delete(T, "id = ?", arrayOf(id)) > 0 }

    /** Adds the title, or removes it when it's already saved — atomically. */
    suspend fun toggle(entity: LibraryEntity) = db.write(T) { d ->
        if (count(d, entity.id) > 0) d.delete(T, "id = ?", arrayOf(entity.id)) > 0
        else d.replace(T, values(entity))
    }

    private fun count(d: SQLiteDatabase, id: String): Int =
        d.rawQuery("SELECT COUNT(*) FROM library WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun values(e: LibraryEntity) = ContentValues().apply {
        put("id", e.id); put("title", e.title); put("posterUrl", e.posterUrl); put("idMal", e.idMal)
    }

    private fun row(c: Cursor) = LibraryEntity(c.str("id"), c.str("title"), c.strOrNull("posterUrl"), c.intOrNull("idMal"))

    private companion object { const val T = "library" }
}

class DownloadDao internal constructor(private val db: Db) {
    fun observeAll(): Flow<List<DownloadEntity>> = db.observe(T) { d ->
        d.rawQuery("SELECT * FROM downloads ORDER BY createdAt DESC", null).mapRows(::row)
    }

    suspend fun getById(id: String): DownloadEntity? = db.read { d ->
        d.rawQuery("SELECT * FROM downloads WHERE id = ?", arrayOf(id)).mapRows(::row).firstOrNull()
    }

    /** Newest not-yet-completed download (0 DOWNLOADING, 3 PAUSED, 4 QUEUED) — its persisted
     *  headers seed DownloadHeaderStore after a process restart so resumes don't 403. Blocking on
     *  purpose: it's called lazily from Media3's download threads. */
    fun newestActiveNow(): DownloadEntity? = db.readNow { d ->
        d.rawQuery("SELECT * FROM downloads WHERE state IN (0, 3, 4) ORDER BY createdAt DESC LIMIT 1", null)
            .mapRows(::row).firstOrNull()
    }

    /** Every active download (0 DOWNLOADING, 3 PAUSED, 4 QUEUED), newest first — seeds
     *  DownloadHeaderStore's per-host headers after a process restart so each concurrent download
     *  (possibly on a different source/host) resumes with its own Referer. Blocking, like
     *  [newestActiveNow]. */
    fun activeNow(): List<DownloadEntity> = db.readNow { d ->
        d.rawQuery("SELECT * FROM downloads WHERE state IN (0, 3, 4) ORDER BY createdAt DESC", null).mapRows(::row)
    }

    fun observeDownloadedAnimeIds(): Flow<List<String>> = db.observe(T) { d ->
        d.rawQuery("SELECT DISTINCT animeId FROM downloads WHERE state = 1", null).mapRows { it.getString(0) }
    }

    fun observeDownloadedEpisodes(animeId: String): Flow<List<Int>> = db.observe(T) { d ->
        d.rawQuery("SELECT episodeNumber FROM downloads WHERE animeId = ? AND state = 1", arrayOf(animeId))
            .mapRows { it.getInt(0) }
    }

    suspend fun upsert(entity: DownloadEntity) = db.write(T) { d -> d.replace(T, values(entity)) }

    suspend fun updateState(id: String, state: Int, progress: Int) = db.write(T) { d ->
        d.update(
            T,
            ContentValues().apply { put("state", state); put("progress", progress) },
            "id = ? AND (state != ? OR progress != ?)",
            arrayOf(id, state.toString(), progress.toString()),
        ) > 0
    }

    /** Progress-only write for the live poll: guarded on `state = 0` (DOWNLOADING) so a tick that
     *  lands just after the row flipped to COMPLETED/FAILED can't reset it back to DOWNLOADING. */
    suspend fun updateProgress(id: String, progress: Int) = db.write(T) { d ->
        d.update(
            T,
            ContentValues().apply { put("progress", progress) },
            "id = ? AND state = 0 AND progress != ?",
            arrayOf(id, progress.toString()),
        ) > 0
    }

    suspend fun deleteById(id: String) = db.write(T) { d -> d.delete(T, "id = ?", arrayOf(id)) > 0 }

    private fun values(e: DownloadEntity) = ContentValues().apply {
        put("id", e.id); put("animeId", e.animeId); put("episodeNumber", e.episodeNumber); put("title", e.title)
        put("posterUrl", e.posterUrl); put("idMal", e.idMal); put("streamUri", e.streamUri); put("mimeType", e.mimeType)
        put("quality", e.quality); put("subtitlesJson", e.subtitlesJson); put("skipMarkersJson", e.skipMarkersJson)
        put("state", e.state); put("progress", e.progress); put("createdAt", e.createdAt); put("headersJson", e.headersJson)
    }

    private fun row(c: Cursor) = DownloadEntity(
        id = c.str("id"),
        animeId = c.str("animeId"),
        episodeNumber = c.int("episodeNumber"),
        title = c.str("title"),
        posterUrl = c.strOrNull("posterUrl"),
        idMal = c.intOrNull("idMal"),
        streamUri = c.str("streamUri"),
        mimeType = c.strOrNull("mimeType"),
        quality = c.strOrNull("quality"),
        subtitlesJson = c.str("subtitlesJson"),
        skipMarkersJson = c.str("skipMarkersJson"),
        state = c.int("state"),
        progress = c.int("progress"),
        createdAt = c.long("createdAt"),
        headersJson = c.strOrNull("headersJson"),
    )

    private companion object { const val T = "downloads" }
}

class MalDao internal constructor(private val db: Db) {
    fun observeByStatus(status: String): Flow<List<MalEntryEntity>> = db.observe(T) { d ->
        d.rawQuery("SELECT * FROM mal_list WHERE status = ? ORDER BY title", arrayOf(status)).mapRows(::row)
    }

    fun observeAll(): Flow<List<MalEntryEntity>> = db.observe(T) { d ->
        d.rawQuery("SELECT * FROM mal_list ORDER BY title", null).mapRows(::row)
    }

    suspend fun upsertAll(entries: List<MalEntryEntity>) = db.write(T) { d -> insertAll(d, entries) }

    suspend fun clear() = db.write(T) { d -> d.delete(T, null, null) > 0 }

    /** Atomic mirror replace — process death between clear and insert can't empty the list. */
    suspend fun replaceAll(entries: List<MalEntryEntity>) = db.write(T) { d ->
        d.delete(T, null, null)
        insertAll(d, entries)
        true
    }

    private fun insertAll(d: SQLiteDatabase, entries: List<MalEntryEntity>): Boolean {
        d.compileStatement(
            "INSERT OR REPLACE INTO mal_list (malId, title, posterUrl, status, score, episodesWatched, totalEpisodes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { st ->
            for (e in entries) {
                st.clearBindings()
                st.bindLong(1, e.malId.toLong())
                st.bindString(2, e.title)
                e.posterUrl?.let { st.bindString(3, it) } ?: st.bindNull(3)
                st.bindString(4, e.status)
                st.bindLong(5, e.score.toLong())
                st.bindLong(6, e.episodesWatched.toLong())
                e.totalEpisodes?.let { st.bindLong(7, it.toLong()) } ?: st.bindNull(7)
                st.executeInsert()
            }
        }
        return entries.isNotEmpty()
    }

    private fun row(c: Cursor) = MalEntryEntity(
        malId = c.int("malId"),
        title = c.str("title"),
        posterUrl = c.strOrNull("posterUrl"),
        status = c.str("status"),
        score = c.int("score"),
        episodesWatched = c.int("episodesWatched"),
        totalEpisodes = c.intOrNull("totalEpisodes"),
    )

    private companion object { const val T = "mal_list" }
}

class ProgressDao internal constructor(private val db: Db) {
    fun observeRecent(limit: Int): Flow<List<WatchProgressEntity>> = db.observe(T) { d ->
        d.rawQuery("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT ?", arrayOf(limit.toString())).mapRows(::row)
    }

    suspend fun upsert(entity: WatchProgressEntity) = db.write(T) { d -> d.replace(T, values(entity)) }

    suspend fun getById(animeId: String): WatchProgressEntity? = db.read { d -> byId(d, animeId) }

    /** Writes [entity] unless the stored row already holds the same progress — atomically. */
    suspend fun upsertIfChanged(entity: WatchProgressEntity) = db.write(T) { d ->
        if (byId(d, entity.animeId)?.sameProgressAs(entity) == true) false else d.replace(T, values(entity))
    }

    suspend fun deleteById(animeId: String) = db.write(T) { d -> d.delete(T, "animeId = ?", arrayOf(animeId)) > 0 }

    private fun byId(d: SQLiteDatabase, animeId: String): WatchProgressEntity? =
        d.rawQuery("SELECT * FROM watch_progress WHERE animeId = ?", arrayOf(animeId)).mapRows(::row).firstOrNull()

    private fun values(e: WatchProgressEntity) = ContentValues().apply {
        put("animeId", e.animeId); put("title", e.title); put("posterUrl", e.posterUrl); put("idMal", e.idMal)
        put("episodeNumber", e.episodeNumber); put("positionMs", e.positionMs); put("durationMs", e.durationMs)
        put("updatedAt", e.updatedAt)
    }

    private fun row(c: Cursor) = WatchProgressEntity(
        animeId = c.str("animeId"),
        title = c.str("title"),
        posterUrl = c.strOrNull("posterUrl"),
        idMal = c.intOrNull("idMal"),
        episodeNumber = c.int("episodeNumber"),
        positionMs = c.long("positionMs"),
        durationMs = c.long("durationMs"),
        updatedAt = c.long("updatedAt"),
    )

    private companion object { const val T = "watch_progress" }
}
