package com.anilocal.app.data.skip

import com.anilocal.app.domain.model.SkipMarker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AniSkipRepositoryTest {
    @Test fun repeatedRecoveryReusesMarkersButDifferentSourceDurationIsRetested() = runTest {
        val api = FakeApi { duration ->
            AniSkipResponse(true, listOf(AniSkipResult(AniSkipInterval(10.0, duration / 2.0), "op")))
        }
        val repository = AniSkipRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        val first = repository.markers(1, 2, 1200)
        assertEquals(first, repository.markers(1, 2, 1200))
        val newCut = repository.markers(1, 2, 1300)
        assertEquals(listOf(1200L, 1300L), api.durations)
        assertEquals(600_000L, first.single().endMs)
        assertEquals(650_000L, newCut.single().endMs)
    }

    @Test fun transientFailureCanRetryAndInvalidIntervalsAreOmitted() = runTest {
        var fail = true
        val api = FakeApi {
            if (fail) throw IOException("Disconnected")
            AniSkipResponse(true, listOf(
                AniSkipResult(AniSkipInterval(10.0, 90.0), "op"),
                AniSkipResult(AniSkipInterval(-1.0, 20.0), "op"),
                AniSkipResult(AniSkipInterval(50.0, 30.0), "ed"),
                AniSkipResult(AniSkipInterval(Double.NaN, 30.0), "ed"),
            ))
        }
        val repository = AniSkipRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        assertTrue(repository.markers(1, 2, 1200).isEmpty())
        fail = false
        assertEquals(listOf(SkipMarker(SkipMarker.Type.INTRO, 10_000, 90_000)), repository.markers(1, 2, 1200))
        assertEquals(2, api.durations.size)
    }

    @Test fun leavingPlayerCancelsSkipLookup() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val api = FakeApi {
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val repository = AniSkipRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        val request = async { repository.markers(1, 2, 1200) }
        runCurrent()
        request.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled.isCompleted)
    }

    private class FakeApi(private val response: suspend (Long) -> AniSkipResponse) : AniSkipApi {
        val durations = mutableListOf<Long>()
        override suspend fun skipTimes(malId: Int, episode: Int, types: List<String>, episodeLength: Long): AniSkipResponse {
            durations += episodeLength
            return response(episodeLength)
        }
    }
}
