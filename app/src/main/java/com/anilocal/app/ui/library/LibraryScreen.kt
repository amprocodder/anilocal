package com.anilocal.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.anilocal.app.ui.common.SearchField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    /** null = "My List" (local additions + ALL MAL entries, uncategorised); else a MAL status. */
    val filter = MutableStateFlow<MalStatus?>(null)

    /** Case-insensitive title filter applied on top of the current chip filter. */
    val query = MutableStateFlow("")

    /**
     * Grid items for the current chip filter, unified to [AnimeSummary], pre-query. "My List"
     * merges the local library with the entire MAL mirror — deduped by MAL id, with the local
     * entry winning so it opens directly. A status chip shows just that MAL category.
     */
    private val unfiltered: StateFlow<List<AnimeSummary>> =
        filter.flatMapLatest { f ->
            if (f == null) {
                combine(library.library, mal.all()) { local, malAll ->
                    val localMalIds = local.mapNotNull { it.idMal }.toSet()
                    local + malAll.filter { it.malId !in localMalIds }.map { it.toSummary() }
                }
            } else {
                mal.list(f).map { entries -> entries.map { it.toSummary() } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<AnimeSummary>> =
        unfiltered.combine(query) { list, q ->
            val t = q.trim()
            if (t.isEmpty()) list else list.filter { it.title.contains(t, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the current chip has anything at all, pre-query — picks the right empty hint. */
    val hasEntries: StateFlow<Boolean> =
        unfiltered.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setFilter(status: MalStatus?) { filter.value = status }

    fun setQuery(q: String) { query.value = q }

    suspend fun anilistIdForMal(malId: Int): String? = catalog.anilistIdForMal(malId)

    private fun MalListEntry.toSummary() = AnimeSummary("mal-$malId", title, posterUrl, malId)
}

@Composable
fun LibraryScreen(onOpen: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val gridItems by vm.items.collectAsStateWithLifecycle()
    val hasEntries by vm.hasEntries.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "My List",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SearchField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = "Search your list…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val chipShape = RoundedCornerShape(100.dp)
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                FilterChip(
                    selected = filter == null,
                    onClick = { vm.setFilter(null) },
                    label = { Text("My List") },
                    shape = chipShape,
                    colors = chipColors,
                )
            }
            items(MalStatus.entries, key = { it.name }) { s ->
                FilterChip(
                    selected = filter == s,
                    onClick = { vm.setFilter(s) },
                    label = { Text(s.label) },
                    shape = chipShape,
                    colors = chipColors,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                // Only when the query filtered out a non-empty list — a genuinely empty
                // list/category falls through to its actionable onboarding hint instead.
                gridItems.isEmpty() && query.isNotBlank() && hasEntries ->
                    Hint("No titles match “${query.trim()}”.")

                gridItems.isEmpty() && filter == null ->
                    Hint("Your list is empty — add titles from a detail page, or sync your MAL list in Settings.")

                gridItems.isEmpty() ->
                    Hint("Nothing here yet — sync from Settings → MyAnimeList Sync.")

                else -> Grid {
                    items(gridItems, key = { it.id }) { summary ->
                        PosterCard(
                            summary,
                            onClick = {
                                val malId = summary.idMal
                                if (summary.id.startsWith("mal-") && malId != null) {
                                    // MAL-only entry: resolve its AniList id before opening detail.
                                    scope.launch {
                                        val id = vm.anilistIdForMal(malId)
                                        if (id != null) onOpen(id)
                                        else Toast.makeText(context, "\"${summary.title}\" not found on AniList", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    onOpen(summary.id)
                                }
                            },
                            downloaded = summary.id in downloadedIds,
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
