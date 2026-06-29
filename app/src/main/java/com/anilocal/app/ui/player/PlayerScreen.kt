package com.anilocal.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.anilocal.app.domain.model.SkipMarker
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val position by vm.position.collectAsStateWithLifecycle()
    val buffered by vm.bufferedPosition.collectAsStateWithLifecycle()
    val duration by vm.duration.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val hasPrev by vm.hasPrev.collectAsStateWithLifecycle()
    val hasNext by vm.hasNext.collectAsStateWithLifecycle()
    val textTracks by vm.textTracks.collectAsStateWithLifecycle()
    val textDisabled by vm.textDisabled.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()

    val view = LocalView.current
    var landscape by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    // Immersive fullscreen + keep-screen-on + orientation, scoped to this screen's composition.
    // All restore logic lives in the single onDispose so no path leaves the bars hidden / orientation
    // locked. The Activity isn't recreated on rotation (configChanges), so this effect stays applied.
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Auto-hide the controls while playing and not scrubbing; any interaction bumps the tick to reset
    // the countdown. Pausing, scrubbing, or an open menu (CC) keep them up.
    LaunchedEffect(controlsVisible, isPlaying, scrubbing, menuOpen, interactionTick) {
        if (controlsVisible && isPlaying && !scrubbing && !menuOpen) {
            delay(3_500)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.player
                    useController = false                       // we draw our own Compose controls
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                }
            },
            update = { v ->
                // Apply subtitle preferences live (re-runs when scale/background change).
                v.subtitleView?.let { sv ->
                    sv.setApplyEmbeddedStyles(false)
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                    val bg = if (subtitleBackground) android.graphics.Color.argb(160, 0, 0, 0)
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
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Tap anywhere to toggle the controls (this layer is behind the controls overlay, so it only
        // receives taps when the controls are hidden).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) interactionTick++
                    }
                }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                title = title,
                isPlaying = isPlaying,
                position = position,
                buffered = buffered,
                duration = duration,
                markers = markers,
                hasPrev = hasPrev,
                hasNext = hasNext,
                textTracks = textTracks,
                textDisabled = textDisabled,
                landscape = landscape,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it; interactionTick++ },
                onScrimTap = { controlsVisible = false },
                onBack = onBack,
                onToggleFullscreen = {
                    val activity = view.context.findActivity()
                    landscape = !landscape
                    activity.requestedOrientation =
                        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    interactionTick++
                },
                onPlayPause = { vm.togglePlay(); interactionTick++ },
                onRewind = { vm.seekBack(); interactionTick++ },
                onForward = { vm.seekForward(); interactionTick++ },
                onPrev = { vm.goPrev(); interactionTick++ },
                onNext = { vm.goNext(); interactionTick++ },
                onScrub = { scrubbing = true; interactionTick++ },
                onScrubFinished = { vm.seekTo(it); scrubbing = false; interactionTick++ },
                onScrubCancel = { scrubbing = false; interactionTick++ },
                onSelectText = { vm.selectTextTrack(it); interactionTick++ },
                onDisableText = { vm.disableTextTrack(); interactionTick++ },
            )
        }

        // The Skip Intro/Outro button is independent of the auto-hiding overlay — it shows whenever the
        // playhead is inside a skip window. Floated above the bottom control bar.
        SkipButton(
            positionMs = position,
            markers = markers,
            onSkip = vm::seekPast,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 88.dp, end = 24.dp),
        )

        // A failed load/resolve/playback shows its reason here rather than leaving a black screen.
        error?.let { message ->
            Text(
                message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    markers: List<SkipMarker>,
    hasPrev: Boolean,
    hasNext: Boolean,
    textTracks: List<PlayerTextTrack>,
    textDisabled: Boolean,
    landscape: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onScrimTap: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onScrubCancel: () -> Unit,
    onSelectText: (Int) -> Unit,
    onDisableText: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onScrimTap() } }
    ) {
      // Scrim covers the full screen, but the controls themselves stay inside the safe area so the
      // back button / seek bar don't sit under a camera cutout or the gesture-nav pill in fullscreen.
      Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Top bar: back · title · CC · fullscreen
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (textTracks.isNotEmpty()) {
                Box {
                    IconButton(onClick = { onMenuOpenChange(true) }) {
                        Icon(Icons.Filled.ClosedCaption, "Subtitles", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                        DropdownMenuItem(
                            text = { Text("Off") },
                            onClick = { onDisableText(); onMenuOpenChange(false) },
                            leadingIcon = { if (textDisabled) Icon(Icons.Filled.Check, null) },
                        )
                        textTracks.forEachIndexed { i, t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = { onSelectText(i); onMenuOpenChange(false) },
                                leadingIcon = { if (!textDisabled && t.selected) Icon(Icons.Filled.Check, null) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    "Fullscreen",
                    tint = Color.White,
                )
            }
        }

        // Center transport: prev · rewind · play/pause · forward · next
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(Icons.Filled.SkipPrevious, "Previous episode", onPrev, enabled = hasPrev)
            ControlIcon(Icons.Filled.Replay10, "Rewind 10s", onRewind)
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
            ControlIcon(Icons.Filled.Forward10, "Forward 10s", onForward)
            ControlIcon(Icons.Filled.SkipNext, "Next episode", onNext, enabled = hasNext)
        }

        // Bottom bar: seek bar with skip highlights + time labels
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            SeekBar(
                positionMs = position,
                bufferedMs = buffered,
                durationMs = duration,
                markers = markers,
                onScrub = onScrub,
                onScrubFinished = onScrubFinished,
                onScrubCancel = onScrubCancel,
                accent = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(fmtTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
      }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            desc,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity in context chain")
}
