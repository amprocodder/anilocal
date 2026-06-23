package com.anilocal.app.data.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.Sources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A fully LAWFUL built-in source so the app plays video out of the box with no network,
 * no account, and no scraping. Streams a Creative-Commons "Big Buck Bunny" clip (Blender
 * Foundation, CC-BY). Demonstrates the player + skip button end to end.
 *
 * This is the reference implementation of [AnimeSource]; a real source is a drop-in
 * replacement/addition.
 */
@Singleton
class SampleLocalSource @Inject constructor() : AnimeSource {

    override val info = SourceInfo(id = Sources.SAMPLE_ID, name = "Sample (CC clip)", isExternal = false)

    private val sampleMp4 =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private val poster =
        "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg"

    private val demo = listOf(
        AnimeSummary("sample-1", "Big Buck Bunny (demo)", poster),
        AnimeSummary("sample-2", "Sample Series Two (demo)", poster),
    )

    override suspend fun popular(page: Int): List<AnimeSummary> = if (page == 1) demo else emptyList()

    // Demo source: match any query so playback always resolves to the lawful CC clip,
    // regardless of which (AniList) title the user opened.
    override suspend fun search(query: String, page: Int): List<AnimeSummary> = demo

    override suspend fun detail(animeId: String): AnimeDetail = AnimeDetail(
        id = animeId,
        title = demo.firstOrNull { it.id == animeId }?.title ?: "Demo",
        posterUrl = poster,
        synopsis = "Built-in Creative-Commons sample so playback and the skip button work " +
            "with no scraper. Replace SampleLocalSource with a lawful source to go further.",
        genres = listOf("Demo", "Creative Commons"),
        episodes = (1..3).map { Episode(id = "$animeId-ep$it", number = it, title = "Episode $it") },
    )

    override suspend fun servers(episode: Episode): List<VideoServer> =
        listOf(VideoServer(id = "direct", name = "Direct (CC)", episodeId = episode.id))

    // A real source returns a DISTINCT url per quality; the demo reuses the one CC clip but
    // exposes three labels so the quality picker is exercised end to end.
    override suspend fun resolve(server: VideoServer): List<VideoStream> = listOf(
        VideoStream(url = sampleMp4, mimeType = "video/mp4", quality = "1080p", height = 1080),
        VideoStream(url = sampleMp4, mimeType = "video/mp4", quality = "720p", height = 720),
        VideoStream(url = sampleMp4, mimeType = "video/mp4", quality = "480p", height = 480),
    )
}
