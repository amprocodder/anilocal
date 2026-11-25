package com.anilocal.app.ui.common

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.image.RemoteImage
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.LinearProgressIndicator
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

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
    Surface(
        color = AppTheme.colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResume() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                RemoteImage(
                    url = item.anime.posterUrl,
                    contentDescription = item.anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 32.dp, height = 44.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        item.anime.title,
                        style = AppTheme.type.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Resume episode ${item.episodeNumber}",
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    AppIcons.Filled.PlayArrow,
                    contentDescription = "Resume",
                    tint = AppTheme.colors.primary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.Filled.Close, contentDescription = "Hide resume bar")
                }
            }
            item.fraction?.let { f ->
                LinearProgressIndicator(
                    progress = { f },
                    color = AppTheme.colors.tertiary,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}
