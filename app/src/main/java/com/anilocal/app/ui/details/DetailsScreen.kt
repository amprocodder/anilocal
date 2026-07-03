package com.anilocal.app.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A download awaiting a quality choice (when more than one variant is available). */
data class PendingDownload(
    val episode: Episode,
    val markers: List<SkipMarker>,
    val options: List<VideoStream>,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val downloads: DownloadRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val animeId: String = checkNotNull(savedState["animeId"])
    private val _detail = MutableStateFlow<AnimeDetail?>(null)
    val detail: StateFlow<AnimeDetail?> = _detail

    val saved: StateFlow<Boolean> =
        library.isSaved(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedEpisodes: StateFlow<Set<Int>> =
        downloads.downloadedEpisodes(animeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val defaultQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)

    private val _pending = MutableStateFlow<PendingDownload?>(null)
    val pending: StateFlow<PendingDownload?> = _pending

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
}

/** The variant the picker pre-selects, given the user's default-quality preference. */
private fun pickForQuality(options: List<VideoStream>, quality: DownloadQuality): VideoStream? {
    val maxH = quality.maxHeight ?: return options.maxByOrNull { it.height ?: 0 }
    return options.filter { (it.height ?: 0) <= maxH }.maxByOrNull { it.height ?: 0 }
        ?: options.minByOrNull { it.height ?: Int.MAX_VALUE }
}

@Composable
fun DetailsScreen(
    onPlay: (animeId: String, episodeNumber: Int) -> Unit,
    onBack: () -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedEpisodes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val defaultQuality by vm.defaultQuality.collectAsStateWithLifecycle()
    val d = detail

    Box(Modifier.fillMaxSize()) {
        if (d != null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderArt(d) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(d.title, style = MaterialTheme.typography.headlineSmall)
                        if (d.genres.isNotEmpty()) {
                            Text(
                                d.genres.joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Episodes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (d.episodes.isNotEmpty()) {
                            Text(
                                "${d.episodes.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(d.episodes, key = { it.id }) { ep ->
                    EpisodeCard(
                        episode = ep,
                        downloaded = ep.number in downloaded,
                        onClick = { onPlay(d.id, ep.number) },
                        onDownload = { vm.download(ep) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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

/** Edge-to-edge header art with a bottom scrim fading into the page background. */
@Composable
private fun HeaderArt(d: AnimeDetail) {
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(240.dp)) {
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
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
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

/** AniLab-style episode row: dark rounded card, bold number badge, download state trailing. */
@Composable
private fun EpisodeCard(
    episode: Episode,
    downloaded: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(Modifier.size(width = 44.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "${episode.number}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                episode.title ?: "Episode ${episode.number}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (downloaded) {
                Icon(
                    Icons.Filled.DownloadDone, "Downloaded",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp).size(22.dp),
                )
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Filled.Download, "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
