package com.anilocal.app.data.download

import com.anilocal.app.data.local.DownloadStateUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWriteQueueTest {
    @Test fun burstsKeepOnlyTheLatestStatusForEachEpisode() {
        val queue = DownloadWriteQueue()
        queue.update(status("a", 0, 10))
        queue.update(status("a", 0, 11))
        queue.update(status("b", 0, 20))
        queue.update(status("a", 1, 100))

        assertEquals(setOf(status("a", 1, 100), status("b", 0, 20)), queue.drain().updates.toSet())
    }

    @Test fun unchangedNativeSamplesDoNotScheduleDatabaseWrites() {
        val queue = DownloadWriteQueue()
        val current = status("a", 0, 10)
        assertTrue(queue.update(current))
        assertFalse(queue.update(current))
        val batch = queue.drain()
        assertFalse(queue.update(current))
        queue.committed(batch)
        assertFalse(queue.update(current))
        assertTrue(queue.drain().isEmpty)
    }

    @Test fun aNewerObservationDuringAWriteIsNotDiscardedByAcknowledgement() {
        val queue = DownloadWriteQueue()
        queue.update(status("a", 0, 10))
        val writing = queue.drain()
        queue.update(status("a", 3, 10))
        queue.committed(writing)
        assertEquals(listOf(status("a", 3, 10)), queue.drain().updates)
    }

    @Test fun revertingToThePreviouslyWrittenStateDuringAnInFlightWriteIsPreserved() {
        val queue = DownloadWriteQueue()
        val paused = status("a", 3, 10)
        queue.update(paused)
        queue.committed(queue.drain())
        queue.update(status("a", 0, 10))
        val writing = queue.drain()
        // Pause arrives again while the resumed state is being committed.
        assertTrue(queue.update(paused))
        queue.committed(writing)
        assertEquals(listOf(paused), queue.drain().updates)
    }

    @Test fun failedWritesRetryWithoutReplacingMoreRecentStateOrRemoval() {
        val queue = DownloadWriteQueue()
        queue.update(status("a", 0, 10))
        queue.update(status("b", 0, 20))
        val failed = queue.drain()
        queue.update(status("a", 1, 100))
        queue.remove("b")
        queue.retry(failed)
        val retry = queue.drain()
        assertEquals(listOf(status("a", 1, 100)), retry.updates)
        assertEquals(listOf("b"), retry.removedIds)
    }

    @Test fun removingAnEpisodeReplacesBufferedProgressAndForgetAllowsARequeue() {
        val queue = DownloadWriteQueue()
        queue.update(status("a", 0, 10))
        queue.remove("a")
        val removal = queue.drain()
        assertTrue(removal.updates.isEmpty())
        assertEquals(listOf("a"), removal.removedIds)
        queue.committed(removal)
        assertFalse(queue.remove("a"))
        queue.forget("a")
        assertTrue(queue.update(status("a", 4, 0)))
    }

    private fun status(id: String, state: Int, progress: Int) = DownloadStateUpdate(id, state, progress)
}
