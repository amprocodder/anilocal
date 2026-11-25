package com.anilocal.app.ui.player

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.ui.common.SectionHeader
import com.anilocal.app.ui.common.scoreLabel
import com.anilocal.app.ui.details.MetaPill

/**
 * What the non-fullscreen "watch page" shows beside/under the video: what's playing, the title's
 * details, an up-next card, and the season's episodes (tap one to switch in place). Renders with
 * whatever is known — the title alone while AniList is loading or offline — and fills in as [info]
 * arrives.
 */
internal class WatchContent(
    val title: String,
    val info: AnimeDetail?,
    val episodes: List<Episode>,
    val currentEpisode: Int,
    val onPick: (Int) -> Unit,
) {
    val current: Episode? = episodes.firstOrNull { it.number == currentEpisode }
    val next: Episode? = episodes.filter { it.number > currentEpisode }.minByOrNull { it.number }
    val art: String? = info?.let { it.bannerUrl ?: it.posterHdUrl ?: it.posterUrl }
}

/** Phone layout: one scrolling column — header, up next, details, then episodes — under the video. */
@Composable
internal fun WatchPanel(content: WatchContent, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        watchInfo(content)
        watchEpisodes(content, rowColor = { MaterialTheme.colorScheme.surfaceContainer })
        item(key = "bottom-gap") { Box(Modifier.navigationBarsPadding()) }
    }
}

/**
 * Tablet layout, left pane under the video: the title header with the up-next card beside it
 * (not a 1500px-wide strip), then genres and a synopsis held to a readable line length. No
 * backdrop of its own — the page's ambient art sits behind everything on tablets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WatchInfoPane(content: WatchContent, modifier: Modifier = Modifier) {
    val info = content.info
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header-row", contentType = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WatchHeader(content, backdrop = false) }
                content.next?.let { next ->
                    Box(Modifier.width(UP_NEXT_TABLET_WIDTH).padding(top = 16.dp)) {
                        UpNextCard(next, content.art, onClick = { content.onPick(next.number) })
                    }
                }
            }
        }
        if (info != null && info.genres.isNotEmpty()) item(key = "genres") { GenreChips(info.genres) }
        if (info != null && info.synopsis.isNotBlank()) item(key = "synopsis") {
            Box(Modifier.widthIn(max = READABLE_WIDTH)) { Synopsis(info.synopsis) }
        }
        item(key = "bottom-gap") { Box(Modifier.navigationBarsPadding()) }
    }
}

private val UP_NEXT_TABLET_WIDTH = 380.dp
private val READABLE_WIDTH = 760.dp

/** Tablet layout, right pane beside the video: the episode list on its own raised panel. */
@Composable
internal fun WatchEpisodesPane(content: WatchContent, modifier: Modifier = Modifier) {
    // Item 0 is the header, so index `start` opens with the previous row above the playing one.
    val start = content.episodes.indexOfFirst { it.number == content.currentEpisode }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceAtLeast(0))
    // A floating, slightly translucent card so the ambient art glows through at the edges.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            watchEpisodes(content, rowColor = { MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.watchInfo(c: WatchContent) {
    val info = c.info
    item(key = "header", contentType = "header") { WatchHeader(c) }
    c.next?.let { next ->
        item(key = "up-next", contentType = "up-next") {
            UpNextCard(next, c.art, onClick = { c.onPick(next.number) })
        }
    }
    if (info == null) return
    if (info.genres.isNotEmpty()) item(key = "genres") { GenreChips(info.genres) }
    if (info.synopsis.isNotBlank()) item(key = "synopsis") { Synopsis(info.synopsis) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(genres: List<String>) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        genres.forEach { genre ->
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    genre,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/**
 * Tablet watch page's ambient light: the series art, heavily blurred and dimmed, filling the
 * page behind the video and panels and fading into the background toward the bottom.
 */
@Composable
internal fun AmbientBackdrop(art: String?, modifier: Modifier = Modifier) {
    if (art == null) return
    val bg = MaterialTheme.colorScheme.background
    Box(modifier) {
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.32f,
            modifier = Modifier.fillMaxSize().blur(80.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.2f), bg.copy(alpha = 0.75f), bg))),
        )
    }
}

/**
 * Title block: the series art blurred into a soft backdrop, the poster, title + romaji, a
 * "now playing" line, and the meta pills — a compact echo of the Details hero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchHeader(c: WatchContent, backdrop: Boolean = true) {
    val info = c.info
    val bg = MaterialTheme.colorScheme.background
    val name = c.current?.title?.takeIf { it.isNotBlank() && it != "Episode ${c.currentEpisode}" }
    Box(Modifier.fillMaxWidth()) {
        if (backdrop && c.art != null) {
            AsyncImage(
                model = c.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.matchParentSize().blur(28.dp),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.3f), bg))),
            )
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info?.posterUrl != null) {
                    AsyncImage(
                        model = info.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 68.dp, height = 96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        info?.title ?: c.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    info?.romajiTitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Filled.Equalizer, null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            buildString {
                                append("Episode ${c.currentEpisode}")
                                name?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            if (info != null) {
                val pills = listOfNotNull(
                    info.status?.let { it to true },
                    info.format?.let { it to false },
                    info.seasonYear?.let { it.toString() to false },
                    info.averageScore?.let { scoreLabel(it) to false },
                    c.episodes.size.takeIf { it > 0 }?.let { "$it eps" to false },
                    info.studio?.let { it to false },
                )
                if (pills.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pills.forEach { (text, tinted) -> MetaPill(text, tinted) }
                    }
                }
            }
        }
    }
}

/** A raised card for the next episode with a big play affordance — the one-tap "keep watching". */
@Composable
private fun UpNextCard(next: Episode, fallbackArt: String?, onClick: () -> Unit) {
    val name = next.title?.takeIf { it.isNotBlank() && it != "Episode ${next.number}" }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Up next: episode ${next.number}" },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            EpisodeThumb(next, fallbackArt, current = false, width = 104.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "Episode ${next.number}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                name?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .clickable(
                enabled = overflows || expanded,
                onClickLabel = if (expanded) "Show less" else "Show more",
            ) { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        // Only offer the toggle when the collapsed text is actually cut off.
        if (overflows || expanded) {
            Text(
                if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun LazyListScope.watchEpisodes(c: WatchContent, rowColor: @Composable () -> Color) {
    if (c.episodes.isEmpty()) return
    item(key = "episodes-header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, end = 16.dp),
        ) {
            SectionHeader("Episodes", Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    "${c.episodes.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
    items(c.episodes, key = { "ep-" + it.id }, contentType = { "episode" }) { ep ->
        WatchEpisodeRow(
            episode = ep,
            fallbackArt = c.art,
            current = ep.number == c.currentEpisode,
            color = rowColor(),
            onClick = { if (ep.number != c.currentEpisode) c.onPick(ep.number) },
        )
    }
}

@Composable
private fun WatchEpisodeRow(
    episode: Episode,
    fallbackArt: String?,
    current: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val name = episode.title?.takeIf { it.isNotBlank() && it != "Episode ${episode.number}" }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else color,
        border = if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {
                selected = current
                contentDescription = buildString {
                    append("Episode ${episode.number}")
                    name?.let { append(", $it") }
                    if (current) append(", now playing")
                }
            },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EpisodeThumb(episode, fallbackArt, current, width = 112.dp)
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Episode ${episode.number}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (name != null) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (current) {
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

/**
 * 16:9 episode thumbnail: AniList's per-episode art with a number badge, or — when there's none,
 * which is common — a midnight gradient tile led by the number over a faint wash of the series art
 * (the same full-strength banner on every row reads as a wall of identical cards).
 */
@Composable
private fun EpisodeThumb(episode: Episode, fallbackArt: String?, current: Boolean, width: Dp) {
    val thumb = episode.thumbnailUrl
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHighest))),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallbackArt != null) {
            AsyncImage(
                model = fallbackArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.18f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            current -> {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Icon(AppIcons.Filled.Equalizer, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            thumb == null -> Text(
                "${episode.number}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            else -> Text(
                "${episode.number}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
