package com.anilocal.app.data.source

import android.os.SystemClock
import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.domain.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val selector: AutoSourceSelector,
) : StreamRepository {

    /** Resolve the selected manual source, waiting briefly for the async extension scan to populate
     *  the registry before falling back to the first available source (a resolve fired right after
     *  cold start would otherwise see an empty registry and fail spuriously). */
    private suspend fun sourceFor(selectedId: String): AnimeSource {
        registry.get(selectedId)?.let { return it }
        val available = withTimeoutOrNull(REGISTRY_WAIT_MS) { registry.sources.first { it.isNotEmpty() } }
        return available?.firstOrNull() ?: error("no stream sources available")
    }

    override suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream> =
        withContext(Dispatchers.IO) {
            val selected = settings.selectedSourceId.first()
            if (selected == Sources.AUTO) {
                // Auto mode: race installed sources / reuse the pinned winner. The per-source probe is
                // exactly the manual pipeline below, so caching and failure shape are identical.
                selector.resolve(animeTitle, episodeNumber) { source ->
                    resolveVia(source, animeTitle, episodeNumber)
                }.streams
            } else {
                val source = sourceFor(selected)
                // Time the manual resolve into the same scoreboard Auto reads, so switching to Auto
                // later starts warm instead of cold.
                val start = SystemClock.elapsedRealtime()
                try {
                    val r = resolveVia(source, animeTitle, episodeNumber)
                    selector.record(source.info.id, SystemClock.elapsedRealtime() - start, success = true)
                    r.streams
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    selector.record(source.info.id, SystemClock.elapsedRealtime() - start, success = false)
                    throw e
                }
            }
        }

    /** Resolve ONE source end-to-end (the cached-match fast path + a live fallback), reporting
     *  whether the matched entry actually carried the requested episode. This is the unit the auto
     *  selector races and the manual path runs directly — so there is exactly one pipeline. */
    private suspend fun resolveVia(source: AnimeSource, animeTitle: String, episodeNumber: Int): SourceResolution {
        val key = "src:${source.info.id}:${animeTitle.trim().lowercase()}"

        // A fresh cached match is only trusted when it actually carries the requested episode —
        // an episode that aired after the entry was cached must force a live re-fetch.
        val cached = cache.getFresh<AnimeDetail>(key, DETAIL, MATCH_TTL_MS)
        if (cached != null && cached.episodes.any { it.number == episodeNumber }) {
            try {
                return SourceResolution(resolveFrom(source, cached, episodeNumber), exactEpisode = true)
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
        val exact = detail.episodes.any { it.number == episodeNumber }
        return SourceResolution(resolveFrom(source, detail, episodeNumber), exactEpisode = exact)
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
        // Some sources attach subtitle tracks to only one rendition (often a lower one). Union every
        // variant's subs onto EVERY variant (deduped by url, the variant's own first so the default
        // track stays stable) — whichever variant playback's height sort or the download quality
        // picker selects, no caption is silently lost. Downloads especially depended on this: the
        // picked variant used to carry only its own (often empty) list, so episodes downloaded
        // without any subtitles.
        val union = variants.flatMap { it.subtitles }.distinctBy { it.url }
        return if (union.isEmpty()) variants
        else variants.map { v ->
            if (v.subtitles.size == union.size) v
            else v.copy(subtitles = (v.subtitles + union).distinctBy { it.url })
        }
    }

    // The union merge above already puts every known subtitle on every variant, so the best
    // variant is complete as-is.
    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream =
        resolveStreams(animeTitle, episodeNumber).first()

    private companion object {
        val DETAIL: java.lang.reflect.Type = AnimeDetail::class.java
        const val MATCH_TTL_MS = 4L * 60 * 60 * 1000   // episode lists grow weekly; 4h keeps them current
        const val REGISTRY_WAIT_MS = 3_000L            // tolerate the cold-start async extension scan
    }
}
