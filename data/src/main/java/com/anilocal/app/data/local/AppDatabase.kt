package com.anilocal.app.data.local

import android.content.Context

/**
 * `anilocal.db` — the user's data: library, watch progress, downloads, the MAL mirror. Version 5.
 * Every schema change MUST ship a migration in [SCHEMA] (this file holds user data; an unknown
 * version fails loudly instead of wiping it). The CREATE statements are exactly what Room generated
 * for this schema, and [Db] keeps Room's identity hash, so the file is interchangeable with older
 * Room-based builds.
 */
class AppDatabase internal constructor(context: Context) {
    private val db = Db(context, "anilocal.db", SCHEMA)

    val libraryDao = LibraryDao(db)
    val progressDao = ProgressDao(db)
    val downloadDao = DownloadDao(db)
    val malDao = MalDao(db)

    private companion object {
        val SCHEMA = Db.Schema(
            version = 5,
            identityHash = "3b0ee363784239ad7014a60988621833",
            create = listOf(
                "CREATE TABLE IF NOT EXISTS `library` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, " +
                    "`idMal` INTEGER, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `watch_progress` (`animeId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`posterUrl` TEXT, `idMal` INTEGER, `episodeNumber` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`animeId`))",
                "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `animeId` TEXT NOT NULL, " +
                    "`episodeNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, `idMal` INTEGER, " +
                    "`streamUri` TEXT NOT NULL, `mimeType` TEXT, `quality` TEXT, `subtitlesJson` TEXT NOT NULL, " +
                    "`skipMarkersJson` TEXT NOT NULL, `state` INTEGER NOT NULL, `progress` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `headersJson` TEXT, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `mal_list` (`malId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`posterUrl` TEXT, `status` TEXT NOT NULL, `score` INTEGER NOT NULL, `episodesWatched` INTEGER NOT NULL, " +
                    "`totalEpisodes` INTEGER, PRIMARY KEY(`malId`))",
            ),
            migrations = mapOf(
                // v5: per-download request headers, so resumed downloads survive a process restart.
                4 to { d -> d.execSQL("ALTER TABLE downloads ADD COLUMN headersJson TEXT") },
            ),
        )
    }
}
