package com.anilocal.app.ui.player

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import kotlin.math.abs

/** How the video fills its box when their shapes differ — what the player's resize button cycles. */
enum class VideoScale(val label: String) { Fit("Fit"), Zoom("Zoom"), Stretch("Stretch") }

/**
 * The video: a [SurfaceView] the [player] renders into, sized inside this box like Media3's
 * AspectRatioFrameLayout did ([VideoScale]), with captions drawn over the video frame and a black
 * "shutter" until the current player's first frame (as Media3's PlayerView did, so a new episode
 * never flashes the previous one's last frame).
 *
 * The SurfaceView is created once; when [player] changes (an episode swap or the pre-cached standby
 * being promoted) the surface moves from the old player to the new one, which is what keeps the
 * switch instant. Keep this composable at a stable position — recreating it means a new surface.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, scale: VideoScale, captions: CaptionStyle, modifier: Modifier = Modifier) {
    var aspect by remember { mutableFloatStateOf(0f) }
    var shutter by remember { mutableStateOf(true) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    DisposableEffect(player) {
        aspect = player.videoSize.aspect()
        cues = player.currentCues.cues
        shutter = true
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { aspect = videoSize.aspect() }
            override fun onRenderedFirstFrame() { shutter = false }
            override fun onCues(cueGroup: CueGroup) { cues = cueGroup.cues }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val binding = remember { SurfaceBinding() }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val (frameWidth, frameHeight) = frameSize(boxWidth, boxHeight, aspect, scale)
        Box(Modifier.requiredSize(with(density) { frameWidth.toDp() }, with(density) { frameHeight.toDp() })) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx) },
                update = { view -> binding.bind(view, player) },
                onRelease = { view -> binding.unbind(view) },
                modifier = Modifier.fillMaxSize(),
            )
            if (shutter) Box(Modifier.fillMaxSize().background(Color.Black))
            Captions(cues, captions, Modifier.fillMaxSize())
        }
    }
}

/** Which player the surface is attached to, so a player change detaches the old one first. */
private class SurfaceBinding {
    private var attached: Player? = null

    fun bind(view: SurfaceView, player: Player) {
        if (attached === player) return
        attached?.clearVideoSurfaceView(view)
        player.setVideoSurfaceView(view)
        attached = player
    }

    fun unbind(view: SurfaceView) {
        attached?.clearVideoSurfaceView(view)
        attached = null
    }
}

private fun VideoSize.aspect(): Float =
    if (width == 0 || height == 0) 0f else width * pixelWidthHeightRatio / height

/**
 * The video frame's size in a [boxWidth]×[boxHeight] box — AspectRatioFrameLayout's measure: a frame
 * within 1% of the box's shape (or of unknown shape) just fills it; otherwise Fit letterboxes, Zoom
 * covers (the overflow is clipped) and Stretch fills.
 */
private fun frameSize(boxWidth: Float, boxHeight: Float, videoAspect: Float, scale: VideoScale): Pair<Float, Float> {
    if (videoAspect <= 0f || boxWidth <= 0f || boxHeight <= 0f) return boxWidth to boxHeight
    val deformation = videoAspect / (boxWidth / boxHeight) - 1f
    if (abs(deformation) <= MAX_ASPECT_DEFORMATION) return boxWidth to boxHeight
    return when (scale) {
        VideoScale.Fit -> if (deformation > 0) boxWidth to (boxWidth / videoAspect).toInt().toFloat()
        else (boxHeight * videoAspect).toInt().toFloat() to boxHeight
        VideoScale.Zoom -> if (deformation > 0) (boxHeight * videoAspect).toInt().toFloat() to boxHeight
        else boxWidth to (boxWidth / videoAspect).toInt().toFloat()
        VideoScale.Stretch -> boxWidth to boxHeight
    }
}

private const val MAX_ASPECT_DEFORMATION = 0.01f
