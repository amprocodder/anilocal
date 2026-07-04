package com.anilocal.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.SectionHeader
import com.anilocal.app.ui.common.scoreLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(
    val title: String,
    val items: List<AnimeSummary>,
    /** Ranked shelves overlay 9anime-style position digits on their posters. */
    val ranked: Boolean = false,
)

/** How many trending titles the hero carousel takes for itself. */
private const val HERO_COUNT = 6

private val HeroTopScrim =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val progress: ProgressRepository,
    downloads: DownloadRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows

    // The hero carousel's titles — the head of the Trending loader specifically (NOT whichever row
    // happens to load first: if Trending fails, the hero stays empty rather than hijacking another
    // section's items). The Trending shelf keeps the remainder.
    private val _hero = MutableStateFlow<List<AnimeSummary>>(emptyList())
    val hero: StateFlow<List<AnimeSummary>> = _hero

    val continueWatching: StateFlow<List<ContinueWatching>> =
        progress.continueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Permanently drop an item from the Continue Watching row. */
    fun removeFromContinue(animeId: String) = viewModelScope.launch { progress.remove(animeId) }

    init {
        // Load the home rows; each appears as soon as it returns (ordered).
        data class Section(
            val title: String,
            val loader: suspend () -> List<AnimeSummary>,
            val ranked: Boolean = false,
        )
        val sections = listOf(
            Section("Trending Now", { catalog.trending() }),
            Section("Popular This Season", { catalog.popularThisSeason() }),
            Section("Top Airing", { catalog.topAiring() }),
            Section("All-Time Popular", { catalog.allTimePopular() }, ranked = true),
            Section("Upcoming", { catalog.upcoming() }),
        )
        viewModelScope.launch {
            for ((index, section) in sections.withIndex()) {
                val items = runCatching { section.loader() }.getOrDefault(emptyList())
                var rowItems = items
                if (index == 0 && items.isNotEmpty()) {
                    _hero.value = items.take(HERO_COUNT)
                    rowItems = items.drop(HERO_COUNT)
                }
                if (rowItems.isNotEmpty()) {
                    _rows.value = _rows.value + HomeRow(section.title, rowItems, section.ranked)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onResume: (animeId: String, episodeNumber: Int, positionMs: Long) -> Unit,
    onExplore: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            if (hero.isEmpty()) {
                // Nothing loaded yet (or offline): still show the brand bar in place of the hero.
                HomeTopBar(onExplore, Modifier.statusBarsPadding())
            } else {
                HeroCarousel(items = hero, onOpen = onOpen, onExplore = onExplore)
            }
        }
        if (continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingShelf(
                    items = continueWatching,
                    downloadedIds = downloadedIds,
                    onResume = onResume,
                    onRemove = vm::removeFromContinue,
                )
            }
        }
        items(rows, key = { it.title }) { row ->
            Shelf(
                row.title, row.items, downloadedIds, onOpen,
                onSeeAll = onExplore,
                ranked = row.ranked,
            )
        }
    }
}

/** Brand wordmark + search — overlaid on the hero (or standalone while nothing is loaded). */
@Composable
private fun HomeTopBar(onExplore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append("Onboard")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) { append(".") }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExplore) {
            Icon(Icons.Filled.Search, "Search", tint = Color.White)
        }
    }
}

/**
 * 9anime-style spotlight: an auto-advancing full-bleed poster carousel bleeding under the status
 * bar, with the brand bar on top and a solid info band (rank label, title, meta, white Watch pill,
 * page dots) pinned to the slide bottom.
 */
@Composable
private fun HeroCarousel(
    items: List<AnimeSummary>,
    onOpen: (String) -> Unit,
    onExplore: () -> Unit,
) {
    val pagerState = rememberPagerState { items.size }

    // Auto-advance every 5s, only while RESUMED (no invisible page animations from the background).
    // A user swipe cancels an in-flight animateScrollToPage with a CancellationException — caught so
    // it skips that turn instead of killing the whole loop; the effect's own cancellation still
    // propagates from the next delay().
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(items.size) {
        if (items.size < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(5_000)
                runCatching {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(410.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onOpen(item.id) },
            )
        }
        // Top scrim: darken the art for the brand bar (status-bar legibility).
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(130.dp)
                .background(HeroTopScrim)
        )

        HomeTopBar(onExplore, Modifier.statusBarsPadding())

        val page = pagerState.currentPage.coerceIn(items.indices)
        val current = items[page]
        // The 9anime signature: a solid info band pinned to the slide bottom.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)),
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "#${page + 1} SPOTLIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        current.format,
                        current.episodes?.let { "$it ep" },
                        current.averageScore?.let(::scoreLabel),
                    ).joinToString("  •  ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { onOpen(current.id) },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0B1230),
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Text("Watch now", fontWeight = FontWeight.Bold)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items.indices.forEach { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .size(width = if (active) 10.dp else 4.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.tertiary
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
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
        SectionHeader("Continue Watching")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items, key = { it.anime.id }) { cw ->
                ContinueCard(
                    item = cw,
                    downloaded = cw.anime.id in downloadedIds,
                    onClick = { onResume(cw.anime.id, cw.episodeNumber, cw.positionMs) },
                    onRemove = { onRemove(cw.anime.id) },
                )
            }
        }
    }
}

/** AniLab-style continue card: landscape thumb + play glyph + remove + resume progress. */
@Composable
private fun ContinueCard(
    item: ContinueWatching,
    downloaded: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.width(168.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(94.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() },
        ) {
            AsyncImage(
                model = item.anime.posterUrl,
                contentDescription = item.anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    Icons.Filled.PlayArrow, "Resume",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(22.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onRemove() },
            ) {
                Icon(
                    Icons.Filled.Close, "Remove from Continue Watching",
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp).size(12.dp),
                )
            }
            if (downloaded) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        Icons.Filled.DownloadDone, "Downloaded",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(3.dp).size(12.dp),
                    )
                }
            }
            item.fraction?.let { f ->
                LinearProgressIndicator(
                    progress = { f },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Text(
            item.anime.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Episode ${item.episodeNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Shelf(
    title: String,
    items: List<AnimeSummary>,
    downloadedIds: Set<String>,
    onOpen: (String) -> Unit,
    onSeeAll: () -> Unit,
    ranked: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, onSeeAll = onSeeAll)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                PosterCard(
                    item,
                    onClick = { onOpen(item.id) },
                    downloaded = item.id in downloadedIds,
                    rank = if (ranked) index + 1 else null,
                )
            }
        }
    }
}
