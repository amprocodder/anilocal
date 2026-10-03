package com.anilocal.app.data.download

import com.anilocal.app.data.local.CompletedDownloadRow

/** An index snapshot cannot overrule operations or native callbacks observed while it was read. */
internal class StartupDownloadReconciliation {
    private val touched = HashSet<String>()
    private var finished = false

    @Synchronized
    fun touch(id: String) {
        if (!finished) touched.add(id)
    }

    @Synchronized
    fun wasTouched(id: String): Boolean = id in touched

    @Synchronized
    fun orphaned(
        completedRows: List<CompletedDownloadRow>,
        nativeIds: Set<String>,
        liveIds: Set<String>,
    ): List<CompletedDownloadRow> =
        if (finished) emptyList()
        else completedRows.filter { it.id !in nativeIds && it.id !in liveIds && it.id !in touched }

    @Synchronized
    fun finish() {
        finished = true
        touched.clear()
    }
}
