package com.anilocal.app.data.skip

import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.repo.SkipRepository
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniSkipRepository @Inject constructor(
    private val api: AniSkipApi,
    private val cache: JsonCache,
) : SkipRepository {

    override suspend fun markers(
        idMal: Int?,
        episodeNumber: Int,
        episodeLengthSec: Long,
    ): List<SkipMarker> {
        // No MAL id (e.g. the built-in Creative-Commons sample) → show demo markers so the
        // skip control is still exercised. Real titles get real AniSkip data (or none).
        if (idMal == null) return demo(episodeLengthSec)

        return withContext(Dispatchers.IO) {
            // Cache-first (24h): markers are crowd-sourced and near-static, and this also keeps the
            // Skip control working when AniSkip is unreachable. Raw candidates are cached (not the
            // deduped result) because dedupe clamps to the CURRENT playback's duration. The key
            // includes the queried length: Details enqueues downloads with length=0 (any-cut,
            // top-voted times), and that answer must not satisfy the player's later matched-cut
            // query for the same episode — the two are different questions.
            val key = "skip:$idMal:$episodeNumber:$episodeLengthSec"
            val aged = cache.getAged<List<Cand>>(key, CAND_LIST)
            val raw: List<Cand> = if (aged != null && aged.ageMs <= TTL_MS) {
                aged.value
            } else {
                // AniSkip only returns submissions whose recorded episodeLength is within a few seconds of
                // the value we send, answering found:false otherwise. The stream durations real sources
                // serve routinely differ from contributors' encodes by more than that window, so passing
                // the player's actual duration silently returned NO markers and the Skip control never
                // appeared. When the length-matched query finds nothing, retry with episodeLength=0, which
                // disables the length filter and returns the top-voted op/ed times. The matched-length
                // query is the one that pins us to the *same cut* (so absolute timestamps line up), so we
                // strongly prefer it; the length=0 fallback is best-effort and can be ~30s off for a
                // recap/cold-open variant. Guard the retry on episodeLengthSec > 0 so we don't fire a
                // second identical request when the duration was still unknown (already 0) at lookup time.
                val matched = fetch(idMal, episodeNumber, episodeLengthSec)
                val fetched =
                    if (matched == null) null
                    else if (matched.isNotEmpty() || episodeLengthSec == 0L) matched
                    else fetch(idMal, episodeNumber, 0)
                // A genuine answer (even "nothing submitted yet" — retried after the TTL) is cached;
                // a network failure is not, falling back to whatever stale entry exists — or, as a
                // last resort, the any-cut length-0 entry (semantically what the online fallback
                // fetch would have returned).
                fetched?.also { cache.put(key, CAND_LIST, it) }
                    ?: aged?.value
                    ?: cache.getAged<List<Cand>>("skip:$idMal:$episodeNumber:0", CAND_LIST)?.value.orEmpty()
            }
            // Collapse the response to exactly one INTRO + one OUTRO so auto-skip, the manual Skip
            // button, and the seek-bar highlight all read the same single window per type (multiple
            // submissions used to all become markers, causing erratic double-skips / wrong windows).
            dedupe(raw, episodeLengthSec)
        }
    }

    // One AniSkip lookup → candidate markers: empty = a real "not found" (found:false or the HTTP
    // 404 AniSkip answers unknown episodes with — cacheable); null = transport/server error (not
    // cacheable, so the caller can fall back to stale data and retry next time).
    private suspend fun fetch(idMal: Int, episodeNumber: Int, episodeLengthSec: Long): List<Cand>? =
        try {
            val resp = api.skipTimes(idMal, episodeNumber, listOf("op", "ed"), episodeLengthSec)
            if (resp.found == true) resp.results.orEmpty().mapNotNull { it.toCand() } else emptyList()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) emptyList() else null
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            null
        }

    // A candidate carries whether it came from a canonical "op"/"ed" submission (preferred over the
    // lower-confidence "mixed-*" variants when more than one of a type is returned).
    // Internal (not private/nested-private) so Moshi's reflection adapter can serialize it for the cache.
    internal data class Cand(val marker: SkipMarker, val canonical: Boolean)

    private fun AniSkipResult.toCand(): Cand? {
        val start = interval?.startTime ?: return null
        val end = interval.endTime ?: return null
        if (end <= start) return null
        val raw = skipType?.lowercase()
        val type = when (raw) {
            "op", "mixed-op" -> SkipMarker.Type.INTRO
            "ed", "mixed-ed" -> SkipMarker.Type.OUTRO
            else -> return null
        }
        val marker = SkipMarker(type, startMs = (start * 1000).toLong(), endMs = (end * 1000).toLong())
        return Cand(marker, canonical = raw == "op" || raw == "ed")
    }

    // Pick the single best INTRO and OUTRO from possibly-many submissions. INTRO: the earliest
    // plausible opening window (canonical preferred); OUTRO: the window whose end sits closest to the
    // real episode end (the same-cut ending), clamped to the actual duration so the auto-skip seek
    // lands exactly at the end and still yields a clean STATE_ENDED → next-episode advance.
    private fun dedupe(cands: List<Cand>, episodeLengthSec: Long): List<SkipMarker> {
        val lenMs = episodeLengthSec * 1000
        val intro = cands
            .filter { it.marker.type == SkipMarker.Type.INTRO }
            .filter { it.marker.startMs < 6 * 60_000 && (it.marker.endMs - it.marker.startMs) < 150_000 }
            .sortedWith(compareByDescending<Cand> { it.canonical }.thenBy { it.marker.startMs })
            .firstOrNull()?.marker
        val outro = cands
            .filter { it.marker.type == SkipMarker.Type.OUTRO }
            .sortedWith(
                compareByDescending<Cand> { it.canonical }
                    .thenBy { if (lenMs > 0) abs(lenMs - it.marker.endMs) else -it.marker.startMs },
            )
            .firstOrNull()?.marker
            ?.let { if (lenMs > 0) it.copy(endMs = minOf(it.endMs, lenMs)) else it }
        return listOfNotNull(intro, outro)
    }

    private companion object {
        val CAND_LIST: java.lang.reflect.Type =
            Types.newParameterizedType(List::class.java, Cand::class.java)
        const val TTL_MS = 24L * 60 * 60 * 1000   // new submissions for airing shows land within a day
    }

    // Only emit the OUTRO when the episode length is known: with lengthSec == 0 (duration not yet
    // reported at STATE_READY) the window would collapse to the empty range [0, 0) and never trigger.
    private fun demo(lengthSec: Long) = buildList {
        add(SkipMarker(SkipMarker.Type.INTRO, 3_000, 15_000))
        if (lengthSec > 0) {
            add(
                SkipMarker(
                    SkipMarker.Type.OUTRO,
                    startMs = (lengthSec - 25).coerceAtLeast(0) * 1000,
                    endMs = lengthSec * 1000,
                ),
            )
        }
    }
}
