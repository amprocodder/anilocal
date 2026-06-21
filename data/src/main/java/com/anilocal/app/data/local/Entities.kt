package com.anilocal.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String?,
    val idMal: Int?,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,           // "<animeId>-ep<n>"
    val animeId: String,
    val episodeNumber: Int,
    val title: String,
    val posterUrl: String?,
    val idMal: Int?,
    val streamUri: String,
    val mimeType: String?,
    val quality: String?,                 // chosen quality label, e.g. "720p"
    val subtitlesJson: String,            // List<Subtitle> with local file:// urls
    val skipMarkersJson: String,          // List<SkipMarker>
    val state: Int,                       // 0 DOWNLOADING, 1 COMPLETED, 2 FAILED
    val progress: Int,
    val createdAt: Long,
)

@Entity(tableName = "mal_list")
data class MalEntryEntity(
    @PrimaryKey val malId: Int,
    val title: String,
    val posterUrl: String?,
    val status: String,                   // MAL api status string
    val score: Int,
    val episodesWatched: Int,
    val totalEpisodes: Int?,
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val posterUrl: String?,
    val idMal: Int?,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
