package com.anilocal.app.ui.common

import kotlinx.coroutines.CancellationException
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
class CatalogLoadTest {
    @Test fun transientFailureCanRetryAndRapidRetryTapsShareTheActiveRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val loader = CatalogLoad(backgroundScope, 0) {
            calls++
            if (calls == 1) error("Offline")
            release.await()
            42
        }
        runCurrent()
        assertEquals(CatalogLoadState.Failed, loader.state.value)
        repeat(10) { loader.refresh() }
        runCurrent()
        assertEquals(2, calls)
        assertEquals(CatalogLoadState.Loading, loader.state.value)
        release.complete(Unit)
        runCurrent()
        assertEquals(42, loader.value.value)
        assertEquals(CatalogLoadState.Ready, loader.state.value)
    }

    @Test fun refreshingRetainsExistingContentDuringLoadingAndOnFailure() = runTest {
        var calls = 0
        val loader = CatalogLoad(backgroundScope, emptyList<Int>()) {
            calls++
            if (calls == 2) { delay(500); error("Connection lost") }
            listOf(calls)
        }
        runCurrent()
        val content = loader.value.value
        loader.refresh()
        runCurrent()
        assertSame(content, loader.value.value)
        assertEquals(CatalogLoadState.Loading, loader.state.value)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(CatalogLoadState.Failed, loader.state.value)
        assertSame(content, loader.value.value)
        loader.refresh()
        runCurrent()
        assertEquals(listOf(3), loader.value.value)
    }

    @Test fun hungMetadataLoadEndsAtTheDeadlineAndCanRetry() = runTest {
        var calls = 0
        var cancelled = false
        val loader = CatalogLoad(backgroundScope, 0, timeoutMs = 1_000) {
            calls++
            if (calls == 1) {
                try { CompletableDeferred<Unit>().await() }
                finally { cancelled = true }
            }
            7
        }
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(CatalogLoadState.Loading, loader.state.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(CatalogLoadState.Failed, loader.state.value)
        loader.refresh()
        runCurrent()
        assertEquals(7, loader.value.value)
    }

    @Test fun cancellationCannotPublishAResultEvenWhenTheLoaderSwallowsIt() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val loader = CatalogLoad(scope, 0) {
            try { CompletableDeferred<Unit>().await() }
            catch (_: CancellationException) { }
            99
        }
        runCurrent()
        scope.cancel()
        runCurrent()
        assertEquals(0, loader.value.value)
        assertEquals(CatalogLoadState.Loading, loader.state.value)
        loader.refresh()
        runCurrent()
        assertEquals(0, loader.value.value)
    }
}
