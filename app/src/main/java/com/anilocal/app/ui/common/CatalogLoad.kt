package com.anilocal.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
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
            catalogResult(timeoutMs, load).fold(
                { result -> _value.value = result; _state.value = CatalogLoadState.Ready },
                { _state.value = CatalogLoadState.Failed },
            )
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
