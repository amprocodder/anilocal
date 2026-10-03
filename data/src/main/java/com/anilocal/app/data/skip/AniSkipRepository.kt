package com.anilocal.app.data.skip

import com.anilocal.app.data.cache.SuspendingLruCache
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.repo.SkipRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniSkipRepository internal constructor(
    private val api: AniSkipApi,
    nowMillis: () -> Long,
    dispatcher: CoroutineDispatcher,
) : SkipRepository {
    @Inject constructor(api: AniSkipApi) : this(api, { System.nanoTime() / 1_000_000 }, Dispatchers.IO)

    // Different source cuts can have different opening/ending times, so duration is part of the key.
    private data class MarkerKey(val malId: Int, val episode: Int, val durationSec: Long)
    private val cache = SuspendingLruCache<MarkerKey, List<SkipMarker>>(
        maxEntries = 100, ttlMillis = 30 * 60 * 1000L, nowMillis = nowMillis, dispatcher = dispatcher,
    )

    override suspend fun markers(
        idMal: Int?,
        episodeNumber: Int,
        episodeLengthSec: Long,
    ): List<SkipMarker> {
        // No MAL id (e.g. the built-in Creative-Commons sample) → show demo markers so the
        // skip control is still exercised. Real titles get real AniSkip data (or none).
        if (idMal == null) return demo(episodeLengthSec)

        return try {
            cache.getOrLoad(MarkerKey(idMal, episodeNumber, episodeLengthSec)) {
                val resp = api.skipTimes(idMal, episodeNumber, listOf("op", "ed"), episodeLengthSec)
                if (resp.found == true) resp.results.orEmpty().mapNotNull { it.toMarker() } else emptyList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun AniSkipResult.toMarker(): SkipMarker? {
        val start = interval?.startTime ?: return null
        val end = interval.endTime ?: return null
        if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
        val type = when (skipType?.lowercase()) {
            "op", "mixed-op" -> SkipMarker.Type.INTRO
            "ed", "mixed-ed" -> SkipMarker.Type.OUTRO
            else -> return null
        }
        return SkipMarker(type, startMs = (start * 1000).toLong(), endMs = (end * 1000).toLong())
    }

    private fun demo(lengthSec: Long) = listOf(
        SkipMarker(SkipMarker.Type.INTRO, 3_000, 15_000),
        SkipMarker(
            SkipMarker.Type.OUTRO,
            startMs = (lengthSec - 25).coerceAtLeast(0) * 1000,
            endMs = lengthSec * 1000,
        ),
    )
}
