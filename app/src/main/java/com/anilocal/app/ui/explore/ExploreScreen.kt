package com.anilocal.app.ui.explore

import com.anilocal.app.ui.common.ScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anilocal.app.appViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.AniListGenres
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.ui.common.CatalogFeedback
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.SearchField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.anilocal.app.ui.kit.FilterChip
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

class ExploreViewModel(
    private val catalog: CatalogRepository,
    downloads: DownloadRepository,
) : ViewModel() {
    private val loader = ExploreResults(viewModelScope, catalog, autoRetryAttempts = 2)
    val query = loader.query
    val genre = loader.genre
    val sort = loader.sort
    val results = loader.results
    val loadState = loader.loadState

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun onQuery(q: String) = loader.onQuery(q)
    fun onGenre(g: String?) = loader.onGenre(g)
    fun onSort(s: BrowseSort) = loader.onSort(s)
    fun retry() = loader.retry()
    fun loadMore() = loader.loadMore()

}

@Composable
fun ExploreScreen(onOpen: (String) -> Unit, vm: ExploreViewModel = appViewModel { ExploreViewModel(it.catalog, it.downloads) }) {
    val query by vm.query.collectAsStateWithLifecycle()
    val genre by vm.genre.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Explore")

        SearchField(
            value = query,
            onValueChange = vm::onQuery,
            placeholder = "Search anime…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Filters only apply to browsing; text search ignores them, so hide them while searching
        // rather than show selections that silently do nothing.
        if (query.isBlank()) {
        // Sort chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(BrowseSort.entries, key = { it.name }) { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { vm.onSort(s) },
                    label = { Text(s.label) },
                )
            }
        }

        // Genre chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                FilterChip(
                    selected = genre == null,
                    onClick = { vm.onGenre(null) },
                    label = { Text("All") },
                )
            }
            items(AniListGenres, key = { it }) { g ->
                FilterChip(
                    selected = genre == g,
                    onClick = { vm.onGenre(if (genre == g) null else g) },
                    label = { Text(g) },
                )
            }
        }

        }

        CatalogFeedback(loadState, vm::retry)

        val gridState = rememberLazyGridState()
        // A new query/filter is a new list: start it from the top, not the old (paged) offset.
        LaunchedEffect(query.trim(), genre, sort) { gridState.scrollToItem(0) }
        // Infinite scroll: ask for the next page once the last ~2 rows come into view.
        LaunchedEffect(gridState) {
            snapshotFlow {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 24
            }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) vm.loadMore() }
        }
        // A page that fills less than the screen never scrolls, so re-check after each append.
        LaunchedEffect(results.size) {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (info.totalItemsCount > 0 && last >= info.totalItemsCount - 24) vm.loadMore()
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(results, key = { it.id }, contentType = { "poster" }) {
                PosterCard(it, onClick = { onOpen(it.id) }, downloaded = it.id in downloadedIds)
            }
        }
    }
}
