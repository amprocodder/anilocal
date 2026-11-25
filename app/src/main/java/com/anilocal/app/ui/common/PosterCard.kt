package com.anilocal.app.ui.common

import com.anilocal.app.ui.icons.AppIcons
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.anilocal.app.ui.kit.PressIndication
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
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
import com.anilocal.app.ui.image.RemoteImage
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.LinearProgressIndicator
import com.anilocal.app.ui.kit.LocalTextStyle
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

@Composable
fun PosterCard(
    item: AnimeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    progress: Float? = null,
    onRemove: (() -> Unit)? = null,
    rank: Int? = null,
    badge: String? = null,
) {
    Column(modifier = modifier.width(130.dp).clickable(interactionSource = null, indication = PressIndication.Card) { onClick() }) {
        Box {
            RemoteImage(
                url = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            // Score, top-left on dark glass with an amber star; the remove chip owns that corner when present.
            val score = item.averageScore
            if (score != null && onRemove == null) {
                PosterBadge(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = StarColor)) { append("★") }
                        append(scoreLabel(score).removePrefix("★"))
                    },
                    Modifier.align(Alignment.TopStart),
                )
            }
            episodeBadge(item.format, item.episodes)?.let { label ->
                PosterBadge(AnnotatedString(label), Modifier.align(Alignment.BottomEnd))
            }
            // Watch progress: a bright line along the poster's bottom edge.
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    trackColor = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
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
                        AppIcons.Filled.Close,
                        contentDescription = "Remove from Continue Watching",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            // Relation tick (e.g. "Sequel"), top-right; the downloaded chip owns that corner when present.
            if (badge != null && !downloaded) {
                PosterBadge(
                    AnnotatedString(badge),
                    Modifier.align(Alignment.TopEnd),
                    color = AppTheme.colors.tertiary,
                )
            }
            if (downloaded) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.primary)
                        .padding(4.dp),
                ) {
                    Icon(
                        AppIcons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = AppTheme.colors.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = item.title,
            style = AppTheme.type.bodySmall,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Format only — the EP tick on the poster already carries the episode count.
        item.format?.let { format ->
            Text(
                text = format,
                style = AppTheme.type.labelSmall,
                color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val StarColor = Color(0xFFF2C14E)
private val GlassColor = Color(0xC70B0D11)

/** A small label on dark glass, inset 6dp into a poster corner, so the art stays in front. */
@Composable
private fun PosterBadge(text: AnnotatedString, modifier: Modifier, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
        modifier = modifier
            .padding(6.dp)
            .background(GlassColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Card/spotlight count label: "1 ep" / "25 eps" — or "Movie" for a movie, where a count of one says
 * nothing. Null when there's nothing to show (no released episodes yet).
 */
fun episodeBadge(format: String?, episodes: Int?): String? = when {
    format.equals("Movie", ignoreCase = true) -> "Movie"
    episodes == null || episodes <= 0 -> null
    episodes == 1 -> "1 ep"
    else -> "$episodes eps"
}
