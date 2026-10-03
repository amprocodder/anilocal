package eu.kanade.tachiyomi.util

import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle as coreAwaitSingle

// Upstream this is a KMP androidMain `actual` delegating to the common bridge. Flattened here to a
// plain function (no expect/actual) that forwards to the vendored RxCoroutineBridge. Aliased import
// avoids same-name recursion. This is the `eu.kanade.tachiyomi.util.awaitSingle` AnimeSource uses.
suspend fun <T> Observable<T>.awaitSingle(): T = coreAwaitSingle()
