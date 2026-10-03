package com.anilocal.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.ui.common.CatalogLoadState
import com.anilocal.app.ui.performance.*
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
class DetailsViewModelTest {
    @Test fun failedDetailsCanRetryAndLoadedDetailsRemainAvailableAfterARefreshFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var attempts = 0
            val catalog = FakeCatalog().apply { detailBlock = { id ->
                attempts++
                if (attempts != 2) error("Offline")
                AnimeDetail(id, "Recovered anime", null)
            } }
            val vm = DetailsViewModel(SavedStateHandle(mapOf("animeId" to "1")), catalog, library, FakeStreams(), FakeSkip(), FakeDownloads(), FakeSettings())
            store.put("details", vm)
            runCurrent()
            assertNull(vm.detail.value)
            assertEquals(CatalogLoadState.Failed, vm.loadState.value)
            vm.retry()
            runCurrent()
            assertEquals("Recovered anime", vm.detail.value?.title)
            assertEquals(CatalogLoadState.Ready, vm.loadState.value)
            val detail = vm.detail.value
            vm.retry()
            runCurrent()
            assertEquals(CatalogLoadState.Failed, vm.loadState.value)
            assertSame(detail, vm.detail.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private val library = object : LibraryRepository {
        override val library = MutableStateFlow<List<AnimeSummary>>(emptyList())
        override suspend fun toggle(item: AnimeSummary) = Unit
        override fun isSaved(id: String) = MutableStateFlow(false)
    }
}
