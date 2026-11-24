package com.anilocal.app.data.skip

import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.repo.SkipRepository
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AniSkip skip windows — the FALLBACK. The player prefers the source's own per-stream timestamps
 * (`VideoStream.skips`) and asks here only for a type the source didn't publish.
 *
 * How AniSkip answers (aniskip-api `findSkipTimes`): for EACH requested type independently, it keeps
 * submissions whose recorded episodeLength is within ±20s of ours (all of them for length 0) and
 * returns only the top-voted one. So a length-matched answer routinely holds just an OP or just an
 * ED — the old code took any non-empty answer as complete, which is how one window went missing on
 * episode after episode. Here each type is settled on its own: the same-cut answer if it has a
 * plausible window of that type, else the any-cut (length 0) top-voted one.
 */
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
        // AniSkip is keyed by MAL id: without one there's nothing to look up, so no markers
        // (never fabricated ones — a Skip button that jumps to made-up times is worse than none).
        if (idMal == null) return emptyList()

        return withContext(Dispatchers.IO) {
            val lenMs = episodeLengthSec.coerceAtLeast(0) * 1000
            val sameCut = if (lenMs > 0) submissions(idMal, episodeNumber, episodeLengthSec) else emptyList()
            var anyCut: List<Submission>? = null
            SkipMarker.Type.entries.mapNotNull { type ->
                sameCut.filter { it.type == type }.firstNotNullOfOrNull { it.toMarker(lenMs) }
                    ?: (anyCut ?: submissions(idMal, episodeNumber, 0).also { anyCut = it })
                        .filter { it.type == type }.firstNotNullOfOrNull { it.toMarker(lenMs) }
            }
        }
    }

    /**
     * One AniSkip query's submissions (≤ 1 per type), cache-first for 24h: they're crowd-sourced and
     * near-static, and this keeps the Skip control working while AniSkip is unreachable. A genuine
     * answer (even "nothing submitted yet") is cached; a transport error is not — it falls back to a
     * stale entry, or as a last resort to the any-cut entry.
     */
    private suspend fun submissions(idMal: Int, episodeNumber: Int, lengthSec: Long): List<Submission> {
        val key = "skip:v2:$idMal:$episodeNumber:$lengthSec"
        val aged = cache.getAged<List<Submission>>(key, SUBMISSIONS)
        if (aged != null && aged.ageMs <= TTL_MS) return aged.value
        return fetch(idMal, episodeNumber, lengthSec)?.also { cache.put(key, SUBMISSIONS, it) }
            ?: aged?.value
            ?: cache.getAged<List<Submission>>("skip:v2:$idMal:$episodeNumber:0", SUBMISSIONS)?.value.orEmpty()
    }

    // Empty = a real "not found" (found:false, or the HTTP 404 AniSkip answers it with — cacheable);
    // null = transport/server error (not cacheable, so the caller can fall back and retry next time).
    private suspend fun fetch(idMal: Int, episodeNumber: Int, lengthSec: Long): List<Submission>? =
        try {
            val resp = api.skipTimes(idMal, episodeNumber, listOf("op", "ed"), lengthSec)
            if (resp.found == true) resp.results.orEmpty().mapNotNull { it.toSubmission() } else emptyList()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) emptyList() else null
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            null
        }

    /** A submission as AniSkip recorded it, on the submitter's cut ([cutMs] = its episodeLength). */
    // Internal (not private) so Moshi's reflection adapter can serialize it for the cache.
    internal data class Submission(val type: SkipMarker.Type, val startMs: Long, val endMs: Long, val cutMs: Long)

    private fun AniSkipResult.toSubmission(): Submission? {
        val start = interval?.startTime ?: return null
        val end = interval.endTime ?: return null
        val type = when (skipType?.lowercase()) {
            "op" -> SkipMarker.Type.INTRO
            "ed" -> SkipMarker.Type.OUTRO
            else -> return null
        }
        return Submission(type, (start * 1000).toLong(), (end * 1000).toLong(), ((episodeLength ?: 0.0) * 1000).toLong())
    }

    /**
     * Map a submission onto the file being played ([lenMs], 0 = unknown), or null when it can't be a
     * real window. Measured over 239 episodes of 20 long-finished shows, across submissions from
     * different-length cuts of the same episode: an OP keeps its ABSOLUTE time (22 pairs vs 1), while
     * an ED keeps its distance from the END (20 vs 4) — the extra footage sits before the ending. So an
     * ED is shifted by the length difference (against per-stream source timestamps this moved 3 of 17
     * misplaced outros onto the real ending). Plausibility is judged against both cuts: an OP starts in
     * the first half (Steins;Gate's opens at 10:42, so no fixed cap), an ED in the second half; this
     * drops junk such as an "ed" submitted at 0:11.
     */
    private fun Submission.toMarker(lenMs: Long): SkipMarker? {
        val span = endMs - startMs
        val maxSpan = if (type == SkipMarker.Type.INTRO) MAX_INTRO_MS else MAX_OUTRO_MS
        if (startMs < 0 || span < MIN_SPAN_MS || span > maxSpan) return null
        val shift = if (type == SkipMarker.Type.OUTRO && lenMs > 0 && cutMs > 0) lenMs - cutMs else 0L
        val start = startMs + shift
        val end = if (lenMs > 0) minOf(endMs + shift, lenMs) else endMs + shift
        val plausible = when (type) {
            SkipMarker.Type.INTRO -> (cutMs <= 0 || startMs < cutMs / 2) && (lenMs <= 0 || start < lenMs / 2)
            SkipMarker.Type.OUTRO -> (cutMs <= 0 || startMs > cutMs / 2) && (lenMs <= 0 || start > lenMs / 2)
        }
        return if (plausible && start >= 0 && end > start) SkipMarker(type, start, end) else null
    }

    private companion object {
        val SUBMISSIONS: java.lang.reflect.Type =
            Types.newParameterizedType(List::class.java, Submission::class.java)
        const val TTL_MS = 24L * 60 * 60 * 1000   // new submissions for airing shows land within a day
        // Real windows run 85–92s (OP) / 78–99s (ED) at p5–p95 across the sampled episodes.
        const val MIN_SPAN_MS = 10_000L
        const val MAX_INTRO_MS = 150_000L
        const val MAX_OUTRO_MS = 180_000L
    }
}
