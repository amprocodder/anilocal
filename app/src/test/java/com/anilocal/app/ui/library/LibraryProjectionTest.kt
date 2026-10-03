package com.anilocal.app.ui.library

import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.MalStatus
import com.anilocal.app.ui.performance.malEntry
import com.anilocal.app.ui.performance.summary
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryProjectionTest {
    @Test fun largeLibrariesMergeAndBuildAllCategoriesOnAWorkerThreadInOnePass() {
        val caller = Thread.currentThread()
        val observedThreads = mutableSetOf<Thread>()
        val localValues = List(1_000) { summary("local-$it", it) }
        val local = object : AbstractList<AnimeSummary>() {
            override val size = localValues.size
            override fun get(index: Int): AnimeSummary {
                observedThreads += Thread.currentThread()
                return localValues[index]
            }
        }
        val mal = List(50_000) { malEntry(it, MalStatus.entries[it % MalStatus.entries.size]) }
        val worker = Executors.newSingleThreadExecutor { Thread(it, "library-projection-worker") }.asCoroutineDispatcher()
        try {
            val projection = runBlocking { libraryProjections(flowOf(local), flowOf(mal), worker).first() }
            assertEquals(50_000, projection.all.size)
            assertSame(localValues.first(), projection.all.first())
            assertEquals("mal-49999", projection.all.last().id)
            assertEquals(10_000, projection.items(MalStatus.WATCHING).size)
            assertEquals(1, observedThreads.size)
            assertTrue(observedThreads.single().name.startsWith("library-projection-worker"))
            assertFalse(caller in observedThreads)
        } finally {
            worker.close()
        }
    }

    @Test fun filteringReusesMaterializedListsWithoutResubscribingOrRemapping() = runTest {
        val filter = MutableStateFlow<MalStatus?>(null)
        val locals = MutableStateFlow(listOf(summary("local", 1)))
        val mal = MutableStateFlow(listOf(malEntry(1), malEntry(2), malEntry(3, MalStatus.COMPLETED)))
        var localSubscriptions = 0
        var malSubscriptions = 0
        val projections = libraryProjections(
            flow { localSubscriptions++; emitAll(locals) },
            flow { malSubscriptions++; emitAll(mal) },
            UnconfinedTestDispatcher(testScheduler),
        )
        val emissions = mutableListOf<List<AnimeSummary>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            combine(filter, projections) { status, projection -> projection.items(status) }.collect { emissions += it }
        }
        runCurrent()
        val merged = emissions.last()
        assertEquals(listOf("local", "mal-2", "mal-3"), merged.map { it.id })
        filter.value = MalStatus.WATCHING
        runCurrent()
        assertEquals(listOf("mal-1", "mal-2"), emissions.last().map { it.id })
        filter.value = MalStatus.COMPLETED
        runCurrent()
        assertEquals(listOf("mal-3"), emissions.last().map { it.id })
        filter.value = null
        runCurrent()
        assertSame(merged, emissions.last())
        assertEquals(1, localSubscriptions)
        assertEquals(1, malSubscriptions)
    }

    @Test fun databaseChangesRefreshTheMergeAndEmptyCategories() = runTest {
        val locals = MutableStateFlow(listOf(summary("local", 1)))
        val mal = MutableStateFlow(listOf(malEntry(1), malEntry(2)))
        val emissions = mutableListOf<LibraryProjection>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            libraryProjections(locals, mal, UnconfinedTestDispatcher(testScheduler)).collect { emissions += it }
        }
        runCurrent()
        assertEquals(listOf("local", "mal-2"), emissions.last().all.map { it.id })
        locals.value = emptyList()
        runCurrent()
        assertEquals(listOf("mal-1", "mal-2"), emissions.last().all.map { it.id })
        mal.value = emptyList()
        runCurrent()
        assertTrue(emissions.last().all.isEmpty())
        assertTrue(emissions.last().items(MalStatus.WATCHING).isEmpty())
    }
}
