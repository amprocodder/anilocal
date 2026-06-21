package com.anilocal.app.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.DownloadItem
import com.anilocal.app.domain.model.DownloadState
import com.anilocal.app.domain.repo.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
) : ViewModel() {
    val items: StateFlow<List<DownloadItem>> =
        downloads.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(id: String) = downloads.pause(id)
    fun resume(id: String) = downloads.resume(id)
    fun remove(id: String) = viewModelScope.launch { downloads.remove(id) }
}

@Composable
fun DownloadsScreen(
    onPlay: (animeId: String, episodeNumber: Int) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloads yet — tap the download icon on an episode.",
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Downloads", style = MaterialTheme.typography.titleLarge) }
        items(items, key = { it.id }) { d ->
            DownloadRow(
                item = d,
                onClick = { if (d.state == DownloadState.COMPLETED) onPlay(d.animeId, d.episodeNumber) },
                onPause = { vm.pause(d.id) },
                onResume = { vm.resume(d.id) },
                onDelete = { vm.remove(d.id) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(60.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "Episode ${item.episodeNumber}" + (item.quality?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading ${item.progress}%", style = MaterialTheme.typography.labelSmall)
                }
                DownloadState.QUEUED -> Text("Waiting for network…", style = MaterialTheme.typography.labelSmall)
                DownloadState.PAUSED -> {
                    LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Paused (${item.progress}%)", style = MaterialTheme.typography.labelSmall)
                }
                DownloadState.COMPLETED -> Text("Downloaded — tap to play offline",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                DownloadState.FAILED -> Text("Failed", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        // State-dependent controls.
        when (item.state) {
            DownloadState.DOWNLOADING, DownloadState.QUEUED ->
                IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, "Pause") }
            DownloadState.PAUSED ->
                IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, "Resume") }
            DownloadState.FAILED ->
                IconButton(onClick = onResume) { Icon(Icons.Filled.Refresh, "Retry") }
            DownloadState.COMPLETED -> Unit
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove") }
    }
}
