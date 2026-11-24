package com.anilocal.app.ui.player

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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

// AniLab player HUD constants (from the reference app's control skin). Hoisted so the 300ms
// position tick doesn't rebuild them every recomposition.
private val TimerColor = Color(0xFFBEBEBE)
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val OverlayMask = Color(0x99121318)
private val DimColor = Color.Black.copy(alpha = 0.45f)
private val DiscColor = Color.Black.copy(alpha = 0.35f)

/**
 * One-shot double-tap seek feedback. Deliberately NOT a data class: each instance has distinct
 * identity, so `LaunchedEffect(seekFlash)` re-triggers its fade on every rapid double-tap.
 */
private class SeekFlash(val forward: Boolean)

@OptIn(UnstableApi::class)
private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val currentPlayer by vm.player.collectAsStateWithLifecycle()
    val recoveryState by vm.recoveryState.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val buffered by vm.bufferedPosition.collectAsStateWithLifecycle()
    val duration by vm.duration.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val playWhenReady by vm.playWhenReady.collectAsStateWithLifecycle()
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
    val info by vm.info.collectAsStateWithLifecycle()

    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    // Playback opens fullscreen (landscape, immersive). Leaving fullscreen drops to the portrait
    // watch page — video on top, title details + episode list below — rather than leaving the player.
    var fullscreen by rememberSaveable { mutableStateOf(true) }
    // Tablets keep the watch page in landscape (side-by-side); phones drop to portrait.
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    var locked by remember { mutableStateOf(false) }
    var resizeIndex by remember { mutableIntStateOf(0) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var ccMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    val anyMenuOpen = ccMenuOpen || speedMenuOpen || episodesOpen

    var resizeLabel by remember { mutableStateOf<String?>(null) }
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var unlockVisible by remember { mutableStateOf(true) }
    // Read inside the pointerInput(Unit) block so a lock/unlock doesn't restart the tap detector.
    val isLocked by rememberUpdatedState(locked)

    // Lock mode swallows back (shows the unlock chip instead); the episode panel closes on back;
    // fullscreen backs out to the watch page, and only the watch page leaves the player.
    val exitFullscreen = { fullscreen = false; interactionTick++ }
    BackHandler {
        when {
            episodesOpen -> episodesOpen = false
            locked -> unlockVisible = true
            fullscreen -> exitFullscreen()
            else -> onBack()
        }
    }

    // Fullscreen = landscape + hidden system bars; the watch page = portrait with the bars back.
    // Fullscreen-only chrome (lock, the in-player episode panel) is dropped on the way out.
    LaunchedEffect(fullscreen, isTablet) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            episodesOpen = false
            locked = false
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation =
                if (isTablet) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Keep-screen-on + cutout mode, scoped to this screen's composition. All restore logic (bars,
    // orientation) lives in the single onDispose so no path leaves the bars hidden /
    // orientation locked. The Activity isn't recreated on rotation
    // (configChanges), so these stay applied.
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    LaunchedEffect(resizeLabel) { if (resizeLabel != null) { delay(900); resizeLabel = null } }
    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(650); seekFlash = null } }
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

    // One tree for every mode: the video Box is always the first child and only its size/offset
    // changes, so the PlayerView/SurfaceView is never torn down when toggling fullscreen. Out of
    // fullscreen a phone stacks the video over the details+episodes list (portrait); a tablet stays
    // landscape with video+details on the left and the episode list on the right.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background)
            .then(
                if (fullscreen) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ),
    ) {
    val tabletWatch = isTablet && !fullscreen
    // Tablet watch page: the video and the episode card float inside a margin with a gutter between.
    val videoWidth =
        if (isTablet) (maxWidth - TABLET_MARGIN * 2 - TABLET_GUTTER) * TABLET_VIDEO_FRACTION else maxWidth
    val videoHeight = videoWidth * 9f / 16f
    val videoTop = statusTop + if (isTablet) TABLET_TOP_GAP else 0.dp
    val videoStart = if (isTablet) TABLET_MARGIN else 0.dp
    // Its own group, so toggling it never shifts the video Box's identity below.
    if (tabletWatch) AmbientBackdrop(info?.let { it.bannerUrl ?: it.posterHdUrl ?: it.posterUrl }, Modifier.fillMaxSize())
    Box(
        (if (fullscreen) Modifier.fillMaxSize()
        else Modifier.padding(start = videoStart, top = videoTop).size(videoWidth, videoHeight))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = currentPlayer
                    useController = false                       // we draw our own Compose controls
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                }
            },
            update = { v ->
                if (v.player !== currentPlayer) v.player = currentPlayer
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
            onReset = null,
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // Tap layer — sits UNDER the control overlay; the overlay's buttons/seek bar consume their
        // own touches, so a tap anywhere else (including the scrims) lands here. A single tap
        // toggles the controls; a double-tap on the left/right third seeks 10s back/forward (the
        // middle third ignores it). No other gestures: everything else is a button.
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
                                else -> return@onDoubleTap
                            }
                            interactionTick++
                        },
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
                isPlaying = playWhenReady,
                isBuffering = isBuffering && error == null,
                // A failed load has nothing to transport; the error card takes the centre instead.
                showTransport = error == null,
                position = position,
                buffered = buffered,
                duration = duration,
                markers = markers,
                hasPrev = hasPrev,
                hasNext = hasNext,
                hasEpisodeList = episodes.isNotEmpty(),
                textTracks = textTracks,
                textDisabled = textDisabled,
                fullscreen = fullscreen,
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
                onBack = { if (fullscreen) exitFullscreen() else onBack() },
                onToggleFullscreen = { fullscreen = !fullscreen; interactionTick++ },
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

        // Double-tap seek flash on the tapped side. The watch page's smaller video gets an icon-only
        // disc hugging the edge so it clears the centred transport row.
        seekFlash?.let { flash ->
            Surface(
                shape = CircleShape,
                color = ScrimColor,
                modifier = Modifier
                    .align(if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = if (fullscreen) 42.dp else 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = if (fullscreen) 14.dp else 8.dp, vertical = if (fullscreen) 10.dp else 8.dp),
                ) {
                    Icon(
                        if (flash.forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                        null, tint = Color.White, modifier = Modifier.size(if (fullscreen) 26.dp else 22.dp),
                    )
                    if (fullscreen) Text("10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Resize-mode name flash.
        resizeLabel?.let { label ->
            CenterChip(label, Modifier.align(Alignment.TopCenter).padding(top = if (fullscreen) 64.dp else 12.dp))
        }

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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (fullscreen) 112.dp else 60.dp, end = if (fullscreen) 24.dp else 12.dp),
            )
        }

        // A failed load/resolve/playback shows its reason here rather than leaving a black screen.
        error?.let { message ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(if (fullscreen) 24.dp else 12.dp)
                    .widthIn(max = 420.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = if (fullscreen) 18.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (fullscreen) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "Can't play this episode",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (recoveryState == PlaybackRecoveryState.Failed) {
                        Surface(
                            onClick = vm::retryPlayback,
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                "Retry",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
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
    if (!fullscreen) {
        val content = WatchContent(title, info, episodes, currentEpisode, vm::jumpTo)
        if (isTablet) {
            // Info column aligned to the video's edges (its items carry their own 16dp inset).
            WatchInfoPane(
                content,
                Modifier
                    .padding(start = videoStart - 16.dp, top = videoTop + videoHeight + 4.dp)
                    .width(videoWidth + 32.dp)
                    .fillMaxHeight(),
            )
            WatchEpisodesPane(
                content,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = videoTop, end = TABLET_MARGIN)
                    .navigationBarsPadding()
                    .padding(bottom = TABLET_TOP_GAP)
                    .width(maxWidth - TABLET_MARGIN * 2 - TABLET_GUTTER - videoWidth)
                    .fillMaxHeight(),
            )
        } else {
            WatchPanel(content, Modifier.padding(top = statusTop + videoHeight).fillMaxSize())
        }
    }
    }
}

/** Tablet watch page: the video's share of the content width (the episode card takes the rest). */
private const val TABLET_VIDEO_FRACTION = 0.66f
private val TABLET_MARGIN = 24.dp
private val TABLET_GUTTER = 20.dp
private val TABLET_TOP_GAP = 16.dp

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

@Composable
private fun PlayerControls(
    title: String,
    episodeNumber: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    showTransport: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    markers: List<SkipMarker>,
    hasPrev: Boolean,
    hasNext: Boolean,
    hasEpisodeList: Boolean,
    textTracks: List<PlayerTextTrack>,
    textDisabled: Boolean,
    fullscreen: Boolean,
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
    // YouTube-style layout: an even dim over the whole video (purely decorative, so touches fall
    // through to the tap layer), back + title top-left with the tool icons top-right, the transport
    // centred, and the time + fullscreen toggle above a full-width seek bar at the bottom. The
    // watch page's 16:9 video gets the same layout at a smaller size, minus lock/episodes/title.
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(DimColor))

        // Fullscreen controls stay inside the safe area so nothing sits under a cutout / gesture
        // pill. The watch-page video already sits below the status bar, and the nav-bar inset
        // belongs to the list under it, not to the video.
        Box(Modifier.fillMaxSize().then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)) {
            val toolIcon = if (fullscreen) 24.dp else 22.dp
            // Top: back · title + episode line · lock · speed · CC · episodes · resize
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = if (fullscreen) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(Icons.Filled.KeyboardArrowDown, "Back", onBack, iconSize = if (fullscreen) 32.dp else 28.dp)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    // The watch page shows the title and episode right under the video.
                    if (fullscreen) {
                        Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Episode $episodeNumber",
                            color = TimerColor,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (fullscreen) ControlIcon(Icons.Filled.LockOpen, "Lock controls", onLock, iconSize = toolIcon)
                Box {
                    ControlIcon(Icons.Filled.Speed, "Playback speed", { onSpeedMenuOpenChange(true) }, iconSize = toolIcon)
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
                        ControlIcon(Icons.Filled.ClosedCaption, "Subtitles", { onCcMenuOpenChange(true) }, iconSize = toolIcon)
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
                if (hasEpisodeList && fullscreen) {
                    ControlIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "Episodes", onOpenEpisodes, iconSize = toolIcon)
                }
                ControlIcon(Icons.Filled.AspectRatio, "Resize mode", onCycleResize, iconSize = toolIcon)
            }

            // Center: previous · rewind 10s · play/pause · forward 10s · next, each on a soft dark
            // disc. While rebuffering the screen-level spinner takes the play button's spot.
            if (showTransport) Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 32.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val side = if (fullscreen) 52.dp else 40.dp
                val sideIcon = if (fullscreen) 32.dp else 24.dp
                CenterButton(Icons.Filled.SkipPrevious, "Previous episode", onPrev, side, sideIcon, enabled = hasPrev)
                CenterButton(Icons.Filled.Replay10, "Rewind 10s", onRewind, side, sideIcon)
                CenterButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    onPlayPause,
                    size = if (fullscreen) 72.dp else 52.dp,
                    iconSize = if (fullscreen) 48.dp else 34.dp,
                    showIcon = !isBuffering,
                    enabled = !isBuffering,
                    accent = !isBuffering,
                )
                CenterButton(Icons.Filled.Forward10, "Forward 10s", onForward, side, sideIcon)
                CenterButton(Icons.Filled.SkipNext, "Next episode", onNext, side, sideIcon, enabled = hasNext)
            }

            // Bottom: "position / duration" left and the fullscreen toggle right, over a full-width
            // seek bar.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = if (fullscreen) 16.dp else 8.dp)
                    .padding(bottom = if (fullscreen) 8.dp else 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmtTime(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        "  /  ${fmtTime(duration)}",
                        color = TimerColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    ControlIcon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        if (fullscreen) "Exit fullscreen" else "Fullscreen",
                        onToggleFullscreen,
                        iconSize = if (fullscreen) 28.dp else 24.dp,
                    )
                }
                SeekBar(
                    positionMs = position,
                    bufferedMs = buffered,
                    durationMs = duration,
                    markers = markers,
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                    onScrubCancel = onScrubCancel,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A transport button on a translucent dark disc (the centred YouTube-style controls). */
@Composable
private fun CenterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    showIcon: Boolean = true,
    // The primary action (play/pause) wears the app's accent, like the watch page's play buttons.
    accent: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .background(if (accent) MaterialTheme.colorScheme.primary else DiscColor, CircleShape),
    ) {
        if (showIcon) {
            Icon(
                icon,
                desc,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(iconSize),
            )
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
