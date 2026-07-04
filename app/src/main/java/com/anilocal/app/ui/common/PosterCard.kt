package com.anilocal.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.AnimeSummary

@Composable
fun PosterCard(
    item: AnimeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    progress: Float? = null,
    onRemove: (() -> Unit)? = null,
    rank: Int? = null,
) {
    Column(modifier = modifier.width(130.dp).clickable { onClick() }) {
        Box {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
            // Score tick, flush in the top-left corner; the remove chip owns that corner when present.
            val score = item.averageScore
            if (score != null && onRemove == null) {
                Text(
                    text = scoreLabel(score),
                    color = Color(0xFF111111),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            Color.White.copy(alpha = 0.92f),
                            RoundedCornerShape(bottomEnd = 6.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            item.episodes?.let { eps ->
                Text(
                    text = "EP $eps",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 6.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (rank != null) {
                Text(
                    text = "$rank",
                    color = when (rank) {
                        1 -> Color(0xFFF59E0B)
                        2 -> Color(0xFF9CA3AF)
                        3 -> Color(0xFFB45309)
                        else -> Color.White.copy(alpha = 0.75f)
                    },
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(Color.Black, blurRadius = 8f),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp),
                )
            }
            if (onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove() }
                        .semantics { role = Role.Button }
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove from Continue Watching",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (downloaded) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Format only — the EP tick on the poster already carries the episode count.
        item.format?.let { format ->
            Text(
                text = format,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
