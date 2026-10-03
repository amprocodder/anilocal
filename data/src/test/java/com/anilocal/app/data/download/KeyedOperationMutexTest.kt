package com.anilocal.app.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyedOperationMutexTest {
    @Test fun theSameEpisodeWaitsForRemovalWhileOtherEpisodesProceed() = runBlocking {
        val gate = KeyedOperationMutex()
        val releaseRemoval = CompletableDeferred<Unit>()
        val enqueueStarted = CompletableDeferred<Unit>()
        val removing = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock("episode-one") { releaseRemoval.await() }
        }
        val enqueue = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock("episode-one") { enqueueStarted.complete(Unit) }
        }
        val other = async { gate.withLock("episode-two") { true } }
        assertTrue(other.await())
        assertFalse(enqueueStarted.isCompleted)
        releaseRemoval.complete(Unit)
        removing.join()
        enqueue.join()
        assertTrue(enqueueStarted.isCompleted)
    }

    @Test fun cancellingAWaitingEnqueueDoesNotBreakTheEpisodeReservation() = runBlocking {
        val gate = KeyedOperationMutex()
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) { gate.withLock("episode") { release.await() } }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock("episode") { throw AssertionError("Cancelled waiter acquired the episode") }
        }
        cancelled.cancelAndJoin()
        val next = async(start = CoroutineStart.UNDISPATCHED) { gate.withLock("episode") { true } }
        assertFalse(next.isCompleted)
        release.complete(Unit)
        holder.join()
        assertTrue(next.await())
        assertTrue(gate.withLock("episode") { true })
    }
}
