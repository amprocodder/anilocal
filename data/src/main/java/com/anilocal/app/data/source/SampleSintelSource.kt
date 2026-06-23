package com.anilocal.app.data.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A SECOND lawful built-in source (Blender's "Sintel", CC-BY) that exists purely to prove the
 * multi-source registry + picker route correctly: selecting it instead of [SampleLocalSource]
 * changes the played clip, with no scraper involved. Like the sample, it matches any query so
 * playback always resolves against an AniList-browsed title.
 */
@Singleton
class SampleSintelSource @Inject constructor() : AnimeSource {

    override val info = SourceInfo(id = "sample-sintel", name = "Sample · Sintel (CC)", isExternal = false)

    private val sintelMp4 =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    private val poster =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg"

    private val demo = listOf(
        AnimeSummary("sintel-1", "Sintel (demo)", poster),
    )

    override suspend fun popular(page: Int): List<AnimeSummary> = if (page == 1) demo else emptyList()

    override suspend fun search(query: String, page: Int): List<AnimeSummary> = demo

    override suspend fun detail(animeId: String): AnimeDetail = AnimeDetail(
        id = animeId,
        title = "Sintel (demo)",
        posterUrl = poster,
        synopsis = "Second built-in Creative-Commons source (Blender 'Sintel', CC-BY). Exists to " +
            "demonstrate that the source picker routes stream resolution to the selected source.",
        genres = listOf("Demo", "Creative Commons"),
        episodes = (1..2).map { Episode(id = "$animeId-ep$it", number = it, title = "Episode $it") },
    )

    override suspend fun servers(episode: Episode): List<VideoServer> =
        listOf(VideoServer(id = "direct", name = "Direct (CC)", episodeId = episode.id))

    override suspend fun resolve(server: VideoServer): List<VideoStream> = listOf(
        VideoStream(url = sintelMp4, mimeType = "video/mp4", quality = "1080p", height = 1080),
        VideoStream(url = sintelMp4, mimeType = "video/mp4", quality = "720p", height = 720),
    )
}
