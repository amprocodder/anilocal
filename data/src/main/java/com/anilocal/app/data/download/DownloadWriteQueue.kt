package com.anilocal.app.data.download

import com.anilocal.app.data.local.DownloadStateUpdate

internal data class DownloadWriteBatch(
    val updates: List<DownloadStateUpdate>,
    val removedIds: List<String>,
) {
    val isEmpty: Boolean get() = updates.isEmpty() && removedIds.isEmpty()
}

/** Keeps only the newest observation per ID, even while a previous batch is being written. */
internal class DownloadWriteQueue {
    private sealed interface Change {
        data class Update(val value: DownloadStateUpdate) : Change
        data object Remove : Change
    }

    private val pending = LinkedHashMap<String, Change>()
    private val inFlight = HashMap<String, Change>()
    private val written = HashMap<String, Change>()

    @Synchronized
    fun update(value: DownloadStateUpdate): Boolean = record(value.id, Change.Update(value))

    @Synchronized
    fun remove(id: String): Boolean = record(id, Change.Remove)

    private fun record(id: String, change: Change): Boolean {
        if (pending[id] == change || (id !in pending && (inFlight[id] ?: written[id]) == change)) return false
        pending[id] = change
        return true
    }

    @Synchronized
    fun drain(): DownloadWriteBatch {
        val batch = DownloadWriteBatch(
            pending.values.mapNotNull { (it as? Change.Update)?.value },
            pending.filterValues { it == Change.Remove }.keys.toList(),
        )
        inFlight.putAll(pending)
        pending.clear()
        return batch
    }

    @Synchronized
    fun committed(batch: DownloadWriteBatch) {
        batch.updates.forEach { acknowledge(it.id, Change.Update(it)) }
        batch.removedIds.forEach { acknowledge(it, Change.Remove) }
    }

    private fun acknowledge(id: String, change: Change) {
        written[id] = change
        if (inFlight[id] == change) inFlight.remove(id)
        if (pending[id] == change) pending.remove(id)
    }

    /** Failed writes are retried, without replacing a newer native observation. */
    @Synchronized
    fun retry(batch: DownloadWriteBatch) {
        batch.updates.forEach {
            inFlight.remove(it.id)
            pending.putIfAbsent(it.id, Change.Update(it))
        }
        batch.removedIds.forEach {
            inFlight.remove(it)
            pending.putIfAbsent(it, Change.Remove)
        }
    }

    /** Called under the writer's mutex before creating or removing an episode row. */
    @Synchronized
    fun forget(id: String) {
        pending.remove(id)
        inFlight.remove(id)
        written.remove(id)
    }
}
