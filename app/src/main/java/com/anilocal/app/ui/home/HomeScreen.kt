package com.anilocal.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(val title: String, val items: List<AnimeSummary>)

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
        // Load the AniLab-style rows; each appears as soon as it returns (ordered).
        val sections: List<Pair<String, suspend () -> List<AnimeSummary>>> = listOf(
            "Trending Now" to { catalog.trending() },
            "Popular This Season" to { catalog.popularThisSeason() },
            "Top Airing" to { catalog.topAiring() },
            "All-Time Popular" to { catalog.allTimePopular() },
            "Upcoming" to { catalog.upcoming() },
        )
        viewModelScope.launch {
            for ((index, section) in sections.withIndex()) {
                val (title, loader) = section
                val items = runCatching { loader() }.getOrDefault(emptyList())
                var rowItems = items
                if (index == 0 && items.isNotEmpty()) {
                    _hero.value = items.take(HERO_COUNT)
                    rowItems = items.drop(HERO_COUNT)
                }
                if (rowItems.isNotEmpty()) _rows.value = _rows.value + HomeRow(title, rowItems)
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
            Shelf(row.title, row.items, downloadedIds, onOpen, onSeeAll = onExplore)
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
 * AniLab-style hero: an auto-advancing full-bleed poster carousel bleeding under the status bar,
 * with gradient scrims fading the art into the page background, the brand bar on top, and the
 * current title + Watch pill at the bottom.
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

    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onOpen(item.id) },
            )
        }
        // Scrims: darken the top for the brand bar, fade the bottom into the page background.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(130.dp)
                .background(HeroTopScrim)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
        )

        HomeTopBar(onExplore, Modifier.statusBarsPadding())

        val current = items[pagerState.currentPage.coerceIn(items.indices)]
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                current.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = { onOpen(current.id) },
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                Text("  Watch now", fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.tertiary
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
        Text(
            "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
                .clip(RoundedCornerShape(10.dp))
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable { onSeeAll() },
            )
        }
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
