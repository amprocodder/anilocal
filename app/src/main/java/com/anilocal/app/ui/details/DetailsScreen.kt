package com.anilocal.app.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.ui.common.scoreLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

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
    val total: Int,
    val finished: Boolean = false,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    // Season downloads keep queuing after the user leaves the screen (viewModelScope would cancel).
    @Named("appScope") private val appScope: CoroutineScope,
) : ViewModel() {
    private val animeId: String = checkNotNull(savedState["animeId"])
    private val _detail = MutableStateFlow<AnimeDetail?>(null)
    val detail: StateFlow<AnimeDetail?> = _detail

    val saved: StateFlow<Boolean> =
        library.isSaved(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedEpisodes: StateFlow<Set<Int>> =
        downloads.downloadedEpisodes(animeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Episodes present in the downloads list in ANY state — drives the season button's done state. */
    val episodesInDownloads: StateFlow<Set<Int>> =
        downloads.downloads
            .map { list -> list.filter { it.animeId == animeId }.map { it.episodeNumber }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val defaultQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)

    private val _pending = MutableStateFlow<PendingDownload?>(null)
    val pending: StateFlow<PendingDownload?> = _pending

    private val _seasonDownload = MutableStateFlow<SeasonDownload?>(null)
    val seasonDownload: StateFlow<SeasonDownload?> = _seasonDownload

    init { viewModelScope.launch { _detail.value = runCatching { catalog.detail(animeId) }.getOrNull() } }

    fun toggleSaved() = viewModelScope.launch {
        _detail.value?.let { library.toggle(AnimeSummary(it.id, it.title, it.posterUrl, it.idMal)) }
    }

    fun download(episode: Episode) = viewModelScope.launch {
        val d = _detail.value ?: return@launch
        val options = runCatching { streams.resolveStreams(d.title, episode.number) }.getOrDefault(emptyList())
        if (options.isEmpty()) return@launch
        val markers = runCatching { skip.markers(d.idMal, episode.number, 0) }.getOrDefault(emptyList())
        if (options.size == 1) {
            downloads.enqueue(d, episode, options.first(), markers)
        } else {
            _pending.value = PendingDownload(episode, markers, options)   // show the picker
        }
    }

    fun chooseQuality(stream: VideoStream) = viewModelScope.launch {
        val d = _detail.value ?: return@launch
        val p = _pending.value ?: return@launch
        downloads.enqueue(d, p.episode, stream, p.markers)
        _pending.value = null
    }

    fun dismissPicker() { _pending.value = null }

    /**
     * One press → the whole season: resolves and enqueues every episode not already in the downloads
     * list, sequentially (polite to the source), picking the variant that matches the default download
     * quality. Failed episodes are counted but don't stop the rest; a re-press after it finishes
     * retries just the episodes that are still missing.
     */
    fun downloadSeason() {
        val d = _detail.value ?: return
        if (_seasonDownload.value?.finished == false) return   // a queue pass is already running
        appScope.launch {
            val existing = runCatching { downloads.downloads.first() }.getOrDefault(emptyList())
                .filter { it.animeId == d.id }
                .map { it.episodeNumber }
                .toSet()
            val toQueue = d.episodes.filter { it.number !in existing }
            if (toQueue.isEmpty()) return@launch
            _seasonDownload.value = SeasonDownload(0, 0, toQueue.size)
            val quality = runCatching { settings.downloadQuality.first() }.getOrDefault(DownloadQuality.AUTO)
            var queued = 0
            var failed = 0
            for (ep in toQueue) {
                val ok = runCatching {
                    val stream = checkNotNull(pickForQuality(streams.resolveStreams(d.title, ep.number), quality))
                    val markers = runCatching { skip.markers(d.idMal, ep.number, 0) }.getOrDefault(emptyList())
                    downloads.enqueue(d, ep, stream, markers)
                }.isSuccess
                if (ok) queued++ else failed++
                _seasonDownload.value = SeasonDownload(queued, failed, toQueue.size)
            }
            _seasonDownload.value = SeasonDownload(queued, failed, toQueue.size, finished = true)
        }
    }
}

/** The variant the picker pre-selects, given the user's default-quality preference. */
private fun pickForQuality(options: List<VideoStream>, quality: DownloadQuality): VideoStream? {
    val maxH = quality.maxHeight ?: return options.maxByOrNull { it.height ?: 0 }
    return options.filter { (it.height ?: 0) <= maxH }.maxByOrNull { it.height ?: 0 }
        ?: options.minByOrNull { it.height ?: Int.MAX_VALUE }
}

private const val EPISODE_RANGE_SIZE = 100
private const val EPISODES_PER_ROW = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    onPlay: (animeId: String, episodeNumber: Int) -> Unit,
    onBack: () -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedEpisodes.collectAsStateWithLifecycle()
    val inDownloads by vm.episodesInDownloads.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val seasonDownload by vm.seasonDownload.collectAsStateWithLifecycle()
    val defaultQuality by vm.defaultQuality.collectAsStateWithLifecycle()
    val d = detail

    Box(Modifier.fillMaxSize()) {
        if (d != null) {
            val showRanges = d.episodes.size > EPISODE_RANGE_SIZE
            var rangeIndex by remember(d.id, d.episodes.size) { mutableStateOf(0) }
            val rangeStart = if (showRanges) rangeIndex * EPISODE_RANGE_SIZE else 0
            val episodeRows = remember(d, rangeStart) {
                val visible =
                    if (showRanges) {
                        d.episodes.subList(rangeStart, minOf(rangeStart + EPISODE_RANGE_SIZE, d.episodes.size))
                    } else {
                        d.episodes
                    }
                visible.chunked(EPISODES_PER_ROW)
            }
            val infoLine = listOfNotNull(d.studio, d.duration?.let { "$it min/ep" }).joinToString("  •  ")
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DetailHero(d) }
                if (d.genres.isNotEmpty()) {
                    item {
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            d.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Text(
                                        genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onPlay(d.id, d.episodes.firstOrNull()?.number ?: 1) },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Text("  Watch", fontWeight = FontWeight.Bold)
                        }
                        SeasonDownloadButton(
                            state = seasonDownload,
                            allInDownloads = d.episodes.isNotEmpty() &&
                                d.episodes.all { it.number in inDownloads },
                            onClick = vm::downloadSeason,
                        )
                        FilledTonalIconButton(
                            onClick = vm::toggleSaved,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                if (saved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                                if (saved) "Remove from My List" else "Add to My List",
                                tint = if (saved) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (d.synopsis.isNotBlank()) {
                    item { ExpandableSynopsis(d.synopsis) }
                }
                if (infoLine.isNotEmpty()) {
                    item {
                        Text(
                            infoLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (d.episodes.isNotEmpty()) {
                                Text(
                                    "${d.episodes.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val sd = seasonDownload
                        Text(
                            when {
                                sd != null && !sd.finished ->
                                    "Queuing season… ${sd.queued + sd.failed}/${sd.total}"
                                sd != null && sd.failed > 0 ->
                                    "Season queued — ${sd.failed} episode${if (sd.failed == 1) "" else "s"} failed"
                                else -> "Tap to play — hold to download"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                items(episodeRows.size, key = { "eps-" + episodeRows[it].first().id }) { rowIndex ->
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        episodeRows[rowIndex].forEach { ep ->
                            EpisodeCell(
                                episode = ep,
                                downloaded = ep.number in downloaded,
                                onClick = { onPlay(d.id, ep.number) },
                                onLongClick = { vm.download(ep) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(EPISODES_PER_ROW - episodeRows[rowIndex].size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Box(Modifier.navigationBarsPadding().height(8.dp)) }
            }
        }

        // Floating back button (over the header art; also the only chrome while loading).
        Box(
            Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }
    }

    pending?.let { p ->
        val preselected = remember(p, defaultQuality) { pickForQuality(p.options, defaultQuality) }
        AlertDialog(
            onDismissRequest = vm::dismissPicker,
            title = { Text("Download quality") },
            text = {
                Column {
                    p.options.forEach { stream ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.chooseQuality(stream) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = stream == preselected, onClick = { vm.chooseQuality(stream) })
                            Text(
                                stream.quality ?: stream.height?.let { "${it}p" } ?: "Default",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissPicker) { Text("Cancel") } },
        )
    }
}

/**
 * One-press "download the whole season" button. While a queue pass runs it shows queued/total;
 * once every episode is in the downloads list it flips to a tinted done check.
 */
@Composable
private fun SeasonDownloadButton(
    state: SeasonDownload?,
    allInDownloads: Boolean,
    onClick: () -> Unit,
) {
    val queuing = state != null && !state.finished
    FilledTonalIconButton(
        onClick = onClick,
        enabled = !queuing,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.size(46.dp).testTag("download-season"),
    ) {
        when {
            queuing -> Text(
                "${state!!.queued + state.failed}/${state.total}",
                style = MaterialTheme.typography.labelSmall,
            )
            allInDownloads -> Icon(
                Icons.Filled.DownloadDone,
                "Season downloaded",
                tint = MaterialTheme.colorScheme.tertiary,
            )
            else -> Icon(
                Icons.Filled.Download,
                "Download season",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 9anime-style hero: edge-to-edge banner with a sharp overlapping poster and meta pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(d: AnimeDetail) {
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(252.dp)) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(210.dp)) {
            AsyncImage(
                model = d.bannerUrl ?: d.posterUrl,
                contentDescription = d.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
            )
        }
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
            AsyncImage(
                model = d.posterUrl,
                contentDescription = null,   // the banner already carries d.title
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 148.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                Modifier.align(Alignment.Bottom).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    d.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
private fun MetaPill(text: String, tinted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (tinted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ExpandableSynopsis(synopsis: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        synopsis,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .clickable { expanded = !expanded },
    )
}

/** 9anime-style numbered episode cell: tap plays, long-press downloads. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCell(
    episode: Episode,
    downloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (downloaded) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    // Downloaded state must be audible, not just a tint; the long-press action gets a label so
    // accessibility services surface the download affordance.
    val description =
        if (downloaded) "Episode ${episode.number}, downloaded" else "Episode ${episode.number}"
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Download",
            )
            .testTag("ep-${episode.number}")
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${episode.number}",
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}
