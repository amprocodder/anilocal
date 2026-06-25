package com.anilocal.app.data.source

import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges catalog metadata (AniList) to playable streams via the user's selected [AnimeSource]:
 * find the title in the source, match the episode number, resolve a server to its quality
 * variants. The active source is taken from [SourceRegistry] by the persisted selected-source id,
 * falling back to the first available source so playback never depends on a single compile-time
 * binding. Browsing/metadata stay on AniList; only the stream leg routes
 * through the chosen source, so any source — built-in or extension — works with no branching here.
 */
@Singleton
class SourceStreamRepository @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
) : StreamRepository {

    private suspend fun activeSource(): AnimeSource {
        val selected = settings.selectedSourceId.first()
        return registry.get(selected)
            ?: registry.sources.value.firstOrNull()
            ?: error("no stream sources available")
    }

    override suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream> =
        withContext(Dispatchers.IO) {
            val source = activeSource()
            val match = source.search(animeTitle, page = 1).firstOrNull()
                ?: error("source '${source.info.name}': no match for \"$animeTitle\"")
            val detail = source.detail(match.id)
            val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
                ?: detail.episodes.firstOrNull()
                ?: error("source '${source.info.name}': no episodes for \"$animeTitle\"")
            val server = source.servers(episode).firstOrNull()
                ?: error("source '${source.info.name}': no servers for episode of \"$animeTitle\"")
            source.resolve(server).sortedByDescending { it.height ?: 0 }
        }

    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream {
        val variants = resolveStreams(animeTitle, episodeNumber)
        val best = variants.firstOrNull() ?: error("no stream for \"$animeTitle\"")
        // Online playback uses only the top-quality variant, but some sources attach subtitle tracks to
        // a lower rendition (or only some of them). Union every variant's subs onto the chosen stream
        // (deduped by url, best's own first) so captions aren't silently lost to the height sort.
        val mergedSubs = variants.flatMap { it.subtitles }.distinctBy { it.url }
        return if (mergedSubs.size > best.subtitles.size) best.copy(subtitles = mergedSubs) else best
    }
}
