package com.anilocal.app.extensions

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.kanade.tachiyomi.animesource.AnimeSource as AniyomiSource

/**
 * Adapts a dynamically-loaded Aniyomi [AniyomiSource] onto AniLocal's [AnimeSource] seam so any
 * installed extension can resolve streams for AniList-browsed titles. AniList stays the browse layer
 * — [popular]/[search] here are only used by `SourceStreamRepository` to find the source's match for
 * an AniList title, then `detail → servers → resolve` returns the playable variants.
 *
 * Mapping notes:
 * - Aniyomi ids are opaque urls/Longs; AniLocal ids are Strings. The source id is `"aniyomi:<id>"`;
 *   an [AnimeSummary]/[AnimeDetail] id is the `SAnime.url`; an [Episode] id packs the `SEpisode.url`
 *   and its `Float` `episode_number` so [servers]/[resolve] can rebuild the exact [SEpisode].
 * - Episodes keep AniLocal's Int ordinal: the source list is sorted ascending by `episode_number`
 *   and assigned 1..N, so fractional/recap episodes fold into the ordinal sequence (no Float model).
 * - Both video paths are handled: the classic `getVideoList(episode)` and the ext-lib 16 two-step
 *   `getHosterList(episode)` → `getVideoList(hoster)`. A source without hosters yields one synthetic
 *   server (index −1). Every source call is wrapped so a failing extension degrades to empty, not a
 *   crash (matches the app's silent-degrade convention).
 */
class AniyomiSourceAdapter(private val src: AniyomiSource) : AnimeSource {

    override val info = SourceInfo(
        id = SOURCE_PREFIX + src.id,
        name = src.name,
        lang = src.lang.ifEmpty { "en" },
        isExternal = true,
    )

    override suspend fun popular(page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val cat = src as? AnimeCatalogueSource ?: return@withContext emptyList()
        runCatching { cat.getPopularAnime(page).animes.map { it.toSummary() } }.getOrDefault(emptyList())
    }

    override suspend fun search(query: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val cat = src as? AnimeCatalogueSource ?: return@withContext emptyList()
        runCatching { cat.getSearchAnime(page, query, AnimeFilterList()).animes.map { it.toSummary() } }
            .getOrDefault(emptyList())
    }

    override suspend fun detail(animeId: String): AnimeDetail = withContext(Dispatchers.IO) {
        val seed = SAnime.create().apply { url = animeId; title = "" }
        val full = runCatching { src.getAnimeDetails(seed) }.getOrDefault(seed)
        val episodes = runCatching { src.getEpisodeList(seed) }.getOrDefault(emptyList())
        AnimeDetail(
            id = animeId,
            title = full.title.ifBlank { animeId },
            posterUrl = full.thumbnail_url,
            bannerUrl = full.background_url,
            synopsis = full.description.orEmpty(),
            genres = full.getGenres() ?: emptyList(),
            idMal = null,
            episodes = episodes.toDomainEpisodes(),
        )
    }

    override suspend fun servers(episode: Episode): List<VideoServer> = withContext(Dispatchers.IO) {
        val sEpisode = episode.id.toSEpisode()
        val hosters = runCatching { src.getHosterList(sEpisode) }.getOrDefault(emptyList())
        if (hosters.isNotEmpty()) {
            hosters.mapIndexed { i, h ->
                VideoServer(
                    id = encodeServer(episode.id, i),
                    name = h.hosterName.ifBlank { "Server ${i + 1}" },
                    episodeId = episode.id,
                )
            }
        } else {
            listOf(VideoServer(id = encodeServer(episode.id, -1), name = src.name, episodeId = episode.id))
        }
    }

    override suspend fun resolve(server: VideoServer): List<VideoStream> = withContext(Dispatchers.IO) {
        val sEpisode = server.episodeId.toSEpisode()
        val index = decodeServerIndex(server.id)
        val videos = if (index >= 0) {
            val hoster = runCatching { src.getHosterList(sEpisode) }.getOrDefault(emptyList()).getOrNull(index)
            when {
                hoster == null -> emptyList()
                hoster.videoList != null && !hoster.lazy -> hoster.videoList!!
                else -> runCatching { src.getVideoList(hoster) }.getOrDefault(emptyList())
            }
        } else {
            runCatching { src.getVideoList(sEpisode) }.getOrDefault(emptyList())
        }
        videos.map { it.toVideoStream() }
    }

    // ---- model mapping ----

    private fun SAnime.toSummary() = AnimeSummary(id = url, title = title, posterUrl = thumbnail_url)

    private fun List<SEpisode>.toDomainEpisodes(): List<Episode> =
        sortedBy { it.episode_number }.mapIndexed { i, se ->
            Episode(
                id = encodeEpisode(se.url, se.episode_number),
                number = i + 1,
                title = se.name.ifBlank { null },
                thumbnailUrl = se.preview_url,
            )
        }

    private fun Video.toVideoStream() = VideoStream(
        url = videoUrl,
        mimeType = inferStreamMime(videoUrl),
        headers = headers?.toMap() ?: emptyMap(),
        subtitles = subtitleTracks.map { Subtitle(url = it.url, language = it.lang, label = it.lang) },
        quality = videoTitle.ifBlank { resolution?.let { "${it}p" } },
        height = resolution,
    )

    // Aniyomi's Video carries no format field, so infer the container from the URL. "m3u8" is a
    // distinctive token, so match it anywhere and WITHOUT the dot — some extensions serve via a local
    // proxy whose path is the bare word, e.g. "http://localhost:44311/m3u8?url=<encoded>" (Animetsu)
    // or ".../master.m3u8?token=…". DASH keeps the leading "." since bare "mpd" would false-match
    // ordinary words (e.g. "tempdir"). null lets Media3 sniff a progressive container (mp4/mkv/…).
    private fun inferStreamMime(url: String): String? = when {
        url.contains("m3u8", ignoreCase = true) -> HLS_MIME
        url.contains(".mpd", ignoreCase = true) -> DASH_MIME
        else -> null
    }

    private fun String.toSEpisode(): SEpisode {
        val sep = lastIndexOf(EP)
        val epUrl = if (sep < 0) this else substring(0, sep)
        val number = if (sep < 0) 1f else substring(sep + 1).toFloatOrNull() ?: 1f
        return SEpisode.create().apply {
            url = epUrl
            episode_number = number
        }
    }

    companion object {
        const val SOURCE_PREFIX = "aniyomi:"
        private const val HLS_MIME = "application/x-mpegURL"
        private const val DASH_MIME = "application/dash+xml"

        // Control-char delimiters that never appear in URLs/episode names.
        private const val EP = '\u0001' // SEpisode url / episode_number, packed into Episode.id
        private const val SV = '\u0002' // hoster index / episodeId, packed into VideoServer.id

        private fun encodeEpisode(url: String, number: Float): String = "$url$EP$number"
        private fun encodeServer(episodeId: String, index: Int): String = "$index$SV$episodeId"

        private fun decodeServerIndex(serverId: String): Int {
            val i = serverId.indexOf(SV)
            return if (i < 0) -1 else serverId.substring(0, i).toIntOrNull() ?: -1
        }
    }
}
