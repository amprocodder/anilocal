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
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.ui.common.PosterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(val title: String, val items: List<AnimeSummary>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    progress: ProgressRepository,
    downloads: DownloadRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows

    val continueWatching: StateFlow<List<AnimeSummary>> =
        progress.continueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // Load the AniLab-style rows; each appears as soon as it returns (ordered).
        val sections: List<Pair<String, suspend () -> List<AnimeSummary>>> = listOf(
            "Trending Now" to { catalog.trending() },
            "Popular This Season" to { catalog.popularThisSeason() },
            "Top Airing" to { catalog.topAiring() },
            "All-Time Popular" to { catalog.allTimePopular() },
            "Upcoming" to { catalog.upcoming() },
        )
        viewModelScope.launch {
            for ((title, loader) in sections) {
                val items = runCatching { loader() }.getOrDefault(emptyList())
                if (items.isNotEmpty()) _rows.value = _rows.value + HomeRow(title, items)
            }
        }
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("AniLocal", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (continueWatching.isNotEmpty()) {
            item { Shelf("Continue Watching", continueWatching, downloadedIds, onOpen) }
        }
        items(rows, key = { it.title }) { row ->
            Shelf(row.title, row.items, downloadedIds, onOpen)
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
            items(items, key = { it.id }) {
                PosterCard(it, onClick = { onOpen(it.id) }, downloaded = it.id in downloadedIds)
            }
        }
    }
}
