package com.anilocal.app.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
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

}

@Composable
fun ExploreScreen(onOpen: (String) -> Unit, vm: ExploreViewModel = hiltViewModel()) {
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
        Text(
            "Explore",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SearchField(
            value = query,
            onValueChange = vm::onQuery,
            placeholder = "Search anime…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White,
        )
        val chipShape = RoundedCornerShape(100.dp)

        // Sort chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(BrowseSort.entries, key = { it.name }) { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { vm.onSort(s) },
                    label = { Text(s.label) },
                    shape = chipShape,
                    colors = chipColors,
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
                    shape = chipShape,
                    colors = chipColors,
                )
            }
            items(AniListGenres, key = { it }) { g ->
                FilterChip(
                    selected = genre == g,
                    onClick = { vm.onGenre(if (genre == g) null else g) },
                    label = { Text(g) },
                    shape = chipShape,
                    colors = chipColors,
                )
            }
        }

        CatalogFeedback(loadState, vm::retry)

        LazyVerticalGrid(
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
