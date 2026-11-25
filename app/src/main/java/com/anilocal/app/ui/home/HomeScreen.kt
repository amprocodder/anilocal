package com.anilocal.app.ui.home

import com.anilocal.app.ui.icons.AppIcons
import com.anilocal.app.ui.common.episodeBadge
import com.anilocal.app.ui.common.StatusBarScrim
import com.anilocal.app.ui.common.scrolledPast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.anilocal.app.R
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.anilocal.app.appViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.ui.image.RemoteImage
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.ui.common.CatalogLoad
import com.anilocal.app.ui.common.CatalogFeedback
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.SectionHeader
import com.anilocal.app.ui.common.scoreLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.anilocal.app.ui.kit.Button
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.LinearProgressIndicator
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

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

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val progress: ProgressRepository,
    downloads: DownloadRepository,
) : ViewModel() {

    private val loader = CatalogLoad(
        viewModelScope,
        HomeCatalog(),
        autoRetryAttempts = 2,
    ) {
        catalog.home().also {
            check(listOf(it.trending, it.popularThisSeason, it.topAiring, it.allTimePopular, it.upcoming)
                .any { row -> row.isNotEmpty() }) { "AniList: home unavailable" }
        }
    }
    val loadState = loader.state
    val hero: StateFlow<List<AnimeSummary>> = loader.value.map { it.trending.take(HERO_COUNT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rows: StateFlow<List<HomeRow>> = loader.value.map { home ->
        listOf(
            HomeRow("Trending Now", home.trending.drop(HERO_COUNT)),
            HomeRow("Popular This Season", home.popularThisSeason),
            HomeRow("Top Airing", home.topAiring),
            HomeRow("All-Time Popular", home.allTimePopular, ranked = true),
            HomeRow("Upcoming", home.upcoming),
        ).filter { it.items.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() = loader.refresh()

    val continueWatching: StateFlow<List<ContinueWatching>> =
        progress.continueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedAnimeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _actionStatus = MutableStateFlow<String?>(null)
    val actionStatus: StateFlow<String?> = _actionStatus

    /** Permanently drop an item from the Continue Watching row. */
    fun removeFromContinue(animeId: String) = viewModelScope.launch {
        _actionStatus.value = null
        if (!removeWithRetry(animeId)) {
            _actionStatus.value = "Couldn't remove this item yet. Try again later."
        }
    }

    private suspend fun removeWithRetry(animeId: String): Boolean {
        repeat(REMOVE_ATTEMPTS) { attempt ->
            try {
                progress.remove(animeId)
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt + 1 < REMOVE_ATTEMPTS) delay(REMOVE_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return false
    }

    private companion object {
        const val REMOVE_ATTEMPTS = 3
        const val REMOVE_RETRY_DELAY_MS = 250L
    }


}

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onResume: (animeId: String, episodeNumber: Int, positionMs: Long) -> Unit,
    onExplore: () -> Unit,
    vm: HomeViewModel = appViewModel { HomeViewModel(it.catalog, it.progress, it.downloads) },
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val actionStatus by vm.actionStatus.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // The hero has its own top scrim; once it scrolls mostly away, shelves would run under the clock.
    val scrolled by remember { derivedStateOf { listState.scrolledPast(thresholdPx = 600) } }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "hero", contentType = "hero") {
            if (hero.isEmpty()) {
                // Nothing loaded yet (or offline): still show the brand bar in place of the hero.
                HomeTopBar(onExplore, Modifier.statusBarsPadding())
            } else {
                HeroCarousel(items = hero, onOpen = onOpen, onExplore = onExplore)
            }
        }
        item(key = "catalog-status", contentType = "status") { CatalogFeedback(loadState, vm::retry) }
        actionStatus?.let { message ->
            item(key = "action-status", contentType = "status") {
                Text(
                    message,
                    color = AppTheme.colors.error,
                    style = AppTheme.type.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching", contentType = "continue-watching") {
                ContinueWatchingShelf(
                    items = continueWatching,
                    downloadedIds = downloadedIds,
                    onResume = onResume,
                    onRemove = vm::removeFromContinue,
                )
            }
        }
        items(rows, key = { it.title }, contentType = { "shelf" }) { row ->
            Shelf(
                row.title, row.items, downloadedIds, onOpen,
                onSeeAll = onExplore,
                ranked = row.ranked,
            )
        }
    }
    StatusBarScrim(visible = scrolled, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** Brand mark, wordmark and search — overlaid on the hero (or standalone while nothing is loaded). */
@Composable
private fun HomeTopBar(onExplore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tugboat mark (the launcher icon's glyph) in the bright accent, which reads over art.
        Icon(ImageVector.vectorResource(R.drawable.ic_logo), null, tint = AppTheme.colors.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("Onboard")
                withStyle(SpanStyle(color = AppTheme.colors.tertiary)) { append(".") }
            },
            style = AppTheme.type.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExplore) {
            Icon(AppIcons.Filled.Search, "Search", tint = Color.White)
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

    BoxWithConstraints(Modifier.fillMaxWidth().height(460.dp)) {
        // A portrait cover cropped into a wide slot gets upscaled several times over (blurry on
        // tablets/landscape); use the wide banner there, and the HD cover on squarer phone slots.
        val wide = maxWidth / maxHeight > 1.6f
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            RemoteImage(
                url = if (wide) item.bannerUrl ?: item.posterHdUrl ?: item.posterUrl
                else item.posterHdUrl ?: item.posterUrl,
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
        val bg = AppTheme.colors.background
        // Cinematic fade: the art melts into the page instead of ending in a hard band, and the
        // spotlight's details sit over the darkest part of the fade.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to bg.copy(alpha = 0.75f),
                        1f to bg,
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "#${page + 1} SPOTLIGHT",
                style = AppTheme.type.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.tertiary,
                letterSpacing = 1.4.sp,
            )
            Text(
                current.title,
                style = AppTheme.type.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            val meta = listOfNotNull(
                current.format,
                // The format already says "Movie"; only series get a count.
                episodeBadge(null, current.episodes).takeUnless { current.format.equals("Movie", true) },
                current.averageScore?.let(::scoreLabel),
            )
            if (meta.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.forEach { HeroPill(it) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onOpen(current.id) },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(AppIcons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch now")
                }
                Spacer(Modifier.weight(1f))
                // Page dots, right-aligned beside the call to action.
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items.indices.forEach { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            Modifier
                                .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (active) AppTheme.colors.tertiary
                                    else Color.White.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Frosted meta chip over hero art. */
@Composable
private fun HeroPill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.14f)) {
        Text(
            text,
            style = AppTheme.type.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
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
            RemoteImage(
                url = item.anime.posterUrl,
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
                    AppIcons.Filled.PlayArrow, "Resume",
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
                    AppIcons.Filled.Close, "Remove from Continue Watching",
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
                        .background(AppTheme.colors.primary),
                ) {
                    Icon(
                        AppIcons.Filled.DownloadDone, "Downloaded",
                        tint = AppTheme.colors.onPrimary,
                        modifier = Modifier.padding(3.dp).size(12.dp),
                    )
                }
            }
            item.fraction?.let { f ->
                LinearProgressIndicator(
                    progress = { f },
                    color = AppTheme.colors.tertiary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Text(
            item.anime.title,
            style = AppTheme.type.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Episode ${item.episodeNumber}",
            style = AppTheme.type.labelSmall,
            color = AppTheme.colors.onSurfaceVariant,
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
            itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "poster" }) { index, item ->
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
