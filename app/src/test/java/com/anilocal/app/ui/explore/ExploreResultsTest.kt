package com.anilocal.app.ui.explore

import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.ui.common.CatalogLoadState
import com.anilocal.app.ui.performance.FakeCatalog
import com.anilocal.app.ui.performance.summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreResultsTest {
    @Test fun rapidTypingPerformsOneSearchAfterTheQuietPeriod() = runTest {
        val catalog = FakeCatalog()
        val browser = ExploreResults(backgroundScope, catalog)
        runCurrent()
        for (text in listOf("n", "na", "nar", "naru", "naruto")) {
            browser.onQuery(text)
            runCurrent()
            advanceTimeBy(50)
        }
        runCurrent()
        assertTrue(catalog.searches.isEmpty())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("naruto"), catalog.searches)
        assertEquals(listOf(summary("naruto")), browser.results.value)
    }

    @Test fun newInputCancelsThePreviousNetworkCallBeforeItsOwnDebounce() = runTest {
        val catalog = FakeCatalog()
        var cancelled = false
        catalog.searchBlock = { query ->
            if (query == "old") {
                try { CompletableDeferred<Unit>().await() }
                finally { cancelled = true }
            }
            listOf(summary(query))
        }
        val browser = ExploreResults(backgroundScope, catalog)
        browser.onQuery("old")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("old"), catalog.searches)
        browser.onQuery("new")
        runCurrent()
        assertTrue(cancelled)
        assertEquals(listOf("old"), catalog.searches)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(summary("new")), browser.results.value)
    }

    @Test fun repositoryThatSwallowsCancellationCannotPublishStaleResults() = runTest {
        val catalog = FakeCatalog()
        catalog.searchBlock = { query ->
            if (query == "old") {
                try { delay(5_000) }
                catch (_: CancellationException) { withContext(NonCancellable) { delay(20) } }
            }
            listOf(summary(query))
        }
        val browser = ExploreResults(backgroundScope, catalog)
        browser.onQuery("old")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        browser.onQuery("new")
        runCurrent()
        advanceTimeBy(20)
        runCurrent()
        assertTrue(browser.results.value.isEmpty())
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(summary("new")), browser.results.value)
    }

    @Test fun browsingAndFilterChangesRunImmediatelyAndClearingSearchReturnsToCurrentFilters() = runTest {
        val catalog = FakeCatalog()
        val browser = ExploreResults(backgroundScope, catalog)
        runCurrent()
        browser.onGenre("Action")
        browser.onSort(BrowseSort.SCORE)
        runCurrent()
        assertEquals("Action" to BrowseSort.SCORE, catalog.browses.last())
        browser.onQuery("title")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        browser.onGenre("Drama")
        browser.onSort(BrowseSort.NEWEST)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("title"), catalog.searches)
        browser.onQuery("   ")
        runCurrent()
        assertEquals("Drama" to BrowseSort.NEWEST, catalog.browses.last())
    }

    @Test fun normalizedEquivalentQueriesDoNotRepeatRequests() = runTest {
        val catalog = FakeCatalog()
        val browser = ExploreResults(backgroundScope, catalog)
        browser.onQuery("title")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        browser.onQuery(" title ")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("title"), catalog.searches)
        assertEquals(" title ", browser.query.value)
    }

    @Test fun requestFailuresDoNotStopFutureSearches() = runTest {
        val catalog = FakeCatalog()
        catalog.searchBlock = { query ->
            if (query == "fail") error("Offline") else listOf(summary(query))
        }
        val browser = ExploreResults(backgroundScope, catalog)
        browser.onQuery("fail")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertTrue(browser.results.value.isEmpty())
        browser.onQuery("works")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(summary("works")), browser.results.value)
    }

    @Test fun failedSearchKeepsUsefulResultsAndRetryRecoversTheSameQuery() = runTest {
        val catalog = FakeCatalog()
        var attempts = 0
        catalog.searchBlock = { query ->
            attempts++
            if (attempts == 1) error("Offline")
            listOf(summary(query))
        }
        val browser = ExploreResults(backgroundScope, catalog)
        runCurrent()
        val initial = browser.results.value
        browser.onQuery("title")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(CatalogLoadState.Failed, browser.loadState.value)
        assertSame(initial, browser.results.value)
        repeat(10) { browser.retry() }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(CatalogLoadState.Ready, browser.loadState.value)
        assertEquals(listOf(summary("title")), browser.results.value)
    }

    @Test fun stalledSearchStopsAtItsDeadlineAndLeavesFiltersResponsive() = runTest {
        val catalog = FakeCatalog().apply { searchBlock = { delay(5_000); emptyList() } }
        val browser = ExploreResults(backgroundScope, catalog, timeoutMs = 1_000)
        browser.onQuery("title")
        runCurrent()
        advanceTimeBy(1_300)
        runCurrent()
        assertEquals(CatalogLoadState.Failed, browser.loadState.value)
        browser.onQuery("")
        runCurrent()
        assertEquals(CatalogLoadState.Ready, browser.loadState.value)
        assertEquals(listOf(summary("browse")), browser.results.value)
    }
}
