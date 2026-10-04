package com.anilocal.app.ui.explore

import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.ui.common.CATALOG_LOAD_TIMEOUT_MS
import com.anilocal.app.ui.common.CatalogLoadState
import com.anilocal.app.ui.common.catalogResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Cancels obsolete work immediately; only typing waits for a short quiet period. */
internal class ExploreResults(
    scope: CoroutineScope,
    private val catalog: CatalogRepository,
    private val searchDelayMs: Long = 300,
    private val timeoutMs: Long = CATALOG_LOAD_TIMEOUT_MS,
    /** Number of bounded retries for transient catalog/network failures. */
    private val autoRetryAttempts: Int = 0,
    private val retryDelayMs: Long = 1_000L,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre
    private val _sort = MutableStateFlow(BrowseSort.POPULAR)
    val sort: StateFlow<BrowseSort> = _sort
    private val _results = MutableStateFlow<List<AnimeSummary>>(emptyList())
    val results: StateFlow<List<AnimeSummary>> = _results
    private val _loadState = MutableStateFlow(CatalogLoadState.Loading)
    val loadState: StateFlow<CatalogLoadState> = _loadState
    private val retries = MutableStateFlow(0L)

    init {
        scope.launch {
            combine(query, genre, sort, retries) { query, genre, sort, retry ->
                val text = query.trim()
                // Filters do not affect text search; changing them need not repeat that request.
                if (text.isEmpty()) Request(text, genre, sort, retry)
                else Request(text, null, BrowseSort.POPULAR, retry)
            }.distinctUntilChanged().collectLatest { request ->
                if (request.query.isNotEmpty()) delay(searchDelayMs)
                _loadState.value = CatalogLoadState.Loading
                var attempt = 0
                while (true) {
                    val result = catalogResult(timeoutMs) {
                        if (request.query.isEmpty()) catalog.browse(request.genre, request.sort)
                        else catalog.search(request.query)
                    }
                    // Also protects against a repository implementation that swallowed cancellation.
                    currentCoroutineContext().ensureActive()
                    var succeeded = false
                    result.fold(
                        { items ->
                            _results.value = items
                            _loadState.value = CatalogLoadState.Ready
                            succeeded = true
                        },
                        {
                            if (attempt >= autoRetryAttempts.coerceAtLeast(0)) {
                                _loadState.value = CatalogLoadState.Failed
                                succeeded = true // terminal; leave the loop below
                            } else {
                                attempt++
                            }
                        },
                    )
                    if (succeeded) break
                    // Keep stale results visible and leave the retry indicator up while waiting.
                    // collectLatest cancellation aborts this delay when the query/filter changes.
                    delay(retryDelayMs.coerceAtLeast(0L) * attempt)
                }
            }
        }
    }

    fun onQuery(query: String) { _query.value = query }
    fun onGenre(genre: String?) { _genre.value = genre }
    fun onSort(sort: BrowseSort) { _sort.value = sort }
    fun retry() {
        if (_loadState.value == CatalogLoadState.Loading) return
        _loadState.value = CatalogLoadState.Loading
        retries.value++
    }

    private data class Request(val query: String, val genre: String?, val sort: BrowseSort, val retry: Long)
}
