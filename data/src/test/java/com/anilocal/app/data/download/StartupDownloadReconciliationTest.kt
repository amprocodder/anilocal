package com.anilocal.app.data.download

import com.anilocal.app.data.local.CompletedDownloadRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDownloadReconciliationTest {
    @Test fun onlyMissingCompletedRowsAreInvalidated() {
        val startup = StartupDownloadReconciliation()
        val rows = listOf(row("stored"), row("live"), row("missing"))
        assertEquals(listOf(row("missing")), startup.orphaned(rows, setOf("stored"), setOf("live")))
    }

    @Test fun nativeCallbacksAndEnqueueOrRemovalIntentsDuringSnapshotProtectThoseRows() {
        val startup = StartupDownloadReconciliation()
        val rows = listOf(row("native-change"), row("enqueue"), row("remove"), row("orphan"))
        startup.touch("native-change")
        startup.touch("enqueue")
        startup.touch("remove")
        assertEquals(listOf(row("orphan")), startup.orphaned(rows, emptySet(), emptySet()))
    }

    @Test fun anIntentAfterTheInitialOrphanSnapshotIsExcludedWhenTheWriterRechecks() {
        val startup = StartupDownloadReconciliation()
        val candidates = startup.orphaned(listOf(row("episode")), emptySet(), emptySet())
        assertEquals(listOf(row("episode")), candidates)
        startup.touch("episode")
        assertTrue(startup.orphaned(candidates, emptySet(), emptySet()).isEmpty())
    }

    @Test fun startupIntentsBeforeTheIndexReadAreKeptAndFinishedReconciliationCannotInvalidateAgain() {
        val startup = StartupDownloadReconciliation()
        startup.touch("early-enqueue")
        assertTrue(startup.wasTouched("early-enqueue"))
        assertTrue(startup.orphaned(listOf(row("early-enqueue")), emptySet(), emptySet()).isEmpty())
        startup.finish()
        startup.touch("after-startup")
        assertTrue(startup.orphaned(listOf(row("missing")), emptySet(), emptySet()).isEmpty())
    }

    private fun row(id: String) = CompletedDownloadRow(id, 1L)
}
