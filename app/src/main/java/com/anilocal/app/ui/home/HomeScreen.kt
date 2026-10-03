package com.anilocal.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.CatalogFeedback
import com.anilocal.app.ui.common.CatalogLoad
import com.anilocal.app.ui.common.CatalogLoadState
import com.anilocal.app.ui.common.loadOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(val title: String, val items: List<AnimeSummary>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val progress: ProgressRepository,
    downloads: DownloadRepository,
) : ViewModel() {

    private val loader = CatalogLoad(viewModelScope, emptyList<HomeRow>()) {
        val home = catalog.home()
        listOf(
            HomeRow("Trending Now", home.trending),
            HomeRow("Popular This Season", home.popularThisSeason),
            HomeRow("Top Airing", home.topAiring),
            HomeRow("All-Time Popular", home.allTimePopular),
            HomeRow("Upcoming", home.upcoming),
        ).filter { it.items.isNotEmpty() }
    }
    val rows = loader.value
    val loadState = loader.state
    fun retry() = loader.refresh()

    val continueWatching: StateFlow<List<ContinueWatching>> =
        progress.continueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Permanently drop an item from the Continue Watching row. */
    fun removeFromContinue(animeId: String) = viewModelScope.launch { loadOrNull { progress.remove(animeId) } }

}

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onResume: (animeId: String, episodeNumber: Int, positionMs: Long) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "heading", contentType = "heading") {
            Text("AniLocal", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (loadState != CatalogLoadState.Ready) {
            item(key = "load-state", contentType = "load-state") { CatalogFeedback(loadState, vm::retry) }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "shelf") {
                ContinueWatchingShelf(
                    items = continueWatching,
                    downloadedIds = downloadedIds,
                    onResume = onResume,
                    onRemove = vm::removeFromContinue,
                )
            }
        }
        items(rows, key = { it.title }, contentType = { "shelf" }) { row ->
            Shelf(row.title, row.items, downloadedIds, onOpen)
        }
    }
}

@Composable
private fun ContinueWatchingShelf(
    items: List<ContinueWatching>,
    downloadedIds: Set<String>,
    onResume: (animeId: String, episodeNumber: Int, positionMs: Long) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Continue Watching", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items, key = { it.anime.id }, contentType = { "poster" }) { cw ->
                PosterCard(
                    item = cw.anime,
                    onClick = { onResume(cw.anime.id, cw.episodeNumber, cw.positionMs) },
                    downloaded = cw.anime.id in downloadedIds,
                    progress = cw.fraction,
                    onRemove = { onRemove(cw.anime.id) },
                )
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    items: List<AnimeSummary>,
    downloadedIds: Set<String>,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items, key = { it.id }, contentType = { "poster" }) {
                PosterCard(it, onClick = { onOpen(it.id) }, downloaded = it.id in downloadedIds)
            }
        }
    }
}
