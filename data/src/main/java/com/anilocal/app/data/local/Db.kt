package com.anilocal.app.data.local

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One SQLite database file — what Room did for this app, written out over the platform's own
 * [SQLiteOpenHelper]:
 * - **schema**: [Schema.create] builds a fresh file at [Schema.version]; an older file is brought up
 *   one version at a time through [Schema.migrations]. A version with no migration fails loudly for
 *   user data (never silently wipes the library/progress/downloads), or, for a [Schema.destructive]
 *   cache, drops and recreates. Room's identity hash is kept in `room_master_table`, so an older
 *   Room-based build installed over this still opens the same file.
 * - **reactive queries**: [observe] re-runs its query whenever a [write] touched one of its tables
 *   (a write reports whether it changed anything — a no-op write wakes nobody).
 * - **threads**: reads/writes run on the IO pool; [transaction] is the blocking form for callers
 *   already on a background thread (Media3's download threads). WAL is on, as Room had it, so
 *   readers don't block on the writer.
 */
internal class Db(context: Context, name: String, private val schema: Schema) {

    class Schema(
        val version: Int,
        /** Room's identity hash for this schema (see class KDoc). */
        val identityHash: String,
        val create: List<String>,
        /** `from` version → the statements that bring it to `from + 1`. */
        val migrations: Map<Int, (SQLiteDatabase) -> Unit> = emptyMap(),
        val destructive: Boolean = false,
    )

    private val helper = object : SQLiteOpenHelper(context.applicationContext, name, null, schema.version) {
        override fun onCreate(db: SQLiteDatabase) = create(db)

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            for (from in oldVersion until newVersion) {
                val step = schema.migrations[from]
                when {
                    step != null -> step(db)
                    schema.destructive -> { recreate(db); return }
                    else -> error("$name: no migration from version $from to ${from + 1}")
                }
            }
            stampIdentity(db)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (schema.destructive) recreate(db)
            else error("$name is version $oldVersion, newer than this app understands ($newVersion)")
        }
    }.apply { setWriteAheadLoggingEnabled(true) }

    private val changes = MutableSharedFlow<Set<String>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun create(db: SQLiteDatabase) {
        schema.create.forEach(db::execSQL)
        stampIdentity(db)
    }

    private fun stampIdentity(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)", arrayOf(schema.identityHash))
    }

    private fun recreate(db: SQLiteDatabase) {
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('android_metadata', 'sqlite_sequence')",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        tables.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        create(db)
    }

    /** A query that re-runs whenever a [write] to any of [tables] changes something. */
    fun <T> observe(vararg tables: String, query: (SQLiteDatabase) -> T): Flow<T> = channelFlow {
        val watched = tables.toSet()
        val dirty = Channel<Unit>(Channel.CONFLATED)
        // Subscribe BEFORE the first read, so a write landing in between still triggers a re-read.
        launch(start = CoroutineStart.UNDISPATCHED) {
            changes.collect { touched -> if (touched.any(watched::contains)) dirty.trySend(Unit) }
        }
        dirty.trySend(Unit)
        for (signal in dirty) send(read(query))
    }

    suspend fun <T> read(query: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { query(helper.readableDatabase) }

    /** Runs [block] in a transaction on the IO pool; when it returns true, observers of [tables] re-read. */
    suspend fun write(vararg tables: String, block: (SQLiteDatabase) -> Boolean) {
        withContext(Dispatchers.IO) {
            // Published with the commit, not after withContext returns: a caller cancelled during
            // the transaction still committed it, and withContext would then throw before an emit
            // after it ran. (tryEmit never fails here: the buffer drops its oldest instead.)
            if (transaction(block)) changes.tryEmit(tables.toSet())
        }
    }

    /** Blocking transaction, for callers already on a background thread. */
    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            return block(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    /** Blocking read, for callers already on a background thread. */
    fun <T> readNow(query: (SQLiteDatabase) -> T): T = query(helper.readableDatabase)
}

internal inline fun <T> Cursor.mapRows(row: (Cursor) -> T): List<T> = use {
    buildList(count) { while (moveToNext()) add(row(this@mapRows)) }
}

internal fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
internal fun Cursor.strOrNull(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
internal fun Cursor.intOrNull(column: String): Int? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
