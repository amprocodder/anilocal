package com.anilocal.app.data.source

import com.anilocal.app.domain.source.NoSourceInstalledException
import android.os.SystemClock
import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** One source's resolved streams plus whether its title match was [confident] (a strong match
 *  against AniList's names for the title) or only the site's unverified top hit. The auto-selector
 *  prefers confident results; an unconfident one is a fallback only. (The requested episode itself
 *  always exists on the matched entry — the pipeline fails rather than substitute another.) */
data class SourceResolution(val streams: List<VideoStream>, val confident: Boolean)

/**
 * "Auto (best source)" resolution. Instead of routing every request to one fixed source, this races
 * the installed sources for a title the first time it's played, pins the winner (per title, in the
 * disposable JSON cache), and reuses that pin for every later episode — so later plays are as fast
 * as a manual pick, while the first play hides a dead/slow/Cloudflare-stalled source behind a faster
 * one and turns today's unbounded hang into a hard-capped race.
 *
 * Nothing here persists a stream URL (those are tokenized/short-lived) — only the winning source id
 * and per-source health stats, both re-derivable and swept with the rest of the cache. All signals
 * come from real resolutions (including manual-mode ones via [record]), so Auto starts warm and
 * needs no backend or curation.
 */
@Singleton
class AutoSourceSelector @Inject constructor(
    private val registry: SourceRegistry,
    private val cache: JsonCache,
    private val settings: SettingsRepository,
    @Named("appScope") private val appScope: CoroutineScope,
) {
    /** Serializes read-modify-write of the per-source scoreboard rows. */
    private val statsMutex = Mutex()

    /** Caps concurrent probes across ALL races (player load, prefetch, download) — the vendored
     *  lib-14 sources call OkHttp synchronously and bypass its dispatcher, and the CF WebView solves
     *  one challenge at a time, so the host must police fan-out itself. */
    private val probeGate = Semaphore(MAX_CONCURRENT_PROBES)

    /** A per-title (per AniList id) pin: which source last won, when, and whether it won with a
     *  confident match (a weak win means no source matched confidently when it was raced). */
    data class Pin(val sourceId: String, val wonAt: Long, val confident: Boolean = true)

    /** A per-source scoreboard row. Latency EWMA moves only on success, so a one-off 30 s cold
     *  Cloudflare solve or a timeout never poisons a source's ranking; failures only bump the count. */
    data class SrcStat(val latencyEwmaMs: Double, val successes: Int, val failures: Int)

    /**
     * Resolve [title] (AniList id [animeId]) episode [episodeNumber] through the automatic selector.
     * [probe] resolves ONE source end-to-end (search → detail → servers → resolve) — supplied by the
     * caller so the real pipeline (and its cache) is never duplicated here. Throws when every
     * candidate fails, with the same shape a manual resolve throws, so the player hint and download
     * failure handling are unchanged.
     */
    suspend fun resolve(
        animeId: String,
        title: String,
        episodeNumber: Int,
        probe: suspend (AnimeSource) -> SourceResolution,
    ): SourceResolution {
        val available = awaitSources()
        if (available.isEmpty()) throw NoSourceInstalledException()
        val key = memoKey(animeId)

        // One source → no race, but still bound the hang and record the outcome.
        if (available.size == 1) {
            val only = available.first()
            return tryProbe(only, probe, SINGLE_TIMEOUT_MS)
                ?.takeIf { it.streams.isNotEmpty() }
                ?.also { onWin(key, only, it.confident) }
                ?: error("source '${only.info.name}': could not resolve \"$title\" episode $episodeNumber")
        }

        // PIN PATH — reuse the last winner; a warm pinned resolve is the common case and matches
        // manual latency. A pin that no longer resolves (source rotated ids, or a freshly-aired
        // episode it hasn't indexed) is evicted and re-raced, as is a confident pin that has gone
        // weak. A pin that was ALREADY a weak win is kept on a weak result: nothing matched
        // confidently when it was raced, so re-racing every episode would only add latency.
        val pin = cache.getFresh<Pin>(key, PIN_TYPE, PIN_TTL_MS)
        val pinned = pin?.let { registry.get(it.sourceId) }
        if (pin != null && pinned != null) {
            val r = tryProbe(pinned, probe, PIN_TIMEOUT_MS)
            if (r != null && r.streams.isNotEmpty() && (r.confident || !pin.confident)) {
                onWin(key, pinned, r.confident)
                return r
            }
            cache.remove(key)   // pin no longer good → fall through to a fresh race
        }

        return race(title, key, pickCandidates(available), episodeNumber, probe)
    }

    /**
     * Race the [candidates]: each probes on IO, staggered so the fastest source usually answers
     * before the slower ones even start, and gated so we never hammer the shared client/WebView. The
     * FIRST strong success (non-empty AND a confident title match) wins immediately and the rest are
     * cancelled; unconfident ("weak") successes are buffered and only used if no strong success
     * arrives before the candidates settle or the overall deadline fires. Only a total wipeout throws.
     */
    private suspend fun race(
        title: String,
        key: String,
        candidates: List<AnimeSource>,
        episodeNumber: Int,
        probe: suspend (AnimeSource) -> SourceResolution,
    ): SourceResolution {
        val weak = CopyOnWriteArrayList<Pair<AnimeSource, SourceResolution>>()
        // Completes with the first strong winner, or with null once every candidate has settled
        // without one (the "no strong success" signal — the deadline path also yields null).
        val strong = CompletableDeferred<Pair<AnimeSource, SourceResolution>?>()

        // Probes run in a scope DETACHED from the caller (a child of appScope), not a coroutineScope
        // that would join them: a vendored lib-14 source calls OkHttp synchronously and won't
        // interrupt on cancel, so a structured join would make the winner wait out the slowest
        // loser's blocking request. Here the winner returns immediately and abandoned losers drain
        // unobserved. The finally-cancel guarantees they're torn down on every exit, incl. when the
        // caller (e.g. the player's load coroutine) is itself cancelled mid-race.
        val raceScope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext[Job]))
        try {
            val probeJobs = candidates.mapIndexed { i, src ->
                raceScope.launch(Dispatchers.IO) {
                    if (i > 0) delay(i * STAGGER_MS)
                    if (strong.isCompleted) return@launch
                    probeGate.withPermit {
                        if (strong.isCompleted) return@withPermit
                        val r = tryProbe(src, probe, CANDIDATE_TIMEOUT_MS) ?: return@withPermit
                        if (r.streams.isEmpty()) return@withPermit
                        if (r.confident) strong.complete(src to r) else weak.add(src to r)
                    }
                }
            }
            raceScope.launch {
                probeJobs.joinAll()
                strong.complete(null)   // no-op if a strong winner already completed it
            }

            val outcome = withTimeoutOrNull(OVERALL_MS) { strong.await() }
            val winner = outcome ?: weak.firstOrNull()
                ?: error("no source could resolve \"$title\" episode $episodeNumber (${candidates.size} tried)")

            onWin(key, winner.first, winner.second.confident)
            return winner.second
        } finally {
            raceScope.cancel()
        }
    }

    /** Probe one source under a timeout, recording the outcome for the scoreboard. Returns null on
     *  timeout/failure (recorded), the result on success (recorded), and rethrows real cancellation
     *  from a losing race branch (records nothing). */
    private suspend fun tryProbe(
        source: AnimeSource,
        probe: suspend (AnimeSource) -> SourceResolution,
        timeoutMs: Long,
    ): SourceResolution? {
        val start = SystemClock.elapsedRealtime()
        return try {
            val r = withTimeout(timeoutMs) { probe(source) }
            record(source.info.id, SystemClock.elapsedRealtime() - start, success = true)
            r
        } catch (_: TimeoutCancellationException) {
            // withTimeout's exception is a CancellationException subtype — catch it FIRST and treat a
            // timeout as this candidate's failure, not a cancellation of the whole race.
            record(source.info.id, timeoutMs, success = false)
            null
        } catch (ce: CancellationException) {
            throw ce   // a losing branch we cancelled — stay silent, don't skew stats
        } catch (_: Exception) {
            record(source.info.id, SystemClock.elapsedRealtime() - start, success = false)
            null
        }
    }

    /** Record one terminal resolution outcome. Public so manual-mode resolves feed the same
     *  scoreboard, letting Auto rank sensibly from the very first time it's enabled. */
    suspend fun record(sourceId: String, elapsedMs: Long, success: Boolean) {
        statsMutex.withLock {
            val cur = cache.getAged<SrcStat>(statKey(sourceId), STAT_TYPE)?.value
            val ewma = when {
                !success -> cur?.latencyEwmaMs ?: PRIOR_LATENCY_MS   // failures don't move latency
                cur == null -> elapsedMs.toDouble()
                else -> EWMA_ALPHA * elapsedMs + (1 - EWMA_ALPHA) * cur.latencyEwmaMs
            }
            cache.put(
                statKey(sourceId), STAT_TYPE,
                SrcStat(
                    latencyEwmaMs = ewma,
                    successes = (cur?.successes ?: 0) + if (success) 1 else 0,
                    failures = (cur?.failures ?: 0) + if (success) 0 else 1,
                ),
            )
        }
    }

    /** This source's recorded scoreboard row (successes/failures/latency), or null if never seen —
     *  exposed so auto-eviction can judge a source's track record. */
    suspend fun healthOf(sourceId: String): SrcStat? =
        cache.getAged<SrcStat>(statKey(sourceId), STAT_TYPE)?.value

    /** Recovery shares the extension fan-out ceiling but deliberately never reads a saved pin. */
    internal suspend fun withRecoveryPermit(block: suspend () -> Unit) = probeGate.withPermit { block() }

    /** A measured recovery winner replaces Auto's pin only after the caller accepts the result. */
    internal suspend fun rememberRecoveryWinner(animeId: String, source: AnimeSource, confident: Boolean) =
        onWin(memoKey(animeId), source, confident)

    /** Every source id currently pinned as some title's best. Auto-eviction spares these: a pinned
     *  source is the winner for at least one title, and removing it would break that title. */
    suspend fun pinnedSourceIds(): Set<String> =
        cache.valuesUnder<Pin>("bestsrc:", PIN_TYPE).map { it.sourceId }.toSet()

    /** Top sources by score, reserving one slot for an unexplored source so a mediocre-but-working
     *  incumbent can be displaced by a faster installed source over time. */
    private suspend fun pickCandidates(available: List<AnimeSource>): List<AnimeSource> {
        if (available.size <= RACE_K) return available
        // Score each source (suspend cache reads, so an explicit loop — not .map).
        val seen = HashSet<String>()
        val scored = ArrayList<Pair<AnimeSource, Double>>(available.size)
        for (src in available) {
            val stat = cache.getAged<SrcStat>(statKey(src.info.id), STAT_TYPE)?.value
            if (stat != null) seen += src.info.id
            scored += src to score(stat)
        }
        scored.sortBy { it.second }
        val top = scored.take(RACE_K).map { it.first }.toMutableList()
        // Guarantee one exploration slot so a never-tried source is sampled over time.
        if (top.none { it.info.id !in seen }) {
            available.firstOrNull { it.info.id !in seen && it !in top }?.let { top[top.lastIndex] = it }
        }
        return top
    }

    /** Lower is better: expected latency inflated by how often the source fails. An unseen source
     *  gets an optimistic-ish prior so it's sampled without being trusted blindly. */
    private fun score(stat: SrcStat?): Double {
        if (stat == null) return PRIOR_LATENCY_MS / PRIOR_SUCCESS_RATE
        val total = stat.successes + stat.failures
        val rate = ((stat.successes + 1.0) / (total + 2.0)).coerceIn(0.15, 1.0)   // Laplace-smoothed
        return stat.latencyEwmaMs / rate
    }

    private suspend fun onWin(key: String, source: AnimeSource, confident: Boolean) {
        cache.put(key, PIN_TYPE, Pin(source.info.id, System.currentTimeMillis(), confident))
        runCatching { settings.setLastAutoWinner(source.info.name) }
    }

    /** Bounded wait for the async extension scan to populate the registry — a resolve fired right
     *  after cold start would otherwise see an empty registry and fail spuriously. */
    private suspend fun awaitSources(): List<AnimeSource> {
        registry.sources.value.takeIf { it.isNotEmpty() }?.let { return it }
        return withTimeoutOrNull(REGISTRY_WAIT_MS) {
            registry.sources.first { it.isNotEmpty() }
        } ?: emptyList()
    }

    // Keyed by AniList id: titles collide (a remake and its original share a display title).
    private fun memoKey(animeId: String) = "bestsrc:$animeId"
    private fun statKey(sourceId: String) = "srcstats:$sourceId"

    private companion object {
        val PIN_TYPE: java.lang.reflect.Type = Pin::class.java
        val STAT_TYPE: java.lang.reflect.Type = SrcStat::class.java

        const val PIN_TTL_MS = 7L * 24 * 60 * 60 * 1000    // scraper catalogs rot; re-race weekly
        const val RACE_K = 4                                // candidates raced per fresh title
        const val STAGGER_MS = 300L                         // head start for higher-ranked candidates
        const val MAX_CONCURRENT_PROBES = 3                 // shared OkHttp client / CF WebView ceiling
        const val CANDIDATE_TIMEOUT_MS = 12_000L            // per-source deadline (covers one CF solve)
        const val OVERALL_MS = 15_000L                      // hard cap on the whole race
        const val PIN_TIMEOUT_MS = 8_000L                   // warm pinned resolve is ~2 round-trips
        const val SINGLE_TIMEOUT_MS = 60_000L               // one source, no fallback: bound only the hang
        const val REGISTRY_WAIT_MS = 3_000L

        const val EWMA_ALPHA = 0.3
        const val PRIOR_LATENCY_MS = 8_000.0
        const val PRIOR_SUCCESS_RATE = 0.5
    }
}
