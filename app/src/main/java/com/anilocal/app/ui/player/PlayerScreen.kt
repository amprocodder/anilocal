package com.anilocal.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.SkipMarker
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// AniLab player HUD constants (from the reference app's control skin). Hoisted so the 300ms
// position tick doesn't rebuild them every recomposition.
private val TimerColor = Color(0xFFBEBEBE)
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val OverlayMask = Color(0x99121318)
private val TopScrimBrush =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
private val BottomScrimBrush =
    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))

/**
 * One-shot double-tap seek feedback. Deliberately NOT a data class: each instance has distinct
 * identity, so `LaunchedEffect(seekFlash)` re-triggers its fade on every rapid double-tap.
 */
private class SeekFlash(val forward: Boolean)

/** Transient brightness/volume HUD state while a vertical drag is in progress. */
private data class GestureHud(val isVolume: Boolean, val fraction: Float)

private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val position by vm.position.collectAsStateWithLifecycle()
    val buffered by vm.bufferedPosition.collectAsStateWithLifecycle()
    val duration by vm.duration.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by vm.isBuffering.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val currentEpisode by vm.currentEpisode.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val hasPrev by vm.hasPrev.collectAsStateWithLifecycle()
    val hasNext by vm.hasNext.collectAsStateWithLifecycle()
    val textTracks by vm.textTracks.collectAsStateWithLifecycle()
    val textDisabled by vm.textDisabled.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()

    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    val audio = remember(view) {
        view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var landscape by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var resizeIndex by remember { mutableIntStateOf(0) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var ccMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    val anyMenuOpen = ccMenuOpen || speedMenuOpen || episodesOpen

    // Gesture feedback overlays.
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    var hudDragging by remember { mutableStateOf(false) }
    var resizeLabel by remember { mutableStateOf<String?>(null) }
    var boosting by remember { mutableStateOf(false) }
    var preBoostSpeed by remember { mutableFloatStateOf(1f) }
    var unlockVisible by remember { mutableStateOf(true) }
    // Read inside pointerInput(Unit) blocks so a lock/unlock mid-gesture can't restart the gesture
    // coroutine (which would strand a long-press 2× boost with no release handler).
    val isLocked by rememberUpdatedState(locked)

    // Lock mode swallows back (shows the unlock chip instead); the episode panel closes on back.
    BackHandler {
        when {
            episodesOpen -> episodesOpen = false
            locked -> unlockVisible = true
            else -> onBack()
        }
    }

    // Immersive fullscreen + keep-screen-on + orientation, scoped to this screen's composition.
    // All restore logic lives in the single onDispose so no path leaves the bars hidden / orientation
    // locked / brightness overridden. The Activity isn't recreated on rotation (configChanges), so
    // this effect stays applied.
    DisposableEffect(Unit) {
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
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(650); seekFlash = null } }
    // Hide the HUD shortly after a drag ends; a new drag re-keys the effect, cancelling the timer.
    LaunchedEffect(hudDragging) { if (!hudDragging && hud != null) { delay(600); hud = null } }
    LaunchedEffect(resizeLabel) { if (resizeLabel != null) { delay(900); resizeLabel = null } }
    LaunchedEffect(locked, unlockVisible) {
        if (locked && unlockVisible) { delay(2_500); unlockVisible = false }
    }

    // Auto-hide the controls while playing and not scrubbing; any interaction bumps the tick to reset
    // the countdown. Pausing, scrubbing, or an open menu/panel keeps them up.
    LaunchedEffect(controlsVisible, isPlaying, scrubbing, anyMenuOpen, interactionTick) {
        if (controlsVisible && isPlaying && !scrubbing && !anyMenuOpen) {
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
                // update re-runs on unrelated recompositions (the 300ms position tick), so key the
                // real work on the values it depends on — SubtitleView.setStyle would otherwise
                // rebuild/invalidate the caption layer three times a second.
                val key = Triple(resizeIndex, subtitleScale, subtitleBackground)
                if (v.tag != key) {
                    v.tag = key
                    v.resizeMode = ResizeModes[resizeIndex].first
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
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture layer — sits UNDER the control overlay; the overlay's buttons/seek bar consume
        // their own touches, so anything else (including taps on the scrims) lands here. Single tap
        // toggles the controls; double-tap seeks (left/right third) or play/pauses (center);
        // long-press holds 2× speed; vertical drags adjust brightness (left half) / volume (right).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) {
                                unlockVisible = !unlockVisible
                            } else {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) interactionTick++
                            }
                        },
                        onDoubleTap = onDoubleTap@{ o ->
                            if (isLocked) return@onDoubleTap
                            val third = size.width / 3f
                            when {
                                o.x < third -> {
                                    vm.seekBack()
                                    seekFlash = SeekFlash(forward = false)
                                }
                                o.x > 2 * third -> {
                                    vm.seekForward()
                                    seekFlash = SeekFlash(forward = true)
                                }
                                else -> vm.togglePlay()
                            }
                            interactionTick++
                        },
                        onLongPress = {
                            if (!isLocked && !boosting) {
                                preBoostSpeed = vm.speed.value
                                boosting = true
                                vm.setSpeed(2f)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (boosting) {
                                boosting = false
                                vm.setSpeed(preBoostSpeed)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var active = false
                    var isVolume = false
                    var fraction = 0f
                    var maxVolume = 1
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            active = !isLocked
                            if (!active) return@detectVerticalDragGestures
                            isVolume = o.x > size.width / 2f
                            maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            fraction = if (isVolume) {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                            } else {
                                currentBrightness(activity)
                            }
                            hud = GestureHud(isVolume, fraction)
                            hudDragging = true
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!active) return@detectVerticalDragGestures
                            change.consume()
                            // Full swipe over ~80% of the screen height = full range.
                            fraction = (fraction - dragAmount / (size.height * 0.8f)).coerceIn(0f, 1f)
                            if (isVolume) {
                                // Silent-degrade: DND "total silence" makes setStreamVolume throw
                                // SecurityException on many OEM builds — a crash mid-playback for a
                                // volume swipe is never the right trade.
                                runCatching {
                                    audio.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (fraction * maxVolume).roundToInt(),
                                        0,
                                    )
                                }
                            } else {
                                activity.window.attributes = activity.window.attributes.apply {
                                    screenBrightness = fraction.coerceAtLeast(0.01f)
                                }
                            }
                            hud = GestureHud(isVolume, fraction)
                        },
                        onDragEnd = { hudDragging = false },
                        onDragCancel = { hudDragging = false },
                    )
                }
        )

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                title = title,
                episodeNumber = currentEpisode,
                isPlaying = isPlaying,
                position = position,
                buffered = buffered,
                duration = duration,
                markers = markers,
                hasPrev = hasPrev,
                hasNext = hasNext,
                hasEpisodeList = episodes.isNotEmpty(),
                textTracks = textTracks,
                textDisabled = textDisabled,
                landscape = landscape,
                speed = speed,
                ccMenuOpen = ccMenuOpen,
                speedMenuOpen = speedMenuOpen,
                onCcMenuOpenChange = { ccMenuOpen = it; interactionTick++ },
                onSpeedMenuOpenChange = { speedMenuOpen = it; interactionTick++ },
                onSelectSpeed = { vm.setSpeed(it); interactionTick++ },
                onOpenEpisodes = { episodesOpen = true; interactionTick++ },
                onCycleResize = {
                    resizeIndex = (resizeIndex + 1) % ResizeModes.size
                    resizeLabel = ResizeModes[resizeIndex].second
                    interactionTick++
                },
                onLock = { locked = true; unlockVisible = true; interactionTick++ },
                onBack = onBack,
                onToggleFullscreen = {
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

        // Center rebuffer spinner — visible regardless of the control overlay (AniLab-style).
        if (isBuffering && error == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(46.dp),
            )
        }

        // Double-tap seek flash (ripple circle + label at the tapped side's center).
        seekFlash?.let { flash ->
            Surface(
                shape = CircleShape,
                color = ScrimColor,
                modifier = Modifier
                    .align(if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 42.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        if (flash.forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                        null, tint = Color.White, modifier = Modifier.size(26.dp),
                    )
                    Text("10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Brightness / volume HUD while dragging.
        hud?.let { h ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ScrimColor,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        when {
                            !h.isVolume -> Icons.Filled.BrightnessMedium
                            h.fraction <= 0.001f -> Icons.Filled.VolumeOff
                            else -> Icons.Filled.VolumeUp
                        },
                        null, tint = Color.White, modifier = Modifier.size(22.dp),
                    )
                    LinearProgressIndicator(
                        progress = { h.fraction },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }

        // Resize-mode name flash / long-press 2× chip.
        resizeLabel?.let { label -> CenterChip(label, Modifier.align(Alignment.Center)) }
        if (boosting) CenterChip("2× speed", Modifier.align(Alignment.TopCenter).padding(top = 48.dp))

        // Lock mode: everything is hidden; a tap shows this lone unlock button (AniLab-style).
        if (locked) {
            AnimatedVisibility(
                visible = unlockVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ScrimColor,
                    modifier = Modifier.clickable {
                        locked = false
                        controlsVisible = true
                        interactionTick++
                    },
                ) {
                    Icon(
                        Icons.Filled.Lock, "Unlock controls",
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).size(24.dp),
                    )
                }
            }
        }

        // The Skip Intro/Outro pill is independent of the auto-hiding overlay — it shows whenever
        // the playhead is inside a skip window, floated above the bottom control area. Locked mode
        // hides it too: the lock's contract is "no stray tap does anything".
        if (!locked) {
            SkipButton(
                positionMs = position,
                markers = markers,
                onSkip = vm::seekPast,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 112.dp, end = 24.dp),
            )
        }

        // A failed load/resolve/playback shows its reason here rather than leaving a black screen.
        error?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ScrimColor,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Text(
                    message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // In-player episode picker: an in-window overlay panel (NOT a ModalBottomSheet — a dialog
        // window would un-hide the system bars and break the immersive player; AniLab's picker is
        // an in-player overlay for the same reason). Scrim tap or back closes it.
        AnimatedVisibility(
            visible = episodesOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OverlayMask)
                    .pointerInput(Unit) { detectTapGestures { episodesOpen = false } },
            )
        }
        AnimatedVisibility(
            visible = episodesOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            EpisodePanel(
                episodes = episodes,
                currentEpisode = currentEpisode,
                onPick = { number ->
                    episodesOpen = false
                    vm.jumpTo(number)
                },
            )
        }
    }
}

/** Right-side episode list panel shown inside the player window (keeps immersive mode intact). */
@Composable
private fun EpisodePanel(
    episodes: List<Episode>,
    currentEpisode: Int,
    onPick: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex =
            episodes.indexOfFirst { it.number == currentEpisode }.coerceAtLeast(0),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxHeight().width(300.dp),
    ) {
        Column(Modifier.safeDrawingPadding()) {
            Text(
                "Episodes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(state = listState) {
                items(episodes, key = { it.id }) { ep ->
                    EpisodeSheetRow(
                        episode = ep,
                        current = ep.number == currentEpisode,
                        onClick = { onPick(ep.number) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeSheetRow(episode: Episode, current: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(Modifier.size(width = 44.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${episode.number}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (current) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            episode.title ?: "Episode ${episode.number}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (current) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (current) {
            Icon(
                Icons.Filled.PlayArrow, "Playing",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CenterChip(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(100.dp), color = ScrimColor, modifier = modifier) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

private val SpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float) = if (s == 1f) "Normal" else "${"%.2f".format(s).trimEnd('0').trimEnd('.')}x"

/**
 * The window override wins when set; otherwise fall back to the system brightness so the first
 * drag adjusts from where the screen actually is instead of snapping to an arbitrary midpoint.
 */
private fun currentBrightness(activity: Activity): Float {
    val override = activity.window.attributes.screenBrightness
    if (override >= 0f) return override
    return runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0f, 1f)
}

@Composable
private fun PlayerControls(
    title: String,
    episodeNumber: Int,
    isPlaying: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    markers: List<SkipMarker>,
    hasPrev: Boolean,
    hasNext: Boolean,
    hasEpisodeList: Boolean,
    textTracks: List<PlayerTextTrack>,
    textDisabled: Boolean,
    landscape: Boolean,
    speed: Float,
    ccMenuOpen: Boolean,
    speedMenuOpen: Boolean,
    onCcMenuOpenChange: (Boolean) -> Unit,
    onSpeedMenuOpenChange: (Boolean) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onOpenEpisodes: () -> Unit,
    onCycleResize: () -> Unit,
    onLock: () -> Unit,
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
    // Gradient scrim bands top and bottom (AniLab-style) — purely decorative, so touches on them
    // fall through to the gesture layer underneath.
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(130.dp)
                .background(TopScrimBrush)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(190.dp)
                .background(BottomScrimBrush)
        )

        // Controls stay inside the safe area so nothing sits under a cutout / gesture pill.
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Top bar: back · series title + episode line · speed · CC · episodes · resize
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
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Episode $episodeNumber",
                        color = TimerColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Box {
                    IconButton(onClick = { onSpeedMenuOpenChange(true) }) {
                        Icon(Icons.Filled.Speed, "Playback speed", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = speedMenuOpen,
                        onDismissRequest = { onSpeedMenuOpenChange(false) },
                    ) {
                        SpeedOptions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(s)) },
                                onClick = { onSelectSpeed(s); onSpeedMenuOpenChange(false) },
                                leadingIcon = { if (s == speed) Icon(Icons.Filled.Check, null) },
                            )
                        }
                    }
                }
                if (textTracks.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { onCcMenuOpenChange(true) }) {
                            Icon(Icons.Filled.ClosedCaption, "Subtitles", tint = Color.White)
                        }
                        DropdownMenu(expanded = ccMenuOpen, onDismissRequest = { onCcMenuOpenChange(false) }) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = { onDisableText(); onCcMenuOpenChange(false) },
                                leadingIcon = { if (textDisabled) Icon(Icons.Filled.Check, null) },
                            )
                            textTracks.forEachIndexed { i, t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    onClick = { onSelectText(i); onCcMenuOpenChange(false) },
                                    leadingIcon = { if (!textDisabled && t.selected) Icon(Icons.Filled.Check, null) },
                                )
                            }
                        }
                    }
                }
                if (hasEpisodeList) {
                    IconButton(onClick = onOpenEpisodes) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Episodes", tint = Color.White)
                    }
                }
                IconButton(onClick = onCycleResize) {
                    Icon(Icons.Filled.AspectRatio, "Resize mode", tint = Color.White)
                }
            }

            // Bottom: seek bar flanked by the timers, then the AniLab control row.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmtTime(position),
                        color = TimerColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    SeekBar(
                        positionMs = position,
                        bufferedMs = buffered,
                        durationMs = duration,
                        markers = markers,
                        onScrub = onScrub,
                        onScrubFinished = onScrubFinished,
                        onScrubCancel = onScrubCancel,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(
                        fmtTime(duration),
                        color = TimerColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(Icons.Filled.LockOpen, "Lock controls", onLock, iconSize = 22.dp)
                    ControlIcon(Icons.Filled.Replay10, "Rewind 10s", onRewind)
                    ControlIcon(Icons.Filled.SkipPrevious, "Previous episode", onPrev, enabled = hasPrev)
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    ControlIcon(Icons.Filled.SkipNext, "Next episode", onNext, enabled = hasNext)
                    ControlIcon(Icons.Filled.Forward10, "Forward 10s", onForward)
                    ControlIcon(
                        if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        "Fullscreen",
                        onToggleFullscreen,
                    )
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
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            desc,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize),
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
