package com.anilocal.app.ui.more

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import com.anilocal.app.ui.common.ScreenHeader
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anilocal.app.appViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.source.Sources
import kotlin.math.roundToInt

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    onBrowseExtensions: () -> Unit,
    onConfigureSource: (String) -> Unit,
    vm: MoreViewModel = appViewModel { MoreViewModel(it.settings, it.mal, it.sources, it.extensions) },
) {
    val switchColors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
    val context = LocalContext.current
    // One-shot confirmation when a requested install finishes (the inline status is easy to miss).
    LaunchedEffect(vm) {
        vm.installEvents.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "title", contentType = "title") {
            ScreenHeader(
                "Settings",
                ContentWidth.padding(bottom = 4.dp),
                subtitle = "Sources, playback, downloads and sync",
                horizontalPadding = 0.dp,
            )
        }

        // Streaming source first: it's the one thing a new install must set up (the app ships
        // with no sources), so it shouldn't sit below every playback preference.
        item(key = "sources", contentType = "settings") {
            SectionCard(
                AppIcons.Outlined.Hub,
                "Streaming source",
                "Where video is resolved from. Browsing and metadata always come from AniList.",
            ) {
                val sources by vm.sources.collectAsStateWithLifecycle()
                val selectedSourceId by vm.selectedSourceId.collectAsStateWithLifecycle()
                val lastAutoWinner by vm.lastAutoWinner.collectAsStateWithLifecycle()
                val provisioning by vm.provisioning.collectAsStateWithLifecycle()
                val provisionStatus by vm.provisionStatus.collectAsStateWithLifecycle()
                // "Auto" only helps when there's a choice to make — hide it with 0/1 source
                // installed. Keep it visible when it is already selected, though: extension
                // removal can reduce the list after the preference was saved, and hiding the
                // selected row leaves the settings screen with no selected option.
                // With no saved choice (or a since-removed one) streams resolve via the first
                // installed source, so show THAT as selected rather than an empty radio group.
                val effectiveId = when {
                    selectedSourceId == Sources.AUTO -> Sources.AUTO
                    sources.any { it.id == selectedSourceId } -> selectedSourceId
                    else -> sources.firstOrNull()?.id
                }
                if (sources.size > 1 || selectedSourceId == Sources.AUTO) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setSelectedSource(Sources.AUTO) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = effectiveId == Sources.AUTO,
                            onClick = { vm.setSelectedSource(Sources.AUTO) },
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Auto (best source)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (lastAutoWinner.isBlank()) "Races your installed sources and reuses the fastest"
                                else "Races your installed sources · last: $lastAutoWinner",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (sources.isEmpty()) {
                    // The app ships with no sources on purpose; say so plainly instead of leaving
                    // an empty list the user has to interpret.
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Outlined.Info, null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "No sources installed yet. Add a repo in Browse extensions, or " +
                                    "install the recommended sources.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
                sources.forEach { src ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setSelectedSource(src.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = effectiveId == src.id,
                            onClick = { vm.setSelectedSource(src.id) },
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(src.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (src.isExternal) "Extension · ${src.lang}" else "Built-in",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (src.configurable) {
                            IconButton(onClick = { onConfigureSource(src.id) }) {
                                Icon(AppIcons.Filled.Settings, contentDescription = "Source settings")
                            }
                        }
                    }
                }
                // Stacked full-width actions: side by side, the long label wrapped onto three lines.
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onBrowseExtensions,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(AppIcons.Outlined.Extension, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse extensions", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = vm::installRecommended,
                        enabled = !provisioning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (provisioning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(AppIcons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install recommended sources")
                        }
                    }
                }
                provisionStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }


        item(key = "playback", contentType = "settings") {
            val autoSkip by vm.autoSkip.collectAsStateWithLifecycle()
            val autoPlayNext by vm.autoPlayNext.collectAsStateWithLifecycle()
            val precacheNext by vm.precacheNext.collectAsStateWithLifecycle()
            val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
            SectionCard(AppIcons.Outlined.PlayCircle, "Playback") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-skip intro/outro", style = MaterialTheme.typography.bodyLarge)
                        Text("Skip automatically when a marker is reached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoSkip, onCheckedChange = vm::setAutoSkip, colors = switchColors)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-play next episode", style = MaterialTheme.typography.bodyLarge)
                        Text("Start the next episode automatically when one ends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoPlayNext, onCheckedChange = vm::setAutoPlayNext, colors = switchColors)
                }

                // Only meaningful with auto-play on: it prepares the episode auto-play would start.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).alpha(if (autoPlayNext) 1f else 0.38f)) {
                        Text("Pre-cache next episode", style = MaterialTheme.typography.bodyLarge)
                        Text("Load the next episode near the end so it starts instantly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = precacheNext, onCheckedChange = vm::setPrecacheNext,
                        enabled = autoPlayNext, colors = switchColors)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Download over WiFi only", style = MaterialTheme.typography.bodyLarge)
                        Text("Pause downloads on mobile data; resume on WiFi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnly, colors = switchColors)
                }
            }
        }

        item(key = "quality", contentType = "settings") {
            val downloadQuality by vm.downloadQuality.collectAsStateWithLifecycle()
            SectionCard(
                AppIcons.Outlined.HighQuality,
                "Default download quality",
                "Used when a source offers several qualities.",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadQuality.entries.forEach { q ->
                        FilterChip(
                            selected = downloadQuality == q,
                            onClick = { vm.setDownloadQuality(q) },
                            label = { Text(q.label) },
                            leadingIcon = if (downloadQuality == q) {
                                { Icon(AppIcons.Filled.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }

        item(key = "subtitles", contentType = "settings") {
            val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
            val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()
            SectionCard(AppIcons.Outlined.ClosedCaption, "Subtitles") {
                var sliderScale by remember(subtitleScale) { mutableStateOf(subtitleScale) }
                // Live preview — reflects the in-progress slider value and the background toggle so the
                // effect is visible before leaving the screen. Mirrors the player's white-text /
                // black-outline / optional translucent-box styling (see PlayerScreen).
                SubtitlePreview(scale = sliderScale, background = subtitleBackground)
                Text("Text size: ${(sliderScale * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = sliderScale,
                    onValueChange = { sliderScale = it },
                    onValueChangeFinished = { vm.setSubtitleScale(sliderScale) },
                    valueRange = 0.6f..2.0f,
                    // 0.6→2.0 in 0.05 (5%) increments: 28 intervals ⇒ 27 inner step ticks.
                    steps = 27,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtitle background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = subtitleBackground, onCheckedChange = vm::setSubtitleBackground, colors = switchColors)
                }
            }
        }

        item(key = "mal", contentType = "settings") {
            // MyAnimeList Sync — expandable subsection.
            var malExpanded by rememberSaveable { mutableStateOf(false) }
            SectionCard(
                AppIcons.Outlined.Sync,
                "MyAnimeList Sync",
                "Mirror your public MAL list into My List",
                onHeaderClick = { malExpanded = !malExpanded },
                trailing = {
                    Icon(
                        if (malExpanded) AppIcons.Filled.ExpandLess else AppIcons.Filled.ExpandMore,
                        if (malExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                val malUsername by vm.malUsername.collectAsStateWithLifecycle()
                val malSyncEnabled by vm.malSyncEnabled.collectAsStateWithLifecycle()
                val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
                val syncing by vm.syncing.collectAsStateWithLifecycle()
                if (malExpanded) {
                    var usernameField by rememberSaveable(malUsername) { mutableStateOf(malUsername) }
                    OutlinedTextField(
                        value = usernameField,
                        onValueChange = { usernameField = it },
                        label = { Text("MAL username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Active sync (on app open)", style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f))
                        Switch(checked = malSyncEnabled, onCheckedChange = vm::setMalSyncEnabled, colors = switchColors)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.setMalUsername(usernameField.trim())
                        }) { Text("Save") }
                        OutlinedButton(enabled = !syncing, onClick = {
                            vm.syncMalNow(usernameField.trim())
                        }) { Text("Sync now") }
                    }
                    syncStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Enter your MAL username to mirror your PUBLIC list (read-only) — no API key " +
                        "needed. Make sure your list privacy is Public on MAL, then filter it on the My List tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

    }
}

/** Settings read best at a column width; on tablets, full-bleed 2900px rows are hard to scan. */
private val ContentWidth = Modifier.widthIn(max = 840.dp).fillMaxWidth()

/**
 * Rounded settings-group card with an icon-badged header (title + optional muted subtitle and a
 * trailing slot), matching the raised cards of the watch page.
 */
@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onHeaderClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = ContentWidth,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/**
 * A scaled-down video frame that previews a sample caption at the SAME PHYSICAL SIZE it will render
 * on the player. PlayerScreen sizes captions as [SubtitleView.DEFAULT_TEXT_SIZE_FRACTION] * [scale]
 * of the *full-screen* PlayerView's height. This thumbnail is far shorter than the screen, so sizing
 * the caption as a fraction of THIS frame would be proportionally correct but physically tiny (~1/4
 * the on-screen size) — which read as "too small". Instead we compute the caption's absolute pixel
 * size from the real screen height and pin it with [SubtitleView.setFixedTextSize], so the preview
 * text is the same physical size the user will see during playback: a true "this is how big your
 * subtitles will be" preview. Styling (white text, black outline, optional translucent box) mirrors
 * PlayerScreen exactly.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitlePreview(scale: Float, background: Boolean) {
    // The player's SubtitleView fills the screen, so its caption height in px is
    // DEFAULT_TEXT_SIZE_FRACTION * scale * screenHeight. Reproduce that absolute size here (using the
    // current orientation's height — the same orientation the user previews & plays in).
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val captionPx = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale * screenHeightPx
    // Centered, capped-width 16:9 frame standing in for the player surface. Capped so it stays a
    // thumbnail on wide/landscape screens, but wide enough that a player-sized caption has room
    // instead of wrapping unrealistically in a narrow box.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF455A64), Color(0xFF1C242A), Color(0xFF000000)),
                    )
                ),
        ) {
            AndroidView(
                factory = { ctx -> SubtitleView(ctx).apply { setApplyEmbeddedStyles(false) } },
                update = { sv ->
                    // Pin the caption to the player's ABSOLUTE on-screen size (px), not a fraction of
                    // this small frame — so the preview matches the player 1:1 in physical size.
                    sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, captionPx)
                    val bg = if (background) android.graphics.Color.argb(160, 0, 0, 0)
                    else android.graphics.Color.TRANSPARENT
                    sv.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            bg,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    sv.setCues(listOf(Cue.Builder().setText("Sample subtitle text").build()))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
