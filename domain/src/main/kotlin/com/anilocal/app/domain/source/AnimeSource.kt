package com.anilocal.app.domain.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream

data class SourceInfo(
    val id: String,
    val name: String,
    val lang: String = "en",
    /** false = ships in-app (e.g. the sample/local source); true = user-installed extension. */
    val isExternal: Boolean = false,
)

/**
 * THE PLUGIN SEAM.
 *
 * Everything content-related goes through this interface. The app itself bundles only the
 * lawful [com.anilocal.app.data.source.SampleLocalSource]. Any other implementation — a
 * personal media server (Jellyfin/Plex), local files, or a third-party extension — is a
 * drop-in here and is the integrator's responsibility. The rest of the app neither knows
 * nor cares where streams come from.
 */
interface AnimeSource {
    val info: SourceInfo

    suspend fun popular(page: Int): List<AnimeSummary>
    suspend fun search(query: String, page: Int): List<AnimeSummary>
    suspend fun detail(animeId: String): AnimeDetail
    suspend fun servers(episode: Episode): List<VideoServer>

    /** Resolve a server to its available quality variants (highest first is conventional). */
    suspend fun resolve(server: VideoServer): List<VideoStream>
}
