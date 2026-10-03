package com.anilocal.app.ui.details

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.ui.performance.FakeDownloads
import com.anilocal.app.ui.performance.FakeSkip
import com.anilocal.app.ui.performance.FakeStreams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRequestsTest {
    @Test fun streamResolutionAndSkipMarkersOverlapInsteadOfAddingLatency() = runTest {
        val streams = FakeStreams().apply { resolveBlock = { delay(500); listOf(low) } }
        val skip = FakeSkip().apply { markersBlock = { delay(500); emptyList() } }
        val downloads = FakeDownloads()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, skip, downloads)
        requests.download(episode)
        runCurrent()
        assertEquals(1, streams.resolutions)
        assertEquals(1, skip.requests)
        advanceTimeBy(499)
        runCurrent()
        assertTrue(downloads.enqueued.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(low), downloads.enqueued)
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    @Test fun duplicateTapsBeforeAndDuringResolutionPerformOneRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        val streams = FakeStreams().apply { resolveBlock = { release.await(); listOf(low) } }
        val downloads = FakeDownloads()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), downloads)
        repeat(10) { requests.download(episode) }
        runCurrent()
        repeat(10) { requests.download(episode) }
        assertEquals(1, streams.resolutions)
        assertEquals(setOf(1), requests.busyEpisodes.value)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(low), downloads.enqueued)
    }

    @Test fun preparedPickersQueueAndOldDialogCallbacksCannotSelectTheNextEpisode() = runTest {
        val streams = FakeStreams().apply { resolveBlock = { listOf(high, low) } }
        val downloads = FakeDownloads().apply { enqueueBlock = { delay(500) } }
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), downloads)
        requests.download(episode)
        runCurrent()
        requests.download(episode.copy(number = 2))
        runCurrent()
        assertEquals(2, streams.resolutions)
        assertEquals(episode, requests.pending.value?.episode)
        val firstPicker = checkNotNull(requests.pending.value)
        repeat(10) { requests.chooseQuality(firstPicker, low) }
        runCurrent()
        assertEquals(2, requests.pending.value?.episode?.number)
        assertEquals(listOf(low), downloads.enqueued)
        requests.download(episode.copy(number = 2))
        assertEquals(2, streams.resolutions)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(setOf(2), requests.busyEpisodes.value)
        requests.chooseQuality(checkNotNull(requests.pending.value), high)
        runCurrent()
        assertEquals(listOf(1, 2), downloads.enqueuedEpisodes)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    @Test fun dismissingThePickerAllowsAnotherEpisodeAndRejectsUnrelatedStreams() = runTest {
        val streams = FakeStreams().apply { resolveBlock = { listOf(high, low) } }
        val downloads = FakeDownloads()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), downloads)
        requests.download(episode)
        runCurrent()
        val picker = checkNotNull(requests.pending.value)
        requests.chooseQuality(picker, VideoStream("unrelated"))
        assertNotNull(requests.pending.value)
        assertTrue(downloads.enqueued.isEmpty())
        requests.dismissPicker(picker)
        requests.download(episode.copy(number = 2))
        runCurrent()
        assertEquals(2, requests.pending.value?.episode?.number)
        assertEquals(2, streams.resolutions)
    }

    @Test fun streamFailureCancelsUnneededMarkerWorkAndAllowsRetry() = runTest {
        var markersCancelled = false
        val streams = FakeStreams().apply { resolveBlock = { delay(10); error("No source") } }
        val skip = FakeSkip().apply { markersBlock = {
            try { CompletableDeferred<Unit>().await(); emptyList() }
            finally { markersCancelled = true }
        } }
        val downloads = FakeDownloads()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, skip, downloads)
        requests.download(episode)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        assertTrue(markersCancelled)
        assertTrue(requests.busyEpisodes.value.isEmpty())
        assertTrue(downloads.enqueued.isEmpty())
        streams.resolveBlock = { listOf(low) }
        skip.markersBlock = { error("No skip metadata") }
        requests.download(episode)
        runCurrent()
        assertEquals(listOf(low), downloads.enqueued)
    }

    @Test fun enqueueFailureDoesNotLeaveTheWorkflowLocked() = runTest {
        val downloads = FakeDownloads().apply { enqueueBlock = { error("No space") } }
        val requests = DownloadRequests(backgroundScope, { anime }, FakeStreams(), FakeSkip(), downloads)
        requests.download(episode)
        runCurrent()
        assertTrue(requests.busyEpisodes.value.isEmpty())
        downloads.enqueueBlock = {}
        requests.download(episode)
        runCurrent()
        assertEquals(2, downloads.enqueued.size)
    }

    @Test fun leavingTheScreenCancelsPreparationBeforeEnqueue() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val downloads = FakeDownloads()
        val streams = FakeStreams().apply { resolveBlock = { delay(500); listOf(low) } }
        val requests = DownloadRequests(scope, { anime }, streams, FakeSkip(), downloads)
        requests.download(episode)
        runCurrent()
        scope.cancel()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(downloads.enqueued.isEmpty())
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    @Test fun differentEpisodesPrepareInParallelWithinTheBoundAndAllReachDownloadManager() = runTest {
        var active = 0
        var maximum = 0
        val streams = FakeStreams().apply { resolveBlock = {
            active++
            maximum = maxOf(maximum, active)
            try { delay(500); listOf(low) }
            finally { active-- }
        } }
        val downloads = FakeDownloads()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), downloads)
        for (number in 1..9) requests.download(episode.copy(id = "ep$number", number = number))
        runCurrent()
        assertEquals(3, streams.resolutions)
        assertEquals((1..9).toSet(), requests.busyEpisodes.value)
        repeat(3) {
            advanceTimeBy(500)
            runCurrent()
        }
        assertEquals(3, maximum)
        assertEquals(9, streams.resolutions)
        assertEquals((1..9).toSet(), downloads.enqueuedEpisodes.toSet())
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    @Test fun nativeEnqueueDoesNotOccupyPreparationSlotsOrBlockAnotherEpisode() = runTest {
        val release = CompletableDeferred<Unit>()
        val downloads = FakeDownloads().apply { enqueueBlock = { release.await() } }
        val streams = FakeStreams()
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), downloads, preparationLimit = 1)
        requests.download(episode)
        runCurrent()
        requests.download(episode.copy(id = "ep2", number = 2))
        runCurrent()
        assertEquals(2, streams.resolutions)
        assertEquals(listOf(1, 2), downloads.enqueuedEpisodes)
        release.complete(Unit)
        runCurrent()
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    @Test fun dismissingAQueuedPickerCannotDismissTheNextRequestWithAnOldCallback() = runTest {
        val streams = FakeStreams().apply { resolveBlock = { listOf(high, low) } }
        val requests = DownloadRequests(backgroundScope, { anime }, streams, FakeSkip(), FakeDownloads())
        requests.download(episode)
        requests.download(episode.copy(id = "ep2", number = 2))
        runCurrent()
        val first = checkNotNull(requests.pending.value)
        repeat(5) { requests.dismissPicker(first) }
        assertEquals(2, requests.pending.value?.episode?.number)
        assertEquals(setOf(2), requests.busyEpisodes.value)
        requests.dismissPicker(checkNotNull(requests.pending.value))
        assertNull(requests.pending.value)
        assertTrue(requests.busyEpisodes.value.isEmpty())
    }

    private companion object {
        val episode = Episode("ep1", 1)
        val anime = AnimeDetail("1", "Anime", null, idMal = 1, episodes = listOf(episode))
        val high = VideoStream("high", height = 1080)
        val low = VideoStream("low", height = 480)
    }
}
