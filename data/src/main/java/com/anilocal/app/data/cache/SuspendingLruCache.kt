package com.anilocal.app.data.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bounded, expiring results with one cancellable request per key across callers. */
internal class SuspendingLruCache<K, V>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxInFlight: Int = 16,
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private class Flight<V>(val result: Deferred<V>, var waiters: Int = 0)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true)
    private val flights = mutableMapOf<K, Flight<V>>()
    private var capacityChanged = CompletableDeferred<Unit>()

    init {
        require(maxEntries > 0)
        require(ttlMillis >= 0)
        require(maxInFlight > 0)
    }

    suspend fun getOrLoad(key: K, shouldCache: (V) -> Boolean = { true }, loader: suspend () -> V): V {
        currentCoroutineContext().ensureActive()
        val flight: Flight<V>
        while (true) {
            var waitForCapacity: Deferred<Unit>? = null
            val acquired = mutex.withLock {
                val cached = entries[key]
                if (cached != null && cached.expiresAt > nowMillis()) return cached.value
                entries.remove(key)

                val existing = flights[key]
                if (existing?.result?.isCompleted == true) removeFlight(key, existing)
                val current = flights[key]
                if (current?.result?.isCancelled == true || (current == null && flights.size >= maxInFlight)) {
                    // Waiting callers allocate no loader job or map entry and remain cancellable.
                    waitForCapacity = capacityChanged
                    return@withLock null
                }
                val admitted = current ?: run {
                    lateinit var created: Flight<V>
                    val result = scope.async(start = CoroutineStart.LAZY) {
                        val value = loader()
                        mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            if (flights[key] === created && shouldCache(value)) store(key, value)
                        }
                        value
                    }
                    Flight(result).also {
                        created = it
                        flights[key] = it
                        // Completion also handles a lazy request cancelled before its body starts.
                        result.invokeOnCompletion {
                            scope.launch { mutex.withLock { removeFlight(key, created) } }
                        }
                    }
                }
                admitted.waiters++
                admitted
            }
            if (acquired != null) {
                flight = acquired
                break
            }
            waitForCapacity!!.await()
        }

        try {
            return flight.result.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    flight.waiters--
                    if (flight.waiters == 0 && flights[key] === flight && !flight.result.isCompleted) {
                        // Hold the admission slot until the underlying request actually stops.
                        flight.result.cancel()
                    }
                }
            }
        }
    }

    /** Seed a result obtained in a batch request, sharing it with individual queries. */
    suspend fun put(key: K, value: V) = mutex.withLock { store(key, value) }

    /** Called with the mutex held; every removal wakes capacity waiters to recheck their key. */
    private fun removeFlight(key: K, expected: Flight<V>) {
        if (flights[key] !== expected) return
        flights.remove(key)
        capacityChanged.complete(Unit)
        capacityChanged = CompletableDeferred()
    }

    private fun store(key: K, value: V) {
        val now = nowMillis()
        entries.entries.removeAll { it.value.expiresAt <= now }
        entries[key] = Entry(value, now + ttlMillis)
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }
}
