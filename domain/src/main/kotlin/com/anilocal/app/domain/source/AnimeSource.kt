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
    /** false = a built-in source that ships in-app; true = user-installed extension. */
    val isExternal: Boolean = false,
)

/**
 * THE PLUGIN SEAM.
 *
 * Everything content-related goes through this interface. The app bundles no built-in sources;
 * implementations are user-installed Aniyomi/Anikku extensions (mapped onto this seam by the
 * `:extensions` module) — or, as a drop-in, a personal media server (Jellyfin/Plex) or local
 * files. The rest of the app neither knows nor cares where streams come from.
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
