package com.anilocal.app.ui.player

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anilocal.app.AppGraph
import com.anilocal.app.appViewModel
import com.anilocal.app.ui.navigation.Route
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.anilocal.app.MainActivity
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.SkipMarker
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.anilocal.app.ui.kit.CircularProgressIndicator
import com.anilocal.app.ui.kit.DropdownMenu
import com.anilocal.app.ui.kit.DropdownMenuItem
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme
import com.anilocal.app.ui.theme.Motion

// AniLab player HUD constants (from the reference app's control skin). Hoisted so the 300ms
// position tick doesn't rebuild them every recomposition.
private val TimerColor = Color(0xFFBEBEBE)
private val ClockStyle = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val OverlayMask = Color(0x99121318)
private val DimColor = Color.Black.copy(alpha = 0.45f)
private val DiscColor = Color.Black.copy(alpha = 0.35f)

/** The player for [route]; [startHidden] builds it off screen, ahead of being shown. */
internal fun playerViewModel(graph: AppGraph, route: Route.Player, startHidden: Boolean = false) = PlayerViewModel(
    graph.appContext, route.animeId, route.episode, route.startMs, graph.mediaCache, graph.catalog, graph.streams,
    graph.skip, graph.progress, graph.downloads, graph.appScope, graph.settings, startHidden,
)

/**
 * One-shot double-tap seek feedback. Deliberately NOT a data class: each instance has distinct
 * identity, so `LaunchedEffect(seekFlash)` re-triggers its fade on every rapid double-tap.
 */
private class SeekFlash(val forward: Boolean)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    route: Route.Player,
    onBack: () -> Unit,
    onEpisodeChanged: (Int) -> Unit,
    /** Swiping the fullscreen video down: shows Home beneath the player, revealed as far as `progress` reads (0–1). */
    onRevealHome: (progress: () -> Float) -> Unit,
    /** The swipe let go without leaving: Home is covered again. */
    onCancelReveal: () -> Unit,
    /** The swipe let go to leave, and the player is off screen: land on Home. */
    onGoHome: () -> Unit,
    /** False once the player is closing (it slides away), so Back goes to the screen beneath. */
    active: Boolean = true,
    vm: PlayerViewModel = appViewModel { playerViewModel(it, route) },
) {
    // On screen, a player kept warm (closed earlier, or built ahead for the resume bar) carries on
    // playing; closed, it's hidden — paused and kept, so resuming this episode continues it rather
    // than loading it again (see AppNavigation). Leaving composition while still the current screen
    // (the activity being recreated) is neither.
    val isActive by rememberUpdatedState(active)
    DisposableEffect(vm) {
        vm.show(route.startMs)
        onDispose { if (!isActive) vm.hide() }
    }

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
    // Auto-advance moves to the next episode in place; keep the route in step (for restore).
    LaunchedEffect(currentEpisode) { if (currentEpisode != route.episode) onEpisodeChanged(currentEpisode) }
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

    var flashLabel by remember { mutableStateOf<String?>(null) }
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var unlockVisible by remember { mutableStateOf(true) }
    // Read inside the pointerInput(Unit) block so a lock/unlock doesn't restart the tap detector.
    val isLocked by rememberUpdatedState(locked)

    // Swipe down to go Home (fullscreen only; see SwipeHomeState). As the drag takes hold, the
    // controls step aside and the system bars come back, so Home is laid out for the bars it will have
    // before it shows; let go short and both are as they were, once the player has sprung back.
    val swipe = remember { SwipeHomeState() }
    var controlsBeforeSwipe by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val revealHome by rememberUpdatedState(onRevealHome)
    val cancelReveal by rememberUpdatedState(onCancelReveal)
    val goHome by rememberUpdatedState(onGoHome)
    val swipeEvents = remember(swipe, activity, view, haptics) {
        object : SwipeHomeEvents {
            override fun onStart() {
                controlsBeforeSwipe = controlsVisible
                controlsVisible = false
                revealHome { swipe.progress }
                WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
            }

            override fun onArmed() = haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

            override fun onReturned() {
                cancelReveal()
                if (fullscreen) WindowCompat.getInsetsController(activity.window, view).hide(WindowInsetsCompat.Type.systemBars())
                if (controlsBeforeSwipe) {
                    controlsVisible = true
                    interactionTick++
                }
            }

            override fun onGone() = goHome()
        }
    }
    // Leaving composition mid-swipe (the activity going away) mustn't leave Home showing beneath.
    DisposableEffect(swipe) { onDispose { if (swipe.engaged) cancelReveal() } }

    // Lock mode swallows back (shows the unlock chip instead); the episode panel closes on back;
    // fullscreen backs out to the watch page, and only the watch page leaves the player.
    val exitFullscreen = { fullscreen = false; interactionTick++ }
    BackHandler(enabled = active) {
        when {
            swipe.engaged -> Unit   // being swiped away to Home: the swipe decides
            episodesOpen -> episodesOpen = false
            locked -> unlockVisible = true
            fullscreen -> exitFullscreen()
            else -> onBack()
        }
    }

    // 1 = fullscreen, 0 = the watch page. Toggling glides the video from its old place to its new
    // one (and fades the page in or out around it) as a transform of the old layout — nothing is
    // re-laid out per frame and the video never stops — and the layout switches to the new mode
    // once the video is there, where the transform is back to none, so the relayout comes after the
    // motion instead of in its first frame. On a phone the toggle also rotates the screen and the
    // system's rotation animation is the transition, so it just snaps.
    val modeProgress = remember { Animatable(if (fullscreen) 1f else 0f) }
    var layoutFullscreen by remember { mutableStateOf(fullscreen) }
    LaunchedEffect(fullscreen, isTablet) {
        val target = if (fullscreen) 1f else 0f
        if (isTablet) modeProgress.animateTo(target, tween(Motion.FULLSCREEN_MS, easing = Motion.Standard))
        else modeProgress.snapTo(target)
        layoutFullscreen = fullscreen
    }
    // The watch page stays composed while it fades out under the growing video.
    val watchShown by remember { derivedStateOf { !fullscreen || modeProgress.value < 1f } }
    val watchFade = Modifier.graphicsLayer { alpha = 1f - modeProgress.value }

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

    // A hardware keyboard (a tablet keyboard cover): Space toggles play like the overlay's button,
    // Left/Right seek 10s like a double-tap on that side (same flash; holding one repeats), F toggles
    // fullscreen, C toggles captions (a chip names the track, since the controls may be hidden), and
    // Enter presses the Skip Intro/Outro pill while it shows — Media3's session only hears media keys.
    // Hooked at the activity, ahead of focus routing, which would otherwise spend the arrows moving
    // focus between overlay buttons and have Space press the focused one (Back included). Enter is
    // taken only while there's something to skip (its release too), so outside a skip window it still
    // presses a button focused with Tab. Unmodified keys only, so shortcuts (Shift/Ctrl+Space language
    // switching) pass. Pausing brings the controls up to show where it stopped; any press restarts
    // their auto-hide. Deliberate keypresses still work in lock mode, which guards against touches —
    // except Enter, as lock mode hides the Skip pill.
    DisposableEffect(activity) {
        val host = activity as? MainActivity ?: return@DisposableEffect onDispose {}
        var enterTaken = false
        val onKey: (KeyEvent) -> Boolean = onKey@{ event ->
            if (!event.hasNoModifiers()) return@onKey false
            val down = event.action == KeyEvent.ACTION_DOWN
            val first = down && event.repeatCount == 0
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> if (first) {
                    vm.togglePlay()
                    if (!locked) {
                        if (!vm.playWhenReady.value) controlsVisible = true
                        interactionTick++
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) {
                    val forward = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    if (forward) vm.seekForward() else vm.seekBack()
                    seekFlash = SeekFlash(forward)
                    interactionTick++
                }
                KeyEvent.KEYCODE_F -> if (first && !swipe.engaged) {
                    fullscreen = !fullscreen
                    interactionTick++
                }
                KeyEvent.KEYCODE_C -> if (first) {
                    flashLabel = if (textTracks.isEmpty()) "No subtitles"
                        else vm.toggleCaptions()?.let { "Subtitles: $it" } ?: "Subtitles off"
                    interactionTick++
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (first) {
                        val skip = if (locked) null else activeSkip(markers, position)
                        enterTaken = skip != null
                        skip?.let(vm::seekPast)
                    }
                    val taken = enterTaken
                    if (!down) enterTaken = false
                    if (!taken) return@onKey false
                }
                else -> return@onKey false
            }
            true
        }
        host.keyInterceptor = onKey
        onDispose { if (host.keyInterceptor === onKey) host.keyInterceptor = null }
    }

    LaunchedEffect(flashLabel) { if (flashLabel != null) { delay(900); flashLabel = null } }
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
    // changes, so the video SurfaceView is never torn down when toggling fullscreen. Out of
    // fullscreen a phone stacks the video over the details+episodes list (portrait); a tablet stays
    // landscape with video+details on the left and the episode list on the right.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val watchBackground = AppTheme.colors.background
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onSizeChanged { swipe.playerSize = it.toSize() }
            // Over the whole player, so the drag is measured in untransformed px; the tap layer,
            // buttons and seek bar below still get their touches (a seek-bar drag cancels it).
            .swipeHome(
                swipe,
                enabled = {
                    isActive && fullscreen && layoutFullscreen && modeProgress.value >= 1f && !locked &&
                        !ccMenuOpen && !speedMenuOpen && !episodesOpen
                },
                events = swipeEvents,
            )
            // Behind the video; swiped down, it clears to show Home rising beneath.
            .drawBehind { drawRect(lerp(watchBackground, Color.Black, modeProgress.value), alpha = 1f - swipe.progress) }
            .then(
                if (layoutFullscreen) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ),
    ) {
    // Tablet watch page: the video and the episode card float inside a margin with a gutter between.
    val videoWidth =
        if (isTablet) (maxWidth - TABLET_MARGIN * 2 - TABLET_GUTTER) * TABLET_VIDEO_FRACTION else maxWidth
    val videoHeight = videoWidth * 9f / 16f
    val videoTop = statusTop + if (isTablet) TABLET_TOP_GAP else 0.dp
    val videoStart = if (isTablet) TABLET_MARGIN else 0.dp
    val glide = VideoGlide(
        box = with(density) {
            Rect(videoStart.toPx(), videoTop.toPx(), (videoStart + videoWidth).toPx(), (videoTop + videoHeight).toPx())
        },
        // The whole screen in this box's pixels: the watch page pads the box by the side insets.
        screen = if (layoutFullscreen) Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        else Rect(
            -sideInsets.getLeft(density, layoutDirection).toFloat(), 0f,
            (constraints.maxWidth + sideInsets.getRight(density, layoutDirection)).toFloat(),
            constraints.maxHeight.toFloat(),
        ),
        fullscreen = layoutFullscreen,
    )
    // Its own group, so toggling it never shifts the video Box's identity below.
    if (isTablet && watchShown) {
        AmbientBackdrop(info?.let { it.bannerUrl ?: it.posterHdUrl ?: it.posterUrl }, Modifier.fillMaxSize().then(watchFade))
    }
    Box(
        (if (layoutFullscreen) Modifier.fillMaxSize()
        else Modifier.padding(start = videoStart, top = videoTop).size(videoWidth, videoHeight))
            // Above the watch page, which the video overlaps only while it glides.
            .zIndex(1f)
            // Swiped (only ever in fullscreen, where the glide is at rest) the swipe places it instead.
            .graphicsLayer { if (swipe.engaged) swipe.applyTo(this) else glide.apply(this, modeProgress.value) }
            .background(Color.Black)
    ) {
        // The video surface (+ captions over the video frame). Same position in every mode, so the
        // SurfaceView is never recreated; player swaps move the surface to the new player.
        VideoSurface(
            player = currentPlayer,
            scale = VideoScale.entries[resizeIndex],
            captions = CaptionStyle(scale = subtitleScale, background = subtitleBackground),
            // On the watch page the video is laid out at its fullscreen size and scaled down into
            // the box, so toggling fullscreen never resizes its SurfaceView: a resize that leaves
            // its on-screen rect unchanged (the glide ends exactly there) shows one stale frame.
            modifier = Modifier.fillMaxSize().then(if (layoutFullscreen) Modifier else Modifier.laidOutAt(glide.frame.size)),
        )

        // Tap layer — sits UNDER the control overlay; the overlay's buttons/seek bar consume their
        // own touches, so a tap anywhere else (including the scrims) lands here. A single tap
        // toggles the controls; a double-tap on the left/right third seeks 10s back/forward (the
        // middle third ignores it). The only other gesture is the fullscreen swipe down to Home
        // (on the player's root, above); everything else is a button.
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
                fullscreen = layoutFullscreen,
                speed = speed,
                ccMenuOpen = ccMenuOpen,
                speedMenuOpen = speedMenuOpen,
                onCcMenuOpenChange = { ccMenuOpen = it; interactionTick++ },
                onSpeedMenuOpenChange = { speedMenuOpen = it; interactionTick++ },
                onSelectSpeed = { vm.setSpeed(it); interactionTick++ },
                onOpenEpisodes = { episodesOpen = true; interactionTick++ },
                onCycleResize = {
                    resizeIndex = (resizeIndex + 1) % VideoScale.entries.size
                    flashLabel = VideoScale.entries[resizeIndex].label
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
                    .padding(horizontal = if (layoutFullscreen) 42.dp else 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = if (layoutFullscreen) 14.dp else 8.dp, vertical = if (layoutFullscreen) 10.dp else 8.dp),
                ) {
                    Icon(
                        if (flash.forward) AppIcons.Filled.Forward10 else AppIcons.Filled.Replay10,
                        null, tint = Color.White, modifier = Modifier.size(if (layoutFullscreen) 26.dp else 22.dp),
                    )
                    if (layoutFullscreen) Text("10s", color = Color.White, style = AppTheme.type.labelLarge)
                }
            }
        }

        // Resize-mode name / caption-toggle flash.
        flashLabel?.let { label ->
            CenterChip(label, Modifier.align(Alignment.TopCenter).padding(top = if (layoutFullscreen) 64.dp else 12.dp))
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
                        AppIcons.Filled.Lock, "Unlock controls",
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
                // Steps aside with the controls while the player is being swiped away.
                markers = if (swipe.engaged) emptyList() else markers,
                onSkip = vm::seekPast,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (layoutFullscreen) 112.dp else 60.dp, end = if (layoutFullscreen) 24.dp else 12.dp),
            )
        }

        // A failed load/resolve/playback shows its reason here rather than leaving a black screen.
        error?.let { message ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppTheme.colors.surfaceContainerHigh.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, AppTheme.colors.outlineVariant),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(if (layoutFullscreen) 24.dp else 12.dp)
                    .widthIn(max = 420.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = if (layoutFullscreen) 18.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (layoutFullscreen) {
                        Icon(
                            AppIcons.Outlined.ErrorOutline, null,
                            tint = AppTheme.colors.tertiary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "Can't play this episode",
                            color = Color.White,
                            style = AppTheme.type.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        message,
                        color = AppTheme.colors.onSurfaceVariant,
                        style = AppTheme.type.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (recoveryState == PlaybackRecoveryState.Failed) {
                        Surface(
                            onClick = vm::retryPlayback,
                            shape = RoundedCornerShape(100.dp),
                            color = AppTheme.colors.primary,
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
    if (watchShown) {
        val content = WatchContent(title, info, episodes, currentEpisode, vm::jumpTo)
        if (isTablet) {
            // Info column aligned to the video's edges (its items carry their own 16dp inset).
            WatchInfoPane(
                content,
                Modifier
                    .padding(start = videoStart - 16.dp, top = videoTop + videoHeight + 4.dp)
                    .width(videoWidth + 32.dp)
                    .fillMaxHeight()
                    .then(watchFade),
            )
            WatchEpisodesPane(
                content,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = videoTop, end = TABLET_MARGIN)
                    .navigationBarsPadding()
                    .padding(bottom = TABLET_TOP_GAP)
                    .width(maxWidth - TABLET_MARGIN * 2 - TABLET_GUTTER - videoWidth)
                    .fillMaxHeight()
                    .then(watchFade),
            )
        } else {
            WatchPanel(content, Modifier.padding(top = statusTop + videoHeight).fillMaxSize().then(watchFade))
        }
    }
    }
}

/**
 * The video's glide between its two places, in the player box's pixels: [box] on the watch page,
 * and fullscreen the 16:9 frame centred on the [screen] (where a 16:9 video sits). The video box is
 * laid out for one mode ([fullscreen]) and transformed so that frame is drawn at the in-between
 * rect for progress p (0 = watch page, 1 = fullscreen) — a uniform scale + move, which the video's
 * SurfaceView follows exactly. Laid out fullscreen, the box is also clipped to a rect that closes
 * in on the frame as p falls, so its letterbox doesn't spill over the page around the shrinking video.
 */
private class VideoGlide(private val box: Rect, private val screen: Rect, private val fullscreen: Boolean) {
    /** Where a 16:9 video sits fullscreen. */
    val frame = screen.fitAspect(16f / 9f)

    fun apply(layer: GraphicsLayerScope, p: Float) = with(layer) {
        val laidOut = if (fullscreen) screen else box
        val ref = if (fullscreen) frame else box      // where the frame sits in the laid-out box
        val target = lerp(box, frame, p)              // where it's drawn now
        val s = target.width / ref.width
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = s
        scaleY = s
        translationX = target.left - laidOut.left - (ref.left - laidOut.left) * s
        translationY = target.top - laidOut.top - (ref.top - laidOut.top) * s
        clip = fullscreen && p < 1f
        shape = if (clip) RectShape(lerp(ref.translate(-laidOut.left, -laidOut.top), Rect(Offset.Zero, laidOut.size), p))
        else RectangleShape
    }
}

/** Lays the content out at [size] (px) and scales it, from the top-left, to the width it's given. */
private fun Modifier.laidOutAt(size: Size) = layout { measurable, constraints ->
    val width = size.width.roundToInt().coerceAtLeast(1)
    val height = size.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = constraints.maxWidth / width.toFloat()
            scaleY = scaleX
        }
    }
}

internal fun Rect.fitAspect(aspect: Float): Rect =
    if (width / height > aspect) Rect(Offset(center.x - height * aspect / 2f, top), Size(height * aspect, height))
    else Rect(Offset(left, center.y - width / aspect / 2f), Size(width, width / aspect))

private class RectShape(private val rect: Rect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Rectangle(rect)
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
        color = AppTheme.colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxHeight().width(300.dp),
    ) {
        Column(Modifier.safeDrawingPadding()) {
            Text(
                "Episodes",
                style = AppTheme.type.titleMedium,
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
            color = if (current) AppTheme.colors.primary
            else AppTheme.colors.surfaceContainerHighest,
        ) {
            Box(Modifier.size(width = 44.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${episode.number}",
                    style = AppTheme.type.titleSmall,
                    color = if (current) AppTheme.colors.onPrimary
                    else AppTheme.colors.onSurface,
                )
            }
        }
        Text(
            episode.title ?: "Episode ${episode.number}",
            style = AppTheme.type.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (current) AppTheme.colors.tertiary
            else AppTheme.colors.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (current) {
            Icon(
                AppIcons.Filled.PlayArrow, "Playing",
                tint = AppTheme.colors.tertiary,
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
            style = AppTheme.type.labelLarge,
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
                ControlIcon(AppIcons.Filled.KeyboardArrowDown, "Back", onBack, iconSize = if (fullscreen) 32.dp else 28.dp)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    // The watch page shows the title and episode right under the video.
                    if (fullscreen) {
                        Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = AppTheme.type.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Episode $episodeNumber",
                            color = TimerColor,
                            maxLines = 1,
                            style = AppTheme.type.labelMedium,
                        )
                    }
                }
                if (fullscreen) ControlIcon(AppIcons.Filled.LockOpen, "Lock controls", onLock, iconSize = toolIcon)
                Box {
                    ControlIcon(AppIcons.Filled.Speed, "Playback speed", { onSpeedMenuOpenChange(true) }, iconSize = toolIcon)
                    DropdownMenu(
                        expanded = speedMenuOpen,
                        onDismissRequest = { onSpeedMenuOpenChange(false) },
                    ) {
                        SpeedOptions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(s)) },
                                onClick = { onSelectSpeed(s); onSpeedMenuOpenChange(false) },
                                leadingIcon = { if (s == speed) Icon(AppIcons.Filled.Check, null) },
                            )
                        }
                    }
                }
                if (textTracks.isNotEmpty()) {
                    Box {
                        ControlIcon(AppIcons.Filled.ClosedCaption, "Subtitles", { onCcMenuOpenChange(true) }, iconSize = toolIcon)
                        DropdownMenu(expanded = ccMenuOpen, onDismissRequest = { onCcMenuOpenChange(false) }) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = { onDisableText(); onCcMenuOpenChange(false) },
                                leadingIcon = { if (textDisabled) Icon(AppIcons.Filled.Check, null) },
                            )
                            textTracks.forEachIndexed { i, t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    onClick = { onSelectText(i); onCcMenuOpenChange(false) },
                                    leadingIcon = { if (!textDisabled && t.selected) Icon(AppIcons.Filled.Check, null) },
                                )
                            }
                        }
                    }
                }
                if (hasEpisodeList && fullscreen) {
                    ControlIcon(AppIcons.AutoMirrored.Filled.PlaylistPlay, "Episodes", onOpenEpisodes, iconSize = toolIcon)
                }
                ControlIcon(AppIcons.Filled.AspectRatio, "Resize mode", onCycleResize, iconSize = toolIcon)
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
                CenterButton(AppIcons.Filled.SkipPrevious, "Previous episode", onPrev, side, sideIcon, enabled = hasPrev)
                CenterButton(AppIcons.Filled.Replay10, "Rewind 10s", onRewind, side, sideIcon)
                CenterButton(
                    if (isPlaying) AppIcons.Filled.Pause else AppIcons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    onPlayPause,
                    size = if (fullscreen) 72.dp else 52.dp,
                    iconSize = if (fullscreen) 48.dp else 34.dp,
                    showIcon = !isBuffering,
                    enabled = !isBuffering,
                )
                CenterButton(AppIcons.Filled.Forward10, "Forward 10s", onForward, side, sideIcon)
                CenterButton(AppIcons.Filled.SkipNext, "Next episode", onNext, side, sideIcon, enabled = hasNext)
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
                    // Tap the clock to switch between elapsed / total and time remaining. Tabular
                    // figures keep it from jittering as the seconds tick.
                    var remaining by rememberSaveable { mutableStateOf(false) }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = "Toggle time remaining") { remaining = !remaining }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (remaining) "−" + formatClock(duration - position) else formatClock(position),
                            color = Color.White,
                            style = AppTheme.type.labelLarge.merge(ClockStyle),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (remaining) "  left" else "  /  ${formatClock(duration)}",
                            color = TimerColor,
                            style = AppTheme.type.labelLarge.merge(ClockStyle),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    ControlIcon(
                        if (fullscreen) AppIcons.Filled.FullscreenExit else AppIcons.Filled.Fullscreen,
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
                    accent = AppTheme.colors.tertiary,
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
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .background(DiscColor, CircleShape),
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

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity in context chain")
}
