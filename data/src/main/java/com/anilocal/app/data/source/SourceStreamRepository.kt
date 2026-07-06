package com.anilocal.app.data.source

import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.CancellationException
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
 *
 * The title-match half of the pipeline (search → detail, i.e. "which source entry is this AniList
 * title, and what are its episode ids") is cached per source for [MATCH_TTL_MS]: it's the slow,
 * Cloudflare-challenged part, it's stable across episodes, and skipping it turns an episode switch
 * or a season download from 4+ round-trips into 2. Server/stream URLs are NOT cached — they're
 * short-lived/tokenized. A cached match that no longer resolves (source rotated its ids) is
 * dropped and the whole pipeline re-runs live once.
 */
@Singleton
class SourceStreamRepository @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val cache: JsonCache,
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
            val key = "src:${source.info.id}:${animeTitle.trim().lowercase()}"

            // A fresh cached match is only trusted when it actually carries the requested episode —
            // an episode that aired after the entry was cached must force a live re-fetch.
            val cached = cache.getFresh<AnimeDetail>(key, DETAIL, MATCH_TTL_MS)
            if (cached != null && cached.episodes.any { it.number == episodeNumber }) {
                try {
                    return@withContext resolveFrom(source, cached, episodeNumber)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // Could be rotated ids OR a transient/offline error — the adapter flattens both
                    // to empty. Don't evict: fall through to a live run, which overwrites the entry
                    // on success and leaves it intact when the failure was transient.
                }
            }

            val detail = liveMatch(source, animeTitle)
            if (detail.episodes.isNotEmpty()) cache.put(key, DETAIL, detail)
            resolveFrom(source, detail, episodeNumber)
        }

    /** The un-cached search → detail leg (throws with a sourced message, as before). */
    private suspend fun liveMatch(source: AnimeSource, animeTitle: String): AnimeDetail {
        val match = source.search(animeTitle, page = 1).firstOrNull()
            ?: error("source '${source.info.name}': no match for \"$animeTitle\"")
        return source.detail(match.id)
    }

    /** Episode pick → servers → variants. Throws on any empty leg so callers (and the cached-match
     *  fallback above) see one consistent failure shape. */
    private suspend fun resolveFrom(source: AnimeSource, detail: AnimeDetail, episodeNumber: Int): List<VideoStream> {
        val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
            ?: detail.episodes.firstOrNull()
            ?: error("source '${source.info.name}': no episodes for \"${detail.title}\"")
        val server = source.servers(episode).firstOrNull()
            ?: error("source '${source.info.name}': no servers for episode of \"${detail.title}\"")
        val variants = source.resolve(server).sortedByDescending { it.height ?: 0 }
        if (variants.isEmpty()) error("source '${source.info.name}': no streams for episode $episodeNumber of \"${detail.title}\"")
        return variants
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

    private companion object {
        val DETAIL: java.lang.reflect.Type = AnimeDetail::class.java
        const val MATCH_TTL_MS = 4L * 60 * 60 * 1000   // episode lists grow weekly; 4h keeps them current
    }
}
