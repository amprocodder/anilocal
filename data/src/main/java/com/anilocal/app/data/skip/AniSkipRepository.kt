package com.anilocal.app.data.skip

import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.repo.SkipRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniSkipRepository @Inject constructor(
    private val api: AniSkipApi,
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
            // AniSkip only returns submissions whose recorded episodeLength is within a few seconds of
            // the value we send, answering found:false otherwise. The stream durations real sources
            // serve routinely differ from contributors' encodes by more than that window, so passing
            // the player's actual duration silently returned NO markers and the Skip control never
            // appeared. When the length-matched query finds nothing, retry with episodeLength=0, which
            // disables the length filter and returns the top-voted op/ed times. Skip intervals are
            // absolute seconds from the start, so they still apply regardless of total duration.
            // Guard the retry on episodeLengthSec > 0 so we don't fire a second identical request when
            // the duration was still unknown (already 0) at lookup time.
            val matched = fetch(idMal, episodeNumber, episodeLengthSec)
            if (matched.isNotEmpty() || episodeLengthSec == 0L) matched
            else fetch(idMal, episodeNumber, 0)
        }
    }

    // One AniSkip lookup → mapped markers (empty on not-found, error, or no mappable results).
    private suspend fun fetch(idMal: Int, episodeNumber: Int, episodeLengthSec: Long): List<SkipMarker> =
        runCatching {
            val resp = api.skipTimes(idMal, episodeNumber, listOf("op", "ed"), episodeLengthSec)
            if (resp.found == true) resp.results.orEmpty().mapNotNull { it.toMarker() } else emptyList()
        }.getOrDefault(emptyList())

    private fun AniSkipResult.toMarker(): SkipMarker? {
        val start = interval?.startTime ?: return null
        val end = interval.endTime ?: return null
        val type = when (skipType?.lowercase()) {
            "op", "mixed-op" -> SkipMarker.Type.INTRO
            "ed", "mixed-ed" -> SkipMarker.Type.OUTRO
            else -> return null
        }
        return SkipMarker(type, startMs = (start * 1000).toLong(), endMs = (end * 1000).toLong())
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
