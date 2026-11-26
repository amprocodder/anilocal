package com.anilocal.app.ui.details

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.aspectRatio
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.ui.common.scrolledPast
import kotlinx.coroutines.flow.combine
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import com.anilocal.app.appViewModel
import kotlinx.coroutines.flow.filterNotNull
import com.anilocal.app.domain.repo.ProgressRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.ui.image.RemoteImage
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.model.DownloadState
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.TrackLanguages
import com.anilocal.app.domain.model.pickDownloadVariant
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.ui.common.CatalogLoad
import com.anilocal.app.ui.common.CatalogFeedback
import com.anilocal.app.ui.common.loadOrNull
import com.anilocal.app.ui.common.PosterCard
import com.anilocal.app.ui.common.SectionHeader
import com.anilocal.app.ui.common.scoreLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import com.anilocal.app.ui.kit.AlertDialog
import com.anilocal.app.ui.kit.Button
import com.anilocal.app.ui.kit.CircularProgressIndicator
import com.anilocal.app.ui.kit.FilterChip
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.RadioButton
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.kit.TextButton
import com.anilocal.app.ui.kit.TonalIconButton
import com.anilocal.app.ui.theme.AppTheme

/** A download awaiting a quality choice (when more than one variant is available). */
data class PendingDownload(
    val episode: Episode,
    val markers: List<SkipMarker>,
    val options: List<VideoStream>,
)

/** Progress of a one-press season download: how many episodes have been queued (or failed) so far. */
data class SeasonDownload(
    val queued: Int,
    val failed: Int,
    val total: Int,          // 0 while the pass is still working out which episodes it needs
    val finished: Boolean = false,
)

class DetailsViewModel(
    private val animeId: String,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    mal: MalRepository,
    private val progress: ProgressRepository,
    // Season downloads keep queuing after the user leaves the screen (viewModelScope would cancel).
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val loader = CatalogLoad<AnimeDetail?>(
        viewModelScope,
        null,
        autoRetryAttempts = 2,
    ) { catalog.detail(animeId) }
    val detail = loader.value
    val loadState = loader.state
    fun retry() = loader.refresh()

    init {
        // A title the user is partway through: the episode they'll most likely play next is the one
        // they were watching, so start resolving it now — Play is then near-instant. Skipped when it's
        // downloaded (that plays from disk anyway).
        viewModelScope.launch {
            val resume = progress.continueWatching.first().firstOrNull { it.anime.id == animeId } ?: return@launch
            if (downloads.getOffline(animeId, resume.episodeNumber) != null) return@launch
            val d = detail.filterNotNull().first()
            if (d.episodes.any { it.number == resume.episodeNumber }) streams.prefetch(d, resume.episodeNumber)
        }
    }

    /** Where the user left this title (Continue Watching's entry), or null if they haven't started it. */
    val resumePoint: StateFlow<ContinueWatching?> =
        progress.continueWatching
            .map { list -> list.firstOrNull { it.anime.id == animeId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val saved: StateFlow<Boolean> =
        library.isSaved(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when the title is on the user's mirrored MAL list (so it already shows in My List). */
    val onMalList: StateFlow<Boolean> =
        combine(detail, mal.all()) { d, entries -> d?.idMal != null && entries.any { it.malId == d.idMal } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedEpisodes: StateFlow<Set<Int>> =
        downloads.downloadedEpisodes(animeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Episodes present in the downloads list in any NON-FAILED state — drives the season button's
     * done state. Failed rows count as missing so the done check never lies and a season re-press
     * can retry them.
     */
    val episodesInDownloads: StateFlow<Set<Int>> =
        downloads.downloads
            .map { list ->
                list.filter { it.animeId == animeId && it.state != DownloadState.FAILED }
                    .map { it.episodeNumber }
                    .toSet()
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val defaultQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)
    // The quality picker pre-selects in the preferred audio, as playback and season downloads do.
    val audioLanguage: StateFlow<String> =
        settings.audioLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackLanguages.JAPANESE)

    private val requests = DownloadRequests(viewModelScope, { detail.value }, streams, skip, downloads)
    val pending = requests.pending
    val busyEpisodes = requests.busyEpisodes

    // Shared per-title pass state (see companion) so a re-entered Details screen sees the live pass.
    private val seasonState = seasonPasses.getOrPut(animeId) { MutableStateFlow(null) }
    val seasonDownload: StateFlow<SeasonDownload?> = seasonState
    private val savedWrites = Mutex()

    fun toggleSaved() = viewModelScope.launch {
        savedWrites.withLock {
            detail.value?.let { anime ->
                val summary = AnimeSummary(
                    anime.id,
                    anime.title,
                    anime.posterUrl,
                    anime.idMal,
                    anime.format,
                    anime.episodes.size,
                    anime.averageScore,
                )
                repeat(SAVED_WRITE_ATTEMPTS) { attempt ->
                    try {
                        library.toggle(summary)
                        return@withLock
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (attempt + 1 < SAVED_WRITE_ATTEMPTS) {
                            delay(SAVED_WRITE_DELAY_MS * (attempt + 1))
                        }
                    }
                }
            }
        }
    }

    fun download(episode: Episode) = requests.download(episode)
    fun chooseQuality(request: PendingDownload, stream: VideoStream) = requests.chooseQuality(request, stream)
    fun dismissPicker(request: PendingDownload) = requests.dismissPicker(request)

    /**
     * One press → the whole season: resolves and enqueues every episode not already in the downloads
     * list (failed rows count as missing, so a re-press retries them), sequentially — polite to the
     * source — at the default download quality. Failed episodes are counted but don't stop the rest.
     * One pass per title app-wide: the pass survives this screen closing, a re-entered screen picks
     * up its live progress, and pressing the button mid-pass cancels it.
     */
    fun downloadSeason() {
        val d = detail.value ?: return
        if (seasonState.value?.finished == false) return   // a queue pass is already running
        seasonState.value = SeasonDownload(0, 0, 0)        // claim synchronously — no double-tap window
        seasonJobs[animeId] = appScope.launch {
            try {
                val quality = loadOrNull { settings.downloadQuality.first() } ?: DownloadQuality.AUTO
                val audio = loadOrNull { settings.audioLanguage.first() } ?: TrackLanguages.JAPANESE
                val toQueue = d.episodes.filter { it.number !in episodesQueuedNow() }
                if (toQueue.isEmpty()) {
                    seasonState.value = null
                    return@launch
                }
                seasonState.value = SeasonDownload(0, 0, toQueue.size)
                var queued = 0
                var failed = 0
                suspend fun tryQueue(ep: Episode): Boolean {
                    val ok = runCatching {
                        // Re-check against live state: a single-episode download may have queued it mid-pass.
                        if (ep.number !in episodesQueuedNow()) {
                            coroutineScope {
                                val markers = async { loadOrNull { skip.markers(d.idMal, ep.number, 0) }.orEmpty() }
                                val stream = checkNotNull(pickDownloadVariant(streams.resolveStreams(d, ep.number), quality.maxHeight, audio))
                                downloads.enqueue(d, ep, stream, markers.await())
                            }
                        }
                    }.isSuccess
                    ensureActive()   // runCatching swallows cancellation — don't count it as a failure
                    return ok
                }
                val resolveFailed = mutableListOf<Episode>()
                for (ep in toQueue) {
                    if (tryQueue(ep)) queued++ else { failed++; resolveFailed += ep }
                    seasonState.value = SeasonDownload(queued, failed, toQueue.size)
                }
                // Second chance for resolve-time failures (rate-limit/CF hiccups mid-pass): one
                // more polite sequential attempt each. Episodes that fail LATER, at download time,
                // are handled separately by the repository's auto-retry — this pass only covers
                // the ones that never made it into the downloads list at all.
                for (ep in resolveFailed) {
                    if (tryQueue(ep)) {
                        queued++; failed--
                        seasonState.value = SeasonDownload(queued, failed, toQueue.size)
                    }
                }
                seasonState.value = SeasonDownload(queued, failed, toQueue.size, finished = true)
            } finally {
                seasonJobs.remove(animeId, kotlinx.coroutines.currentCoroutineContext()[Job])
            }
        }
    }

    /** Stops a running pass; episodes already handed to the download manager keep downloading. */
    fun cancelSeasonDownload() {
        seasonJobs.remove(animeId)?.cancel()
        seasonState.value = null
    }

    /** Episode numbers of this title currently in the downloads list in any non-failed state. */
    private suspend fun episodesQueuedNow(): Set<Int> =
        loadOrNull { downloads.downloads.first() }.orEmpty()
            .filter { it.animeId == animeId && it.state != DownloadState.FAILED }
            .map { it.episodeNumber }
            .toSet()

    companion object {
        private const val SAVED_WRITE_ATTEMPTS = 3
        private const val SAVED_WRITE_DELAY_MS = 250L
        // Keyed by animeId and static: a pass runs on appScope and outlives any single Details
        // ViewModel, so in-flight tracking (and its progress) must too — otherwise leaving and
        // re-entering the screen could start a second concurrent pass for the same title.
        private val seasonPasses = ConcurrentHashMap<String, MutableStateFlow<SeasonDownload?>>()
        private val seasonJobs = ConcurrentHashMap<String, Job>()

        @VisibleForTesting
        internal fun resetSeasonDownloadState() {
            seasonJobs.values.forEach { it.cancel() }
            seasonJobs.clear()
            seasonPasses.clear()
        }
    }
}

private const val EPISODE_RANGE_SIZE = 100
private val EPISODE_CARD_MIN_WIDTH = 180.dp
/** Width AniList serves banner images at. */
private const val BANNER_NATIVE_WIDTH_PX = 1900
private val EPISODE_GAP = 12.dp
private val ACTIONS_MAX_WIDTH = 640.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    animeId: String,
    onPlay: (animeId: String, episodeNumber: Int) -> Unit,
    onResume: (animeId: String, episodeNumber: Int, positionMs: Long) -> Unit,
    onOpenDetail: (animeId: String) -> Unit,
    onBack: () -> Unit,
    vm: DetailsViewModel = appViewModel { DetailsViewModel(animeId, it.catalog, it.library, it.streams, it.skip, it.downloads, it.settings, it.mal, it.progress, it.appScope) },
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val onMalList by vm.onMalList.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedEpisodes.collectAsStateWithLifecycle()
    val inDownloads by vm.episodesInDownloads.collectAsStateWithLifecycle()
    val busyEpisodes by vm.busyEpisodes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val seasonDownload by vm.seasonDownload.collectAsStateWithLifecycle()
    val defaultQuality by vm.defaultQuality.collectAsStateWithLifecycle()
    val audioLanguage by vm.audioLanguage.collectAsStateWithLifecycle()
    val resumePoint by vm.resumePoint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val d = detail

    val mainState = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (d != null) {
            // Episode cards in as many columns as fit (2 on a phone, ~8 on a landscape tablet).
            val perRow = ((maxWidth - 32.dp + EPISODE_GAP) / (EPISODE_CARD_MIN_WIDTH + EPISODE_GAP))
                .toInt().coerceAtLeast(1)
            val showRanges = d.episodes.size > EPISODE_RANGE_SIZE
            var rangeIndex by remember(d.id, d.episodes.size) { mutableStateOf(0) }
            val rangeStart = if (showRanges) rangeIndex * EPISODE_RANGE_SIZE else 0
            val episodeRows = remember(d, rangeStart, perRow) {
                val visible =
                    if (showRanges) {
                        d.episodes.subList(rangeStart, minOf(rangeStart + EPISODE_RANGE_SIZE, d.episodes.size))
                    } else {
                        d.episodes
                    }
                visible.chunked(perRow)
            }
            // A movie is one long "episode": "129 min", not "129 min/ep".
            val isMovie = d.format.equals("Movie", ignoreCase = true)
            val infoLine = listOfNotNull(
                d.studio,
                d.duration?.let { if (isMovie) "$it min" else "$it min/ep" },
            ).joinToString("  •  ")

            val overview: LazyListScope.() -> Unit = {
                item(key = "hero", contentType = "hero") { DetailHero(d) }
                item(key = "catalog-status", contentType = "status") { CatalogFeedback(loadState, vm::retry) }
                if (d.genres.isNotEmpty()) {
                    item(key = "genres") {
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            d.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, AppTheme.colors.outlineVariant),
                                ) {
                                    Text(
                                        genre,
                                        style = AppTheme.type.labelSmall,
                                        color = AppTheme.colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "actions") {
                    Row(
                        // Keep the action row a sensible width on tablets instead of a 2000px pill.
                        Modifier.padding(horizontal = 16.dp).widthIn(max = ACTIONS_MAX_WIDTH),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Only released episodes are listed, so none means nothing is out yet:
                        // there's nothing to play or download, only to save for later.
                        val released = d.episodes.isNotEmpty()
                        // Partway through the title: the button picks up where Continue Watching
                        // would (that episode is already being resolved in the background).
                        val resumeAt = resumePoint?.takeIf { r -> d.episodes.any { it.number == r.episodeNumber } }
                        Button(
                            onClick = {
                                if (resumeAt != null) {
                                    onResume(d.id, resumeAt.episodeNumber, resumeAt.positionMs)
                                } else {
                                    d.episodes.firstOrNull()?.let { onPlay(d.id, it.number) }
                                }
                            },
                            enabled = released,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            if (released) {
                                Icon(AppIcons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (resumeAt != null) "Resume E${resumeAt.episodeNumber} · ${clockTime(resumeAt.positionMs)}"
                                    else "Watch",
                                    maxLines = 1,
                                )
                            } else {
                                Text("Not released yet")
                            }
                        }
                        if (released) {
                            SeasonDownloadButton(
                                state = seasonDownload,
                                allInDownloads = d.episodes.all { it.number in inDownloads },
                                onDownload = vm::downloadSeason,
                                onCancel = vm::cancelSeasonDownload,
                            )
                        }
                        // In My List either locally or through the MAL mirror. A MAL-only title is
                        // managed on MyAnimeList, so the tap explains that instead of adding a
                        // duplicate local entry the user then can't visibly remove.
                        val inList = saved || onMalList
                        TonalIconButton(
                            onClick = {
                                if (!saved && onMalList) {
                                    Toast.makeText(context, "Already in My List from your MAL list", Toast.LENGTH_SHORT).show()
                                } else {
                                    vm.toggleSaved()
                                }
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                if (inList) AppIcons.Filled.BookmarkAdded else AppIcons.Outlined.BookmarkAdd,
                                when {
                                    saved -> "Remove from My List"
                                    onMalList -> "In My List (from MAL)"
                                    else -> "Add to My List"
                                },
                                tint = if (inList) AppTheme.colors.tertiary
                                else AppTheme.colors.onSurface,
                            )
                        }
                    }
                }
                if (d.synopsis.isNotBlank()) {
                    item(key = "synopsis") { ExpandableSynopsis(d.synopsis) }
                }
                if (infoLine.isNotEmpty()) {
                    item(key = "info") {
                        Text(
                            infoLine,
                            style = AppTheme.type.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            val episodesAndRelated: LazyListScope.() -> Unit = {
                if (d.episodes.isNotEmpty()) item(key = "episodes-header") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader("Episodes", Modifier.weight(1f), horizontalPadding = 0.dp)
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = AppTheme.colors.surfaceContainerHighest,
                            ) {
                                Text(
                                    "${d.episodes.size}",
                                    style = AppTheme.type.labelMedium,
                                    color = AppTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                        val sd = seasonDownload
                        Text(
                            when {
                                sd != null && !sd.finished && sd.total == 0 ->
                                    "Preparing season download…"
                                sd != null && !sd.finished ->
                                    "Queuing season… ${sd.queued + sd.failed}/${sd.total} — press again to cancel"
                                sd != null && sd.failed > 0 ->
                                    "Season queued — ${sd.failed} episode${if (sd.failed == 1) "" else "s"} failed, press to retry"
                                else -> "Tap an episode to play, or its download icon to save it"
                            },
                            style = AppTheme.type.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                if (showRanges) {
                    item(key = "ep-ranges") {
                        val rangeCount = (d.episodes.size + EPISODE_RANGE_SIZE - 1) / EPISODE_RANGE_SIZE
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(rangeCount) { i ->
                                val start = i * EPISODE_RANGE_SIZE + 1
                                val end = minOf((i + 1) * EPISODE_RANGE_SIZE, d.episodes.size)
                                FilterChip(
                                    selected = i == rangeIndex,
                                    onClick = { rangeIndex = i },
                                    label = { Text("$start–$end") },
                                )
                            }
                        }
                    }
                }
                items(episodeRows.size, key = { "eps-" + episodeRows[it].first().id }, contentType = { "episodes" }) { rowIndex ->
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(EPISODE_GAP),
                    ) {
                        episodeRows[rowIndex].forEach { ep ->
                            EpisodeCard(
                                episode = ep,
                                fallbackArt = d.bannerUrl ?: d.posterHdUrl ?: d.posterUrl,
                                downloaded = ep.number in downloaded,
                                queued = ep.number in inDownloads,
                                busy = ep.number in busyEpisodes,
                                onPlay = { onPlay(d.id, ep.number) },
                                onDownload = { vm.download(ep) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(perRow - episodeRows[rowIndex].size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // Franchise neighbors (prequels/sequels/seasons/movies), already in watch-order.
                if (d.related.isNotEmpty()) {
                    item(key = "related-header") {
                        SectionHeader("Related", Modifier.padding(top = 8.dp))
                    }
                    item(key = "related-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(d.related, key = { it.anime.id }) { rel ->
                                PosterCard(
                                    item = rel.anime,
                                    badge = rel.relation,
                                    onClick = { onOpenDetail(rel.anime.id) },
                                    modifier = Modifier.testTag("related-${rel.anime.id}"),
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                state = mainState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                overview()
                episodesAndRelated()
                item { Box(Modifier.navigationBarsPadding().height(8.dp)) }
            }
        }

        if (d == null) {
            CatalogFeedback(loadState, vm::retry, Modifier.align(Alignment.Center))
        }

        // Once the banner scrolls away, a solid top bar (back + title) fades in behind the back
        // button, so it never floats over the Watch button or the episode grid.
        val scrolled by remember { derivedStateOf { mainState.scrolledPast(thresholdPx = 420) } }
        val barAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "detailsBar")
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = barAlpha }
                .background(AppTheme.colors.background.copy(alpha = 0.96f))
                .statusBarsPadding()
                .height(56.dp),
        ) {
            Text(
                d?.title.orEmpty(),
                style = AppTheme.type.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 64.dp, end = 16.dp),
            )
        }

        // Back button: a dark disc over the header art, melting into the top bar once scrolled
        // (also the only chrome while loading).
        Box(
            Modifier
                .statusBarsPadding()
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f * (1f - barAlpha))),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    AppIcons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = if (barAlpha > 0.5f) AppTheme.colors.onSurface else Color.White,
                )
            }
        }
    }

    pending?.let { p ->
        val preselected = remember(p, defaultQuality, audioLanguage) {
            pickDownloadVariant(p.options, defaultQuality.maxHeight, audioLanguage)
        }
        AlertDialog(
            onDismissRequest = { vm.dismissPicker(p) },
            title = { Text("Episode ${p.episode.number} · Download quality") },
            text = {
                Column {
                    p.options.forEach { stream ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.chooseQuality(p, stream) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = stream == preselected, onClick = { vm.chooseQuality(p, stream) })
                            Text(
                                stream.quality ?: stream.height?.let { "${it}p" } ?: "Default",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.dismissPicker(p) }) { Text("Cancel") } },
        )
    }
}

/**
 * One-press "download the whole season" button. While a queue pass runs it shows progress and a
 * press cancels; once every episode is in the downloads list (none failed) it flips to a tinted
 * done check.
 */
@Composable
private fun SeasonDownloadButton(
    state: SeasonDownload?,
    allInDownloads: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val queuing = state != null && !state.finished
    TonalIconButton(
        onClick = if (queuing) onCancel else onDownload,
        modifier = Modifier.size(44.dp).testTag("download-season"),
    ) {
        when {
            queuing -> {
                val s = state!!
                val done = s.queued + s.failed
                Text(
                    when {
                        s.total == 0 -> "…"
                        // Three-digit seasons don't fit in a 44dp circle — fall back to percent.
                        s.total > 99 -> "${done * 100 / s.total}%"
                        else -> "$done/${s.total}"
                    },
                    style = AppTheme.type.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.semantics {
                        contentDescription = "Cancel season download, $done of ${s.total} queued"
                    },
                )
            }
            allInDownloads -> Icon(
                AppIcons.Filled.DownloadDone,
                "Season downloaded",
                tint = AppTheme.colors.tertiary,
            )
            else -> Icon(
                AppIcons.Filled.Download,
                "Download season",
                tint = AppTheme.colors.onSurface,
            )
        }
    }
}

/** 9anime-style hero: edge-to-edge banner with a sharp overlapping poster and meta pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(d: AnimeDetail) {
    val bg = AppTheme.colors.background
    Box(Modifier.fillMaxWidth().height(252.dp)) {
        BoxWithConstraints(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(210.dp)) {
            val art = d.bannerUrl ?: d.posterHdUrl ?: d.posterUrl
            // AniList banners are ~1900px wide; stretched across a tablet they're upscaled and soft.
            // Wider than that, draw the banner at its native width (feathered at the edges) over a
            // blurred, dimmed copy that fills the rest — sharp art, still edge to edge.
            val nativeWidth = with(LocalDensity.current) { BANNER_NATIVE_WIDTH_PX.toDp() }
            if (d.bannerUrl != null && maxWidth > nativeWidth) {
                RemoteImage(
                    url = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(32.dp),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                RemoteImage(
                    url = art,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(nativeWidth)
                        .fillMaxHeight()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.08f to Color.Black,
                                    0.92f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
            } else {
                RemoteImage(
                    url = art,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
            )
        }
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
            RemoteImage(
                url = d.posterUrl,
                contentDescription = null,   // the banner already carries d.title
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 148.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(
                Modifier.align(Alignment.Bottom).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column {
                    Text(
                        d.title,
                        style = AppTheme.type.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // English is the primary name app-wide; the romaji is shown only here, small.
                    d.romajiTitle?.let {
                        Text(
                            it,
                            style = AppTheme.type.labelSmall,
                            color = AppTheme.colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    d.status?.let { MetaPill(it, tinted = true) }
                    d.format?.let { MetaPill(it) }
                    d.seasonYear?.let { MetaPill(it.toString()) }
                    d.averageScore?.let { MetaPill(scoreLabel(it)) }
                    if (d.episodes.isNotEmpty()) MetaPill("${d.episodes.size} eps")
                }
            }
        }
    }
}

@Composable
internal fun MetaPill(text: String, tinted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (tinted) AppTheme.colors.primaryContainer
        else AppTheme.colors.surfaceContainerHighest,
        contentColor = if (tinted) AppTheme.colors.onPrimaryContainer
        else AppTheme.colors.onSurfaceVariant,
    ) {
        Text(
            text,
            style = AppTheme.type.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun ExpandableSynopsis(synopsis: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        synopsis,
        style = AppTheme.type.bodyMedium,
        color = AppTheme.colors.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .clickable { expanded = !expanded },
    )
}

/**
 * Episode card: a 16:9 thumbnail (AniList's per-episode art, or the title's banner dimmed under a
 * big number when there's none) with "Episode N" and the episode's name below. Tapping the card
 * plays it; tapping the download icon saves it. The icon reflects resolving/queued state and a
 * check badge marks a completed download.
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    fallbackArt: String?,
    downloaded: Boolean,
    queued: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = episode.title?.takeIf { it.isNotBlank() && it != "Episode ${episode.number}" }
    val thumb = episode.thumbnailUrl
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Play", onClick = onPlay)
            .testTag("ep-${episode.number}")
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Episode ${episode.number}")
                    name?.let { append(", $it") }
                    if (downloaded) append(", downloaded")
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppTheme.colors.primaryContainer, AppTheme.colors.surfaceContainerHighest),
                    ),
                ),
        ) {
            // No per-episode art: a midnight gradient tile over a faint wash of the series art, led
            // by the number (the same full-strength banner on every card read as a wall of clones).
            RemoteImage(
                url = thumb ?: fallbackArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (thumb == null) 0.18f else 1f,
                modifier = Modifier.fillMaxSize(),
            )
            if (thumb == null) {
                Text(
                    "${episode.number}",
                    style = AppTheme.type.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Text(
                    "${episode.number}",
                    style = AppTheme.type.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (downloaded) {
                Icon(
                    AppIcons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = AppTheme.colors.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(AppTheme.colors.primary, CircleShape)
                        .padding(3.dp)
                        .size(14.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Episode ${episode.number}",
                    style = AppTheme.type.labelLarge,
                    color = AppTheme.colors.onSurface,
                    maxLines = 1,
                )
                if (name != null) {
                    Text(
                        name,
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !busy && !queued, onClickLabel = "Download", onClick = onDownload)
                    .semantics {
                        contentDescription = when {
                            downloaded -> "Episode ${episode.number} downloaded"
                            queued -> "Episode ${episode.number} in downloads"
                            else -> "Download episode ${episode.number}"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val tint = AppTheme.colors.onSurfaceVariant
                when {
                    busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = tint)
                    queued -> Icon(AppIcons.Filled.DownloadDone, null, Modifier.size(18.dp), tint = AppTheme.colors.tertiary)
                    else -> Icon(AppIcons.Filled.Download, null, Modifier.size(18.dp), tint = tint)
                }
            }
        }
    }
}

/** "14:20" / "1:02:05" — a resume position as the player's clock shows it. */
private fun clockTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
