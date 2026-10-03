package com.anilocal.app.ui.library

import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.MalListEntry
import com.anilocal.app.domain.model.MalStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

internal data class LibraryProjection(
    val all: List<AnimeSummary> = emptyList(),
    val byStatus: Map<MalStatus, List<AnimeSummary>> = emptyMap(),
) {
    fun items(status: MalStatus?): List<AnimeSummary> = if (status == null) all else byStatus[status].orEmpty()
}

/** One linear pass per database update, reused by every filter without re-querying Room. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun libraryProjections(
    local: Flow<List<AnimeSummary>>,
    mal: Flow<List<MalListEntry>>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<LibraryProjection> = combine(local.distinctUntilChanged(), mal.distinctUntilChanged()) { local, mal ->
    local to mal
}.mapLatest { (local, mal) ->
    withContext(dispatcher) {
        val context = currentCoroutineContext()
        val localMalIds = HashSet<Int>(local.size)
        val ids = HashSet<String>(local.size + mal.size)
        val merged = ArrayList<AnimeSummary>(local.size + mal.size)
        val byStatus = HashMap<MalStatus, MutableList<AnimeSummary>>(MalStatus.entries.size)
        local.forEachIndexed { index, item ->
            if (index % 256 == 0) context.ensureActive()
            item.idMal?.let(localMalIds::add)
            if (ids.add(item.id)) merged.add(item)
        }
        mal.forEachIndexed { index, entry ->
            if (index % 256 == 0) context.ensureActive()
            val summary = AnimeSummary("mal-${entry.malId}", entry.title, entry.posterUrl, entry.malId)
            byStatus.getOrPut(entry.status) { ArrayList() }.add(summary)
            if (entry.malId !in localMalIds && ids.add(summary.id)) merged.add(summary)
        }
        LibraryProjection(merged, byStatus)
    }
}.flowOn(dispatcher)
