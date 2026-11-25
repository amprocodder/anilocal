package com.anilocal.app.ui.library

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector
import com.anilocal.app.ui.common.EmptyState
import com.anilocal.app.ui.common.ScreenHeader
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anilocal.app.appViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.MalStatus
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.SearchField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.anilocal.app.ui.common.loadOrNull
import com.anilocal.app.ui.kit.FilterChip
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
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
    private val projection = libraryProjections(library.library, mal.all())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryProjection())

    private val unfiltered: StateFlow<List<AnimeSummary>> =
        projection.combine(filter) { projection, status -> projection.items(status) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<AnimeSummary>> =
        unfiltered.combine(query) { list, q ->
            val text = q.trim()
            if (text.isEmpty()) list else list.filter { it.title.contains(text, ignoreCase = true) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the current chip has anything at all, pre-query — picks the right empty hint. */
    val hasEntries: StateFlow<Boolean> =
        unfiltered.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setFilter(status: MalStatus?) { filter.value = status }

    fun setQuery(q: String) { query.value = q }

    /** Resolve a MAL-only row without making a transient AniList outage require another tap. */
    suspend fun anilistIdForMal(malId: Int): String? {
        repeat(MAL_RESOLVE_ATTEMPTS) { attempt ->
            try {
                return catalog.anilistIdForMal(malId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt + 1 < MAL_RESOLVE_ATTEMPTS) delay(MAL_RESOLVE_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    private companion object {
        const val MAL_RESOLVE_ATTEMPTS = 3
        const val MAL_RESOLVE_DELAY_MS = 500L
    }

}

@Composable
fun LibraryScreen(onOpen: (String) -> Unit, vm: LibraryViewModel = appViewModel { LibraryViewModel(it.library, it.downloads, it.mal, it.catalog) }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val gridItems by vm.items.collectAsStateWithLifecycle()
    val hasEntries by vm.hasEntries.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    var openJob by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("My List")
        SearchField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = "Search your list…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                FilterChip(
                    selected = filter == null,
                    onClick = { vm.setFilter(null) },
                    label = { Text("My List") },
                )
            }
            items(MalStatus.entries, key = { it.name }) { s ->
                FilterChip(
                    selected = filter == s,
                    onClick = { vm.setFilter(s) },
                    label = { Text(s.label) },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                // Only when the query filtered out a non-empty list — a genuinely empty
                // list/category falls through to its actionable onboarding hint instead.
                gridItems.isEmpty() && query.isNotBlank() && hasEntries ->
                    Empty(AppIcons.Outlined.SearchOff, "No matches", "Nothing in your list matches “${query.trim()}”.")

                gridItems.isEmpty() && filter == null ->
                    Empty(
                        AppIcons.Outlined.BookmarkBorder,
                        "Your list is empty",
                        "Save titles from their detail page, or sync your MyAnimeList list in Settings.",
                    )

                gridItems.isEmpty() ->
                    Empty(AppIcons.Outlined.Inbox, "Nothing here yet", "Sync your list from Settings → MyAnimeList Sync.")

                else -> Grid {
                    items(gridItems, key = { it.id }, contentType = { "poster" }) { summary ->
                        PosterCard(
                            summary,
                            onClick = {
                                openJob?.cancel()
                                val malId = summary.idMal
                                if (summary.id.startsWith("mal-") && malId != null) {
                                    // MAL-only entry: resolve its AniList id before opening detail.
                                    openJob = scope.launch {
                                        val id = loadOrNull { vm.anilistIdForMal(malId) }
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
private fun BoxScope.Empty(icon: ImageVector, title: String, message: String) {
    EmptyState(icon, title, message, Modifier.align(Alignment.Center).padding(bottom = 48.dp))
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
