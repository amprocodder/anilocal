package com.anilocal.app.data.source

import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges catalog metadata (AniList) to playable streams via the active [AnimeSource]:
 * find the title in the source, match the episode number, resolve a server to its quality
 * variants. With the bundled [SampleLocalSource] this yields the lawful CC clip in a few
 * quality labels; a real source would return distinct per-quality URLs.
 */
@Singleton
class SourceStreamRepository @Inject constructor(
    private val source: AnimeSource,
) : StreamRepository {

    override suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream> =
        withContext(Dispatchers.IO) {
            val match = source.search(animeTitle, page = 1).firstOrNull()
                ?: error("source '${source.info.name}': no match for \"$animeTitle\"")
            val detail = source.detail(match.id)
            val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
                ?: detail.episodes.firstOrNull()
                ?: error("source '${source.info.name}': no episodes for \"$animeTitle\"")
            val server = source.servers(episode).first()
            source.resolve(server).sortedByDescending { it.height ?: 0 }
        }

    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream =
        resolveStreams(animeTitle, episodeNumber).firstOrNull()
            ?: error("source '${source.info.name}': no stream for \"$animeTitle\"")
}
