package com.anilocal.app.data.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendingLruCacheTest {
    @Test fun burstOfDistinctKeysIsBoundedAndQueuedCancellationDoesNotStartARequest() = runTest {
        val cache = SuspendingLruCache<Int, Int>(30, 100, { 0L }, StandardTestDispatcher(testScheduler), maxInFlight = 3)
        val release = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        var active = 0
        var maximumActive = 0
        val requests = (0 until 20).map { key ->
            async {
                cache.getOrLoad(key) {
                    requested += key
                    active++
                    maximumActive = maxOf(maximumActive, active)
                    try { release.await(); key } finally { active-- }
                }
            }
        }
        runCurrent()
        assertEquals(listOf(0, 1, 2), requested)
        val duplicate = async { cache.getOrLoad(0) { error("Duplicate must share even at capacity") } }
        runCurrent()
        requests[0].cancelAndJoin()
        requests[10].cancelAndJoin()
        assertFalse(duplicate.isCompleted)
        release.complete(Unit)
        assertEquals(0, duplicate.await())
        requests.forEachIndexed { index, request ->
            if (index != 0 && index != 10) assertEquals(index, request.await())
        }
        assertEquals(3, maximumActive)
        assertEquals(19, requested.size)
        assertFalse(10 in requested)
        assertEquals(0, active)
    }

    @Test fun cancellingBeforeLoaderStartsReleasesItsAdmissionSlot() = runTest {
        val cache = SuspendingLruCache<String, Int>(1, 100, dispatcher = StandardTestDispatcher(testScheduler), maxInFlight = 1)
        var obsoleteLoads = 0
        val request = async(Dispatchers.Unconfined) { cache.getOrLoad("old") { obsoleteLoads++; 1 } }
        request.cancelAndJoin()
        runCurrent()
        assertEquals(0, obsoleteLoads)
        assertEquals(2, cache.getOrLoad("new") { 2 })
    }

    @Test fun concurrentCallersShareOneRequestAndOneCanCancelWithoutCancellingTheOther() = runTest {
        val cache = SuspendingLruCache<String, Int>(3, 100, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val first = async { cache.getOrLoad("anime") { requests++; release.await(); 7 } }
        val second = async { cache.getOrLoad("anime") { requests++; 99 } }
        runCurrent()
        assertEquals(1, requests)
        first.cancelAndJoin()
        assertFalse(second.isCompleted)
        release.complete(Unit)
        assertEquals(7, second.await())
        assertEquals(7, cache.getOrLoad("anime") { error("Already cached") })
    }

    @Test fun leavingTheOnlyScreenCancelsItsNetworkRequestAndAllowsAFreshRequest() = runTest {
        val cache = SuspendingLruCache<String, Int>(3, 100, dispatcher = StandardTestDispatcher(testScheduler))
        val requestCancelled = CompletableDeferred<Unit>()
        val first = async {
            cache.getOrLoad("anime") {
                try { awaitCancellation() } finally { requestCancelled.complete(Unit) }
            }
        }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        assertTrue(requestCancelled.isCompleted)
        assertEquals(42, cache.getOrLoad("anime") { 42 })
    }

    @Test fun expiryAndLruLimitReloadOnlyExpiredOrEvictedResultsIncludingCachedNulls() = runTest {
        var now = 0L
        val cache = SuspendingLruCache<String, Int?>(2, 100, { now }, StandardTestDispatcher(testScheduler))
        assertEquals(1, cache.getOrLoad("a") { 1 })
        assertEquals(null, cache.getOrLoad("b") { null })
        assertEquals(null, cache.getOrLoad("b") { error("A successful null is cached") })
        assertEquals(1, cache.getOrLoad("a") { error("Hit") })
        assertEquals(3, cache.getOrLoad("c") { 3 })
        assertEquals(1, cache.getOrLoad("a") { error("Recently used entry must survive") })
        assertEquals(2, cache.getOrLoad("b") { 2 })
        now = 100
        assertEquals(4, cache.getOrLoad("a") { 4 })
    }

    @Test fun failedRequestsAreRetriedAndDoNotPoisonTheCache() = runTest {
        val cache = SuspendingLruCache<String, Int>(1, 100, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            cache.getOrLoad("anime") { throw IOException("Disconnected") }
            error("Failure expected")
        } catch (_: IOException) { }
        assertEquals(8, cache.getOrLoad("anime") { 8 })
    }
}
