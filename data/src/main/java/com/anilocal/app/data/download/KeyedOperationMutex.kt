package com.anilocal.app.data.download

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes operations for one episode, while allowing other episodes to proceed. */
internal class KeyedOperationMutex {
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = HashMap<String, Entry>()

    suspend fun <T> withLock(key: String, action: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { action() }
        } finally {
            synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }
}
