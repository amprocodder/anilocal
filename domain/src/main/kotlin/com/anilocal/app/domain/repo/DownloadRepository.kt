package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.DownloadItem
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.OfflineEpisode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.VideoStream
import kotlinx.coroutines.flow.Flow

/**
 * In-app downloads (Netflix-style): bytes go to app storage via Media3's DownloadManager,
 * subtitles + skip markers are cached alongside, and everything is queryable offline from
 * Room. The UI reads only Room, so the Downloads tab and badges work with no network.
 */
interface DownloadRepository {
    val downloads: Flow<List<DownloadItem>>
    fun downloadedAnimeIds(): Flow<Set<String>>
    fun downloadedEpisodes(animeId: String): Flow<Set<Int>>

    suspend fun enqueue(
        detail: AnimeDetail,
        episode: Episode,
        stream: VideoStream,
        markers: List<SkipMarker>,
    )

    /** Offline playback bundle for a downloaded episode, or null if not (fully) downloaded. */
    suspend fun getOffline(animeId: String, episodeNumber: Int): OfflineEpisode?

    fun pause(id: String)
    fun resume(id: String)

    /**
     * Retry a FAILED download from scratch: re-resolve a FRESH stream URL from the active source
     * (the original one is typically an expired token by the time a queued episode reaches a
     * download slot) at the closest match to the originally chosen quality, refetch subtitles,
     * and re-add the download. Returns false when the row no longer exists or re-resolution
     * found nothing — the row then stays FAILED for a later retry. Failed downloads are also
     * auto-retried this way a bounded number of times per process.
     */
    suspend fun retry(id: String): Boolean

    /** Cancel an in-flight download or delete a completed one. */
    suspend fun remove(id: String)
}
