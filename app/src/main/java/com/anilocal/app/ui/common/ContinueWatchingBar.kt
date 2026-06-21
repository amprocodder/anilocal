package com.anilocal.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.ContinueWatching

/**
 * A slim "resume the most recent show" bar that sits directly above the bottom navigation.
 * Tapping the bar resumes playback from the saved position; the trailing ✕ only hides the bar for
 * this session ([onDismiss]) — it does NOT remove the show from Continue Watching.
 */
@Composable
fun ContinueWatchingBar(
    item: ContinueWatching,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResume() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AsyncImage(
                    model = item.anime.posterUrl,
                    contentDescription = item.anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 32.dp, height = 44.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        item.anime.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Resume episode ${item.episodeNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Resume",
                    tint = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Hide resume bar")
                }
            }
            item.fraction?.let { f ->
                LinearProgressIndicator(
                    progress = { f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}
