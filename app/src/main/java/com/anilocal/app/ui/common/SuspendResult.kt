package com.anilocal.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Optional UI data can fail quietly, but cancelled screens must stop their work. */
internal suspend inline fun <T> loadOrNull(block: () -> T): T? = try {
    val result = block()
    currentCoroutineContext().ensureActive()
    result
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    currentCoroutineContext().ensureActive()
    null
}
