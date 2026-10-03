package com.anilocal.app.ui.home

import androidx.lifecycle.ViewModelStore
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.ui.performance.FakeCatalog
import com.anilocal.app.ui.performance.FakeDownloads
import com.anilocal.app.ui.performance.summary
import com.anilocal.app.ui.common.CatalogLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test fun homeUsesOneBatchAndKeepsShelfOrderWhileDroppingEmptyShelves() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val catalog = object : FakeCatalog() {
                override suspend fun home(): HomeCatalog {
                    calls++
                    return HomeCatalog(listOf(summary("trending")), emptyList(), listOf(summary("airing")), listOf(summary("popular")), listOf(summary("upcoming")))
                }
            }
            val vm = HomeViewModel(catalog, progress, FakeDownloads())
            store.put("home", vm)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(listOf("Trending Now", "Top Airing", "All-Time Popular", "Upcoming"), vm.rows.value.map { it.title })
            assertEquals(listOf("trending", "airing", "popular", "upcoming"), vm.rows.value.map { it.items.single().id })
            assertTrue(catalog.browses.isEmpty())
            assertTrue(catalog.searches.isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun batchFailureKeepsTheScreenUsable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val catalog = object : FakeCatalog() {
                override suspend fun home(): HomeCatalog {
                    calls++
                    if (calls == 1) error("Offline")
                    return HomeCatalog(listOf(summary("recovered")), emptyList(), emptyList(), emptyList(), emptyList())
                }
            }
            val vm = HomeViewModel(catalog, progress, FakeDownloads())
            store.put("home", vm)
            runCurrent()
            assertTrue(vm.rows.value.isEmpty())
            assertEquals(CatalogLoadState.Failed, vm.loadState.value)
            vm.retry()
            runCurrent()
            assertEquals(CatalogLoadState.Ready, vm.loadState.value)
            assertEquals("recovered", vm.rows.value.single().items.single().id)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private val progress = object : ProgressRepository {
        override val continueWatching = MutableStateFlow<List<ContinueWatching>>(emptyList())
        override suspend fun save(anime: AnimeSummary, episodeNumber: Int, positionMs: Long, durationMs: Long) = Unit
        override suspend fun remove(animeId: String) = Unit
    }
}
