package com.anilocal.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.MalListEntry
import com.anilocal.app.domain.model.MalStatus
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.ui.common.PosterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    downloads: DownloadRepository,
    private val mal: MalRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    /** null = local "My List"; otherwise the selected MAL status. */
    val filter = MutableStateFlow<MalStatus?>(null)

    val localItems: StateFlow<List<AnimeSummary>> =
        library.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val malItems: StateFlow<List<MalListEntry>> =
        filter.flatMapLatest { f -> if (f == null) flowOf(emptyList()) else mal.list(f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setFilter(status: MalStatus?) { filter.value = status }

    suspend fun anilistIdForMal(malId: Int): String? = catalog.anilistIdForMal(malId)
}

@Composable
fun LibraryScreen(onOpen: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val localItems by vm.localItems.collectAsStateWithLifecycle()
    val malItems by vm.malItems.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                FilterChip(selected = filter == null, onClick = { vm.setFilter(null) }, label = { Text("My List") })
            }
            items(MalStatus.entries, key = { it.name }) { s ->
                FilterChip(selected = filter == s, onClick = { vm.setFilter(s) }, label = { Text(s.label) })
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                filter == null && localItems.isEmpty() ->
                    Hint("Your list is empty — add titles from a detail page.")

                filter == null -> Grid {
                    items(localItems, key = { it.id }) {
                        PosterCard(it, onClick = { onOpen(it.id) }, downloaded = it.id in downloadedIds)
                    }
                }

                malItems.isEmpty() ->
                    Hint("Nothing here yet — sync from Settings → MyAnimeList Sync.")

                else -> Grid {
                    items(malItems, key = { it.malId }) { e ->
                        PosterCard(
                            AnimeSummary("mal-${e.malId}", e.title, e.posterUrl, e.malId),
                            onClick = {
                                scope.launch {
                                    val id = vm.anilistIdForMal(e.malId)
                                    if (id != null) onOpen(id)
                                    else Toast.makeText(context, "\"${e.title}\" not found on AniList", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
    )
}

@Composable
private fun Grid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
