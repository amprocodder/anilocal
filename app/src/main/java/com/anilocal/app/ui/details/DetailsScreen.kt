package com.anilocal.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.anilocal.app.ui.common.loadOrNull
import com.anilocal.app.ui.common.CatalogFeedback
import com.anilocal.app.ui.common.CatalogLoad
import com.anilocal.app.ui.common.CatalogLoadState
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
    streams: StreamRepository,
    skip: SkipRepository,
    downloads: DownloadRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val animeId: String = checkNotNull(savedState["animeId"])
    private val loader = CatalogLoad<AnimeDetail?>(viewModelScope, null) { catalog.detail(animeId) }
    val detail = loader.value
    val loadState = loader.state
    fun retry() = loader.refresh()

    val saved: StateFlow<Boolean> =
        library.isSaved(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedEpisodes: StateFlow<Set<Int>> =
        downloads.downloadedEpisodes(animeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val defaultQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)

    private val downloadRequests = DownloadRequests(viewModelScope, { detail.value }, streams, skip, downloads)
    val pending = downloadRequests.pending
    val busyEpisodes = downloadRequests.busyEpisodes
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    fun toggleSaved() {
        val d = detail.value ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                loadOrNull { library.toggle(AnimeSummary(d.id, d.title, d.posterUrl, d.idMal)) }
            } finally {
                _saving.value = false
            }
        }
    }

    fun download(episode: Episode) = downloadRequests.download(episode)
    fun chooseQuality(request: PendingDownload, stream: VideoStream) = downloadRequests.chooseQuality(request, stream)
    fun dismissPicker(request: PendingDownload) = downloadRequests.dismissPicker(request)
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
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedEpisodes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val busyEpisodes by vm.busyEpisodes.collectAsStateWithLifecycle()
    val defaultQuality by vm.defaultQuality.collectAsStateWithLifecycle()
    val d = detail

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(d?.title ?: if (loadState == CatalogLoadState.Failed) "Anime" else "Loading…") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CatalogFeedback(loadState, vm::retry)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loadState != CatalogLoadState.Ready) {
                item(key = "load-state", contentType = "load-state") { CatalogFeedback(loadState, vm::retry) }
            }
            item(key = "artwork", contentType = "artwork") {
                AsyncImage(
                    model = d.bannerUrl ?: d.posterUrl,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }
            item(key = "title", contentType = "text") { Text(d.title, style = MaterialTheme.typography.headlineSmall) }
            item(key = "play", contentType = "action") {
                Button(onClick = { onPlay(d.id, d.episodes.firstOrNull()?.number ?: 1) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, null); Text("  Play")
                }
            }
            item(key = "save", contentType = "action") {
                OutlinedButton(onClick = vm::toggleSaved, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved) "Remove from My List" else "Add to My List")
                }
            }
            item(key = "synopsis", contentType = "text") { Text(d.synopsis, style = MaterialTheme.typography.bodyMedium) }
            item(key = "episodes", contentType = "text") { Text("Episodes", style = MaterialTheme.typography.titleMedium) }
            items(d.episodes, key = { it.id }, contentType = { "episode" }) { ep ->
                ListItem(
                    headlineContent = { Text(ep.title ?: "Episode ${ep.number}") },
                    leadingContent = { Text("${ep.number}") },
                    trailingContent = {
                        if (ep.number in downloaded) {
                            Icon(Icons.Filled.DownloadDone, "Downloaded",
                                tint = MaterialTheme.colorScheme.primary)
                        } else {
                            IconButton(onClick = { vm.download(ep) }, enabled = ep.number !in busyEpisodes) {
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
