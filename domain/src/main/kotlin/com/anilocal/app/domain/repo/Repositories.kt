package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.VideoStream
import kotlinx.coroutines.flow.Flow

/** Catalog/browse/detail — backed by AniList (public metadata), not the stream source. */
interface CatalogRepository {
    suspend fun popular(page: Int = 1): List<AnimeSummary>
    suspend fun search(query: String): List<AnimeSummary>
    suspend fun detail(animeId: String): AnimeDetail

    // Home rows
    suspend fun trending(page: Int = 1): List<AnimeSummary>
    suspend fun popularThisSeason(page: Int = 1): List<AnimeSummary>
    suspend fun topAiring(page: Int = 1): List<AnimeSummary>
    suspend fun allTimePopular(page: Int = 1): List<AnimeSummary>
    suspend fun upcoming(page: Int = 1): List<AnimeSummary>

    // Explore grid
    suspend fun browse(genre: String?, sort: BrowseSort, page: Int = 1): List<AnimeSummary>
}

/** Resolves a playable stream for a title+episode via the active AnimeSource plugin. */
interface StreamRepository {
    /** Best single stream (highest quality) — used for online playback. */
    suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream

    /** All available quality variants (highest first) — used by the download quality picker. */
    suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream>
}

/** Opening/ending skip windows — AniSkip-backed. */
interface SkipRepository {
    suspend fun markers(idMal: Int?, episodeNumber: Int, episodeLengthSec: Long): List<SkipMarker>
}

/** "My List" — Room-backed. */
interface LibraryRepository {
    val library: Flow<List<AnimeSummary>>
    suspend fun toggle(item: AnimeSummary)
    fun isSaved(id: String): Flow<Boolean>
}

/** "Continue Watching" — Room-backed watch progress. */
interface ProgressRepository {
    val continueWatching: Flow<List<AnimeSummary>>
    suspend fun save(anime: AnimeSummary, episodeNumber: Int, positionMs: Long, durationMs: Long)
}
