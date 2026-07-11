package com.anilocal.app.ui.more

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.source.Sources
import kotlin.math.roundToInt

@Composable
fun MoreScreen(
    onBrowseExtensions: () -> Unit,
    onConfigureSource: (String) -> Unit,
    vm: MoreViewModel = hiltViewModel(),
) {
    val autoSkip by vm.autoSkip.collectAsStateWithLifecycle()
    val autoPlayNext by vm.autoPlayNext.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val downloadQuality by vm.downloadQuality.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()

    val switchColors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)

        SectionCard {
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

        SectionCard {
            Text("Default download quality", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            DownloadQuality.entries.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setDownloadQuality(q) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = downloadQuality == q, onClick = { vm.setDownloadQuality(q) })
                    Text(q.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        SectionCard {
            Text("Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        SectionCard {
            // MyAnimeList Sync — expandable subsection.
            var malExpanded by remember { mutableStateOf(false) }
            val malUsername by vm.malUsername.collectAsStateWithLifecycle()
            val malSyncEnabled by vm.malSyncEnabled.collectAsStateWithLifecycle()
            val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().clickable { malExpanded = !malExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MyAnimeList Sync", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (malExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle")
            }
            if (malExpanded) {
                var usernameField by remember(malUsername) { mutableStateOf(malUsername) }
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
                    OutlinedButton(onClick = {
                        vm.setMalUsername(usernameField.trim()); vm.syncMalNow()
                    }) { Text("Sync now") }
                }
                syncStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Enter your MAL username to mirror your PUBLIC list (read-only) — no API key " +
                    "needed. Make sure your list privacy is Public on MAL, then filter it on the Library tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val selectedSourceId by vm.selectedSourceId.collectAsStateWithLifecycle()
            val lastAutoWinner by vm.lastAutoWinner.collectAsStateWithLifecycle()
            val provisioning by vm.provisioning.collectAsStateWithLifecycle()
            val provisionStatus by vm.provisionStatus.collectAsStateWithLifecycle()
            Text("Streaming source", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Where video is resolved from. Browsing and metadata always come from AniList.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // "Auto" only helps when there's a choice to make — hide it with 0/1 source installed.
            if (sources.size > 1) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSelectedSource(Sources.AUTO) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedSourceId == Sources.AUTO,
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
            sources.forEach { src ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSelectedSource(src.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedSourceId == src.id,
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
                            Icon(Icons.Filled.Settings, contentDescription = "Source settings")
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBrowseExtensions) { Text("Browse extensions") }
                OutlinedButton(onClick = vm::installRecommended, enabled = !provisioning) {
                    if (provisioning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Install recommended")
                    }
                }
            }
            provisionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Rounded settings-group card: the 9anime panel look for each More section. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
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
