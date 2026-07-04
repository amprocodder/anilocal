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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** All of one title's downloads, shown as a collapsible season folder. */
data class SeasonFolder(
    val animeId: String,
    val title: String,
    val posterUrl: String?,
    val episodes: List<DownloadItem>,   // sorted by episode number
) {
    val completed: Int get() = episodes.count { it.state == DownloadState.COMPLETED }
    val active: Int get() = episodes.count {
        it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED
    }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    // Removals run here, not on viewModelScope: leaving the tab mid-way must not cancel a season
    // delete half-done (file cleanup + Room rows would go inconsistent).
    @Named("appScope") private val appScope: CoroutineScope,
) : ViewModel() {
    /**
     * Downloads grouped into per-title season folders. The DAO emits newest-first, and groupBy
     * keeps first-encounter order, so folders are ordered by most recent download activity.
     */
    val folders: StateFlow<List<SeasonFolder>> =
        downloads.downloads.map { items ->
            items.groupBy { it.animeId }.map { (animeId, eps) ->
                SeasonFolder(
                    animeId = animeId,
                    title = eps.first().title,
                    posterUrl = eps.firstNotNullOfOrNull { it.posterUrl },
                    episodes = eps.sortedBy { it.episodeNumber },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Folders start expanded; the set remembers what the user collapsed (in the VM so it
    // survives tab switches, keyed by animeId so it tracks folders across list changes).
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    val collapsed: StateFlow<Set<String>> = _collapsed

    fun toggleFolder(animeId: String) {
        _collapsed.value =
            if (animeId in _collapsed.value) _collapsed.value - animeId
            else _collapsed.value + animeId
    }

    fun pause(id: String) = downloads.pause(id)
    fun resume(id: String) = downloads.resume(id)
    fun remove(id: String) { appScope.launch { downloads.remove(id) } }

    /**
     * Deletes by live lookup, not the dialog's UI snapshot — an episode that finished queuing after
     * the confirm dialog opened is deleted too, instead of surviving as an orphan row.
     */
    fun removeSeason(animeId: String) {
        appScope.launch {
            runCatching { downloads.downloads.first() }.getOrDefault(emptyList())
                .filter { it.animeId == animeId }
                .forEach { downloads.remove(it.id) }
        }
    }
}

@Composable
fun DownloadsScreen(
    onPlay: (animeId: String, episodeNumber: Int) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val collapsed by vm.collapsed.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SeasonFolder?>(null) }

    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloads yet — tap the download icon on an episode.",
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        folders.forEach { folder ->
            val expanded = folder.animeId !in collapsed
            item(key = "season-${folder.animeId}") {
                SeasonHeader(
                    folder = folder,
                    expanded = expanded,
                    onToggle = { vm.toggleFolder(folder.animeId) },
                    onDeleteAll = { pendingDelete = folder },
                )
            }
            if (expanded) {
                items(folder.episodes, key = { it.id }) { d ->
                    EpisodeRow(
                        item = d,
                        onClick = { if (d.state == DownloadState.COMPLETED) onPlay(d.animeId, d.episodeNumber) },
                        onPause = { vm.pause(d.id) },
                        onResume = { vm.resume(d.id) },
                        onDelete = { vm.remove(d.id) },
                    )
                }
            }
        }
    }

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete season?") },
            text = {
                Text(
                    // "episodes", not "downloaded episodes" — the folder also holds queued,
                    // in-progress and failed rows, and deleting cancels the in-flight ones.
                    "Remove all ${folder.episodes.size} episode" +
                        (if (folder.episodes.size == 1) "" else "s") +
                        " of “${folder.title}” from downloads?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeSeason(folder.animeId)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Season folder header: poster + title + aggregate status; tap to expand/collapse. */
@Composable
private fun SeasonHeader(
    folder: SeasonFolder,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .testTag("season-${folder.animeId}")
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = folder.posterUrl,
                contentDescription = null,   // the header row's Text already announces the title
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    folder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val eps = folder.episodes.size
                Text(
                    buildString {
                        append("$eps episode${if (eps == 1) "" else "s"}")
                        if (folder.completed > 0) append(" · ${folder.completed} downloaded")
                        if (folder.active > 0) append(" · ${folder.active} downloading")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A thin aggregate bar while anything in the folder is still coming down —
                // averaged over the episodes actually moving (paused/failed ones would pin it).
                if (folder.active > 0) {
                    val inFlight = folder.episodes.filter {
                        it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED
                    }
                    ProgressBar(inFlight.sumOf { it.progress } / inFlight.size.coerceAtLeast(1))
                }
            }
            IconButton(onClick = onDeleteAll) { Icon(Icons.Filled.Delete, "Delete season") }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One episode inside an expanded folder (the folder header already shows poster + title). */
@Composable
private fun EpisodeRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Episode ${item.episodeNumber}" + (item.quality?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (item.state) {
                    DownloadState.DOWNLOADING -> {
                        ProgressBar(item.progress)
                        Text("Downloading ${item.progress}%", style = MaterialTheme.typography.labelSmall)
                    }
                    DownloadState.QUEUED -> Text("Waiting for network…", style = MaterialTheme.typography.labelSmall)
                    DownloadState.PAUSED -> {
                        ProgressBar(item.progress)
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
}

@Composable
private fun ProgressBar(progress: Int) {
    LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
