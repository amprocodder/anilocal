package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.VideoStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/** Catalog/browse/detail — backed by AniList (public metadata), not the stream source. */
interface CatalogRepository {
    /** Implementations can batch all shelves into one request. Failed shelves stay empty. */
    suspend fun home(): HomeCatalog = supervisorScope {
        suspend fun shelf(load: suspend () -> List<AnimeSummary>) = try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val trending = async { shelf { trending() } }
        val seasonal = async { shelf { popularThisSeason() } }
        val airing = async { shelf { topAiring() } }
        val popular = async { shelf { allTimePopular() } }
        val upcoming = async { shelf { upcoming() } }
        HomeCatalog(trending.await(), seasonal.await(), airing.await(), popular.await(), upcoming.await())
    }

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

    /** Map a MAL anime id to its AniList id (to open MAL-synced items in the detail page). */
    suspend fun anilistIdForMal(malId: Int): String?
}

/** Resolves playable streams for a title+episode via registered AnimeSource plugins. */
interface StreamRepository {
    /** Highest-quality stream from the selected source, without a speed test. */
    suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream

    /** All available quality variants (highest first) — used by the download quality picker. */
    suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream>

    /**
     * Resolve fresh URLs from all available sources/servers and measure media download speed.
     * A healthy alternative to [failedStreamUrl] is preferred when recovering playback.
     */
    suspend fun resolveFastestStream(
        animeTitle: String,
        episodeNumber: Int,
        failedStreamUrl: String? = null,
    ): VideoStream
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
    /** Recently-watched items, most recent first, each carrying its resume point. */
    val continueWatching: Flow<List<ContinueWatching>>
    suspend fun save(anime: AnimeSummary, episodeNumber: Int, positionMs: Long, durationMs: Long)
    /** Manually drop an anime from Continue Watching (permanent, unlike dismissing the resume bar). */
    suspend fun remove(animeId: String)
}
