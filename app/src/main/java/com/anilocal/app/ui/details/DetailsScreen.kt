package com.anilocal.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(d?.title ?: "Loading…") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { padding ->
        if (d == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AsyncImage(
                    model = d.bannerUrl ?: d.posterUrl,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }
            item { Text(d.title, style = MaterialTheme.typography.headlineSmall) }
            item {
                Button(onClick = { onPlay(d.id, d.episodes.firstOrNull()?.number ?: 1) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, null); Text("  Play")
                }
            }
            item {
                OutlinedButton(onClick = vm::toggleSaved, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved) "Remove from My List" else "Add to My List")
                }
            }
            item { Text(d.synopsis, style = MaterialTheme.typography.bodyMedium) }
            item { Text("Episodes", style = MaterialTheme.typography.titleMedium) }
            items(d.episodes, key = { it.id }) { ep ->
                ListItem(
                    headlineContent = { Text(ep.title ?: "Episode ${ep.number}") },
                    leadingContent = { Text("${ep.number}") },
                    trailingContent = {
                        if (ep.number in downloaded) {
                            Icon(Icons.Filled.DownloadDone, "Downloaded",
                                tint = MaterialTheme.colorScheme.primary)
                        } else {
                            IconButton(onClick = { vm.download(ep) }) {
                                Icon(Icons.Filled.Download, "Download")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(d.id, ep.number) },
                )
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
