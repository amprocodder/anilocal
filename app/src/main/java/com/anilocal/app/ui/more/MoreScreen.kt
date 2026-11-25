package com.anilocal.app.ui.more

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import com.anilocal.app.appViewModel
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.source.Sources
import com.anilocal.app.ui.common.ScreenHeader
import com.anilocal.app.ui.icons.AppIcons
import com.anilocal.app.ui.kit.Button
import com.anilocal.app.ui.kit.CircularProgressIndicator
import com.anilocal.app.ui.kit.FilterChip
import com.anilocal.app.ui.kit.HorizontalDivider
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.RadioButton
import com.anilocal.app.ui.kit.Slider
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Switch
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.kit.TextButton
import com.anilocal.app.ui.kit.TextInput
import com.anilocal.app.ui.kit.TonalButton
import com.anilocal.app.ui.player.CAPTION_TEXT_SIZE_FRACTION
import com.anilocal.app.ui.player.CaptionStyle
import com.anilocal.app.ui.player.Captions
import com.anilocal.app.ui.theme.AppTheme
import kotlin.math.roundToInt

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    onBrowseExtensions: () -> Unit,
    onConfigureSource: (String) -> Unit,
    vm: MoreViewModel = appViewModel { MoreViewModel(it.settings, it.mal, it.sources, it.extensions) },
) {
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
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "title", contentType = "title") {
            ScreenHeader(
                "Settings",
                ContentWidth,
                subtitle = "Sources, playback, downloads and sync",
                horizontalPadding = 0.dp,
            )
        }

        // Streaming source first: it's the one thing a new install must set up (the app ships
        // with no sources), so it shouldn't sit below every playback preference.
        item(key = "sources", contentType = "settings") {
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
            SettingsGroup(
                "Streaming source",
                "Where video is resolved from. Browsing and metadata always come from AniList.",
            ) {
                var first = true
                fun divider(): Boolean = !first.also { first = false }
                if (sources.size > 1 || selectedSourceId == Sources.AUTO) {
                    SourceRow(
                        name = "Auto (best source)",
                        detail = if (lastAutoWinner.isBlank()) "Races your installed sources and reuses the fastest"
                        else "Races your installed sources · last: $lastAutoWinner",
                        selected = effectiveId == Sources.AUTO,
                        divider = divider(),
                        onSelect = { vm.setSelectedSource(Sources.AUTO) },
                    )
                }
                if (sources.isEmpty()) {
                    // The app ships with no sources on purpose; say so plainly instead of leaving
                    // an empty list the user has to interpret.
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Outlined.Info, null, tint = AppTheme.colors.tertiary, modifier = Modifier.size(20.dp))
                        Text(
                            "No sources installed yet. Add a repo in Browse extensions, or " +
                                "install the recommended sources.",
                            style = AppTheme.type.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                sources.forEach { src ->
                    SourceRow(
                        name = src.name,
                        detail = if (src.isExternal) "Extension · ${src.lang}" else "Built-in",
                        selected = effectiveId == src.id,
                        divider = divider(),
                        onSelect = { vm.setSelectedSource(src.id) },
                        onConfigure = if (src.configurable) ({ onConfigureSource(src.id) }) else null,
                    )
                }
            }
            Column(ContentWidth.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton(onClick = onBrowseExtensions, modifier = Modifier.weight(1f)) {
                        Icon(AppIcons.Outlined.Extension, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse extensions", maxLines = 1)
                    }
                    TextButton(
                        onClick = vm::installRecommended,
                        enabled = !provisioning,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                    ) {
                        if (provisioning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(AppIcons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install recommended", maxLines = 1)
                        }
                    }
                }
                provisionStatus?.let {
                    Text(it, style = AppTheme.type.bodySmall, color = AppTheme.colors.onSurfaceVariant)
                }
            }
        }

        item(key = "playback", contentType = "settings") {
            val autoSkip by vm.autoSkip.collectAsStateWithLifecycle()
            val autoPlayNext by vm.autoPlayNext.collectAsStateWithLifecycle()
            val precacheNext by vm.precacheNext.collectAsStateWithLifecycle()
            val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
            SettingsGroup("Playback") {
                SwitchRow("Auto-skip intro/outro", "Skip automatically when a marker is reached", autoSkip, vm::setAutoSkip)
                SwitchRow(
                    "Auto-play next episode", "Start the next episode automatically when one ends",
                    autoPlayNext, vm::setAutoPlayNext, divider = true,
                )
                // Only meaningful with auto-play on: it prepares the episode auto-play would start.
                SwitchRow(
                    "Pre-cache next episode", "Load the next episode near the end so it starts instantly",
                    precacheNext, vm::setPrecacheNext, enabled = autoPlayNext, divider = true,
                )
                SwitchRow(
                    "Download over WiFi only", "Pause downloads on mobile data; resume on WiFi",
                    wifiOnly, vm::setWifiOnly, divider = true,
                )
            }
        }

        item(key = "quality", contentType = "settings") {
            val downloadQuality by vm.downloadQuality.collectAsStateWithLifecycle()
            Column(ContentWidth, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GroupLabel("Default download quality", "Used when a source offers several qualities.")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadQuality.entries.forEach { q ->
                        FilterChip(
                            selected = downloadQuality == q,
                            onClick = { vm.setDownloadQuality(q) },
                            label = { Text(q.label) },
                        )
                    }
                }
            }
        }

        item(key = "subtitles", contentType = "settings") {
            val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
            val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()
            var sliderScale by remember(subtitleScale) { mutableStateOf(subtitleScale) }
            SettingsGroup("Subtitles") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    // Live preview — reflects the in-progress slider value and the background toggle so
                    // the effect is visible before leaving the screen, drawn by the player's renderer.
                    SubtitlePreview(scale = sliderScale, background = subtitleBackground)
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", style = AppTheme.type.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${(sliderScale * 100).roundToInt()}%",
                            style = AppTheme.type.bodyLarge.merge(TabularNumbers),
                            color = AppTheme.colors.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = sliderScale,
                        onValueChange = { sliderScale = it },
                        onValueChangeFinished = { vm.setSubtitleScale(sliderScale) },
                        valueRange = 0.6f..2.0f,
                        // 0.6→2.0 in 0.05 (5%) increments: 28 intervals ⇒ 27 inner steps.
                        steps = 27,
                    )
                }
                SwitchRow("Subtitle background", null, subtitleBackground, vm::setSubtitleBackground, divider = true)
            }
        }

        item(key = "mal", contentType = "settings") {
            var malExpanded by rememberSaveable { mutableStateOf(false) }
            val malUsername by vm.malUsername.collectAsStateWithLifecycle()
            val malSyncEnabled by vm.malSyncEnabled.collectAsStateWithLifecycle()
            val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
            val syncing by vm.syncing.collectAsStateWithLifecycle()
            SettingsGroup("MyAnimeList sync") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { malExpanded = !malExpanded }
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mirror your public MAL list", style = AppTheme.type.bodyLarge)
                        Text(
                            if (malUsername.isBlank()) "Into My List — no account or API key" else "As $malUsername",
                            style = AppTheme.type.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (malExpanded) AppIcons.Filled.ExpandLess else AppIcons.Filled.ExpandMore,
                        if (malExpanded) "Collapse" else "Expand",
                        tint = AppTheme.colors.onSurfaceVariant,
                    )
                }
                if (malExpanded) {
                    var usernameField by rememberSaveable(malUsername) { mutableStateOf(malUsername) }
                    RowDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextInput(
                            value = usernameField,
                            onValueChange = { usernameField = it },
                            label = "MAL username",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.setMalUsername(usernameField.trim()) }) { Text("Save") }
                            TonalButton(enabled = !syncing, onClick = { vm.syncMalNow(usernameField.trim()) }) {
                                Text("Sync now")
                            }
                        }
                        syncStatus?.let {
                            Text(it, style = AppTheme.type.bodySmall, color = AppTheme.colors.onSurfaceVariant)
                        }
                        Text(
                            "Enter your MAL username to mirror your PUBLIC list (read-only) — no API key " +
                                "needed. Make sure your list privacy is Public on MAL, then filter it on the My List tab.",
                            style = AppTheme.type.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant,
                        )
                    }
                    SwitchRow("Active sync (on app open)", null, malSyncEnabled, vm::setMalSyncEnabled, divider = true)
                }
            }
        }
    }
}

/** Settings read best at a column width; on tablets, full-bleed 2900px rows are hard to scan. */
private val ContentWidth = Modifier.widthIn(max = 840.dp).fillMaxWidth()

private val TabularNumbers = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/** A group's small uppercase label, with an optional muted line under it. */
@Composable
private fun GroupLabel(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title.uppercase(),
            style = AppTheme.type.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            color = AppTheme.colors.onSurfaceVariant,
        )
        if (subtitle != null) {
            Text(subtitle, style = AppTheme.type.bodySmall, color = AppTheme.colors.onSurfaceVariant)
        }
    }
}

/** A labelled group: [GroupLabel] over a rounded card whose rows are separated by hairlines. */
@Composable
private fun SettingsGroup(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(ContentWidth, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel(title, subtitle)
        Surface(shape = RoundedCornerShape(12.dp), color = AppTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

/** The hairline between rows of a group, inset from the start like the rows' text. */
@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

/** A title + optional detail row with a trailing [Switch]; the whole row toggles. */
@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    divider: Boolean = false,
) {
    if (divider) RowDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp).alpha(if (enabled) 1f else 0.38f)) {
            Text(title, style = AppTheme.type.bodyLarge)
            if (detail != null) {
                Text(detail, style = AppTheme.type.bodySmall, color = AppTheme.colors.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A source in the picker: radio, name and detail, an optional settings gear; tinted when selected. */
@Composable
private fun SourceRow(
    name: String,
    detail: String,
    selected: Boolean,
    divider: Boolean,
    onSelect: () -> Unit,
    onConfigure: (() -> Unit)? = null,
) {
    if (divider) RowDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) AppTheme.colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(name, style = AppTheme.type.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else null)
            Text(detail, style = AppTheme.type.bodySmall, color = AppTheme.colors.onSurfaceVariant)
        }
        if (onConfigure != null) {
            IconButton(onClick = onConfigure) {
                Icon(AppIcons.Filled.Settings, contentDescription = "Source settings", tint = AppTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * A scaled-down video frame that previews a sample caption at the SAME PHYSICAL SIZE it will render
 * on the player. The player sizes captions as [CAPTION_TEXT_SIZE_FRACTION] × [scale] of the video
 * frame's height, which in fullscreen is about the screen's height. This thumbnail is far shorter,
 * so sizing the caption as a fraction of THIS frame would be proportionally right but physically tiny;
 * instead the caption's absolute pixel size is computed from the real screen height and pinned, so the
 * preview text is the size the user will see during playback. It's drawn by the same [Captions]
 * renderer the player uses, so the styling (white text, black outline, optional box) is identical.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitlePreview(scale: Float, background: Boolean) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val captionPx = CAPTION_TEXT_SIZE_FRACTION * scale * screenHeightPx
    val sample = remember { listOf(Cue.Builder().setText("Sample subtitle text").build()) }
    // Centered, capped-width 16:9 frame standing in for the player surface. Capped so it stays a
    // thumbnail on wide/landscape screens, but wide enough that a player-sized caption has room
    // instead of wrapping unrealistically in a narrow box.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF455A64), Color(0xFF1C242A), Color(0xFF000000)),
                    )
                ),
        ) {
            Captions(sample, CaptionStyle(background = background, textSizePx = captionPx), Modifier.fillMaxSize())
        }
    }
}
