package com.anilocal.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal const val CATALOG_LOAD_TIMEOUT_MS = 20_000L

enum class CatalogLoadState { Loading, Ready, Failed }

/** Retains useful content during a refresh and allows retry after a bounded network wait. */
internal class CatalogLoad<T>(
    private val scope: CoroutineScope,
    initialValue: T,
    private val timeoutMs: Long = CATALOG_LOAD_TIMEOUT_MS,
    /** Number of bounded, automatic retries after the initial request fails. */
    private val autoRetryAttempts: Int = 0,
    private val retryDelayMs: Long = 1_000L,
    private val load: suspend () -> T,
) {
    private val _value = MutableStateFlow(initialValue)
    val value: StateFlow<T> = _value
    private val _state = MutableStateFlow(CatalogLoadState.Loading)
    val state: StateFlow<CatalogLoadState> = _state
    private var active: Job? = null

    init { refresh() }

    fun refresh() {
        if (!scope.isActive || active?.isActive == true) return
        _state.value = CatalogLoadState.Loading
        active = scope.launch {
            var attempt = 0
            while (true) {
                catalogResult(timeoutMs, load).fold(
                    { result ->
                        _value.value = result
                        _state.value = CatalogLoadState.Ready
                        return@launch
                    },
                    {
                        if (attempt >= autoRetryAttempts.coerceAtLeast(0)) {
                            _state.value = CatalogLoadState.Failed
                            return@launch
                        }
                        attempt++
                        // Keep stale content and the Loading state visible while a transient
                        // connection failure is retried. Cancellation from a leaving screen
                        // propagates through delay and aborts the retry loop.
                        delay(retryDelayMs.coerceAtLeast(0L) * attempt)
                    },
                )
            }
        }
    }
}

internal suspend fun <T> catalogResult(timeoutMs: Long, load: suspend () -> T): Result<T> = try {
    val result = withTimeout(timeoutMs) { load() }
    currentCoroutineContext().ensureActive()
    Result.success(result)
} catch (timeout: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(timeout)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    currentCoroutineContext().ensureActive()
    Result.failure(error)
}
