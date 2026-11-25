package com.anilocal.app.data.source

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import com.anilocal.app.domain.source.NoSourceInstalledException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.coroutines.coroutineContext

/**
 * Bridges catalog metadata (AniList) to playable streams via the user's selected [AnimeSource]:
 * find the title in the source, match the episode number, resolve a server to its quality
 * variants. The active source is taken from [SourceRegistry] by the persisted selected-source id,
 * falling back to the first available source so playback never depends on a single compile-time
 * binding. Browsing/metadata stay on AniList; only the stream leg routes
 * through the chosen source, so any source — built-in or extension — works with no branching here.
 *
 * The title-match half of the pipeline (search → detail, i.e. "which source entry is this AniList
 * title, and what are its episode ids") is cached per source and AniList id for [MATCH_TTL_MS]: it's
 * the slow, Cloudflare-challenged part, it's stable across episodes, and skipping it turns an
 * episode switch or a season download from 4+ round-trips into 2. Server/stream URLs are NOT cached
 * — they're short-lived/tokenized. A cached match that no longer resolves (source rotated its ids)
 * is dropped and the whole pipeline re-runs live once.
 *
 * Which search result IS the title is decided by [TitleMatcher], never by the site's ranking: a
 * sequel's search lists its prequel first, and since every entry numbers its episodes from 1, taking
 * that first hit played season 1's episodes under season 2. Likewise an episode the matched entry
 * doesn't have fails rather than playing a different one.
 */
class SourceStreamRepository internal constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val cache: JsonCache,
    private val selector: AutoSourceSelector,
    speedProbe: StreamSpeedProbe,
    appScope: CoroutineScope,
) : StreamRepository {

    constructor(
        registry: SourceRegistry,
        settings: SettingsRepository,
        cache: JsonCache,
        selector: AutoSourceSelector,
        client: OkHttpClient,
        appScope: CoroutineScope,
    ) : this(registry, settings, cache, selector, HttpStreamSpeedProbe(client), appScope)

    private val recovery = FreshStreamRecovery(registry, speedProbe, appScope) { block ->
        selector.withRecoveryPermit(block)
    }

    /** Resolve the selected manual source, waiting briefly for the async extension scan to populate
     *  the registry before falling back to the first available source (a resolve fired right after
     *  cold start would otherwise see an empty registry and fail spuriously). */
    private suspend fun sourceFor(selectedId: String): AnimeSource {
        registry.get(selectedId)?.let { return it }
        val available = withTimeoutOrNull(REGISTRY_WAIT_MS) { registry.sources.first { it.isNotEmpty() } }
        return available?.firstOrNull() ?: throw NoSourceInstalledException()
    }

    override suspend fun resolveStreams(anime: AnimeDetail, episodeNumber: Int): List<VideoStream> =
        withContext(Dispatchers.IO) {
            val selected = settings.selectedSourceId.first()
            if (selected == Sources.AUTO) {
                // Auto mode: race installed sources / reuse the pinned winner. The per-source probe is
                // exactly the manual pipeline below, so caching and failure shape are identical.
                selector.resolve(anime.id, anime.title, episodeNumber) { source ->
                    resolveVia(source, anime, episodeNumber)
                }.streams
            } else {
                val source = sourceFor(selected)
                // Time the manual resolve into the same scoreboard Auto reads, so switching to Auto
                // later starts warm instead of cold.
                val start = SystemClock.elapsedRealtime()
                try {
                    val r = resolveVia(source, anime, episodeNumber)
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

    /** A source's entry for one AniList title, as cached: its detail (episode ids) and whether the
     *  title match was confident or only the site's unverified top hit. */
    @Serializable
    data class SourceMatch(val detail: AnimeDetail, val confident: Boolean)

    /** Resolve ONE source end-to-end (the cached-match fast path + a live fallback), reporting
     *  whether the title match was confident. This is the unit the auto selector races and the
     *  manual path runs directly — so there is exactly one pipeline. */
    private suspend fun resolveVia(source: AnimeSource, anime: AnimeDetail, episodeNumber: Int): SourceResolution {
        val key = matchKey(source, anime)

        // A fresh cached match is only trusted when it actually carries the requested episode —
        // an episode that aired after the entry was cached must force a live re-fetch.
        val cached = cache.getFresh<SourceMatch>(key, MATCH, MATCH_TTL_MS)
        if (cached != null && cached.detail.episodes.any { it.number == episodeNumber }) {
            try {
                return SourceResolution(resolveFrom(source, cached.detail, episodeNumber), cached.confident)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Could be rotated ids OR a transient/offline error — the adapter flattens both
                // to empty. Don't evict: fall through to a live run, which overwrites the entry
                // on success and leaves it intact when the failure was transient.
            }
        }

        val match = liveMatch(source, anime)
        if (match.detail.episodes.isNotEmpty()) cache.put(key, MATCH, match)
        return SourceResolution(resolveFrom(source, match.detail, episodeNumber), match.confident)
    }

    /** The un-cached search → detail leg: find this exact title (not its prequel or sequel) among
     *  the source's search results. Throws with a sourced message when it isn't there. */
    private suspend fun liveMatch(source: AnimeSource, anime: AnimeDetail): SourceMatch {
        val match = TitleMatcher.find(TitleMatcher.Target(anime)) { query -> source.search(query, page = 1) }
            ?: error("source '${source.info.name}': no match for \"${anime.title}\"")
        return SourceMatch(source.detail(match.candidate.id), match.confident)
    }

    /** Episode pick → servers → variants. Throws on any empty leg so callers (and the cached-match
    *  fallback above) see one consistent failure shape. The episode must exist on the matched
    *  entry: substituting another one plays the wrong video without any error, which is worse than
    *  failing over to the next source. */
    private suspend fun resolveFrom(source: AnimeSource, detail: AnimeDetail, episodeNumber: Int): List<VideoStream> {
        val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
            ?: error("source '${source.info.name}': episode $episodeNumber is unavailable for \"${detail.title}\"")
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
        return mergeSubtitles(variants)
    }

    // The union merge above already puts every known subtitle on every variant, so the best
    // variant is complete as-is.
    override suspend fun resolveStream(anime: AnimeDetail, episodeNumber: Int): VideoStream {
        val pre = prefetches.remove(prefetchKey(anime.id, episodeNumber))
        if (pre != null && !pre.isStale()) {
            try {
                return pre.result.await()
            } catch (ce: CancellationException) {
                currentCoroutineContext().ensureActive()   // the caller was cancelled: propagate
            } catch (_: Exception) {
                // The prefetch failed; resolve live below (it may have been a transient error).
            }
        }
        return resolveStreams(anime, episodeNumber).first()
    }

    // Pre-resolved streams for the episode the user is likely to play next (see prefetch). Links are
    // tokenized and expire, so one is only trusted for PREFETCH_TTL_MS after it was started.
    private class Prefetch(val result: Deferred<VideoStream>, val startedAt: Long) {
        fun isStale() = SystemClock.elapsedRealtime() - startedAt > PREFETCH_TTL_MS
    }
    private val prefetches = ConcurrentHashMap<String, Prefetch>()
    private val prefetchScope = appScope

    private fun prefetchKey(animeId: String, episode: Int) = "$animeId:$episode"

    override fun prefetch(anime: AnimeDetail, episodeNumber: Int) {
        val key = prefetchKey(anime.id, episodeNumber)
        prefetches.entries.removeAll { (_, p) -> p.isStale().also { stale -> if (stale) p.result.cancel() } }
        if (prefetches.containsKey(key)) return
        // At most a couple at a time — the latest asks are the likely ones.
        while (prefetches.size >= MAX_PREFETCHES) {
            prefetches.entries.minByOrNull { it.value.startedAt }?.let { oldest ->
                prefetches.remove(oldest.key)?.result?.cancel()
            } ?: break
        }
        prefetches[key] = Prefetch(
            prefetchScope.async(Dispatchers.IO) { resolveStreams(anime, episodeNumber).first() },
            SystemClock.elapsedRealtime(),
        )
    }

    override suspend fun recoverStream(
        anime: AnimeDetail,
        episodeNumber: Int,
        failedStreamUrl: String?,
    ): VideoStream = withContext(Dispatchers.IO) {
        val selected = settings.selectedSourceId.first()
        val winner = recovery.resolve(anime, episodeNumber, failedStreamUrl, selected)
        coroutineContext.ensureActive()
        // Only the accepted winner writes shared state: abandoned extension calls cannot overwrite
        // pins/cache after the player closes or a newer recovery starts.
        cache.put(matchKey(winner.source, anime), MATCH, SourceMatch(winner.detail, winner.confident))
        coroutineContext.ensureActive()
        if (selected == Sources.AUTO) selector.rememberRecoveryWinner(anime.id, winner.source, winner.confident)
        winner.stream
    }

    // Keyed by AniList id, not title: two entries can share a display title (a remake and its
    // original), and the old title-keyed `src:` rows may hold a wrong-season match — the new
    // namespace leaves those to the 30-day sweep instead of reusing them.
    private fun matchKey(source: AnimeSource, anime: AnimeDetail) = "srcmatch:${source.info.id}:${anime.id}"

    private companion object {
        val MATCH = SourceMatch.serializer()
        const val MATCH_TTL_MS = 4L * 60 * 60 * 1000   // episode lists grow weekly; 4h keeps them current
        const val PREFETCH_TTL_MS = 4L * 60 * 1000   // tokenized links outlive this comfortably
        const val MAX_PREFETCHES = 2
        const val REGISTRY_WAIT_MS = 3_000L            // tolerate the cold-start async extension scan
    }
}

/** Preserve each rendition's default caption first, while carrying all of the server's captions. */
internal fun mergeSubtitles(variants: List<VideoStream>): List<VideoStream> {
    val union = variants.flatMap { it.subtitles }.distinctBy { it.url }
    return if (union.isEmpty()) variants else variants.map { variant ->
        variant.copy(subtitles = (variant.subtitles + union).distinctBy { it.url })
    }
}
