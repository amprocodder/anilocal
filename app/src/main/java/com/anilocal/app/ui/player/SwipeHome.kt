package com.anilocal.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the swipe tells the player screen as it goes (see [SwipeHomeState]). */
internal interface SwipeHomeEvents {
    /** A drag took hold. The player still covers the screen; Home should come up beneath it. */
    fun onStart()

    /** The drag crossed the point past which letting go leaves for Home. */
    fun onArmed()

    /** Let go short of leaving, and the player has sprung back into place. */
    fun onReturned()

    /** Let go to leave, and the player is off the bottom edge. */
    fun onGone()
}

/**
 * Swipe down to go Home, from fullscreen. The video comes away under the finger: the point you
 * grabbed stays under it while the player shrinks, narrows to the video's own 16:9 frame (dropping
 * any letterbox) and rounds its corners, and Home rises out of the dark behind it ([progress] drives
 * that reveal). Let go far enough down ([LEAVE_DISTANCE]) or with a downward fling and the player
 * drops off the bottom edge, carrying on from the release speed; otherwise it springs back.
 *
 * All of it is a transform of the player's layer ([applyTo]): a uniform scale + move, which the
 * video's SurfaceView follows, and a rounded clip, which its hole in the window follows (what's
 * drawn around it — Home — is opaque, so the video below the window shows only through the hole).
 * Nothing is re-laid out while it moves.
 */
@Stable
internal class SwipeHomeState {
    enum class Phase { Idle, Dragging, Returning, Leaving }

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** The player's size in px (fed from its layout). */
    var playerSize by mutableStateOf(Size.Zero)

    /** True from the moment a drag takes hold until the player is back in place (or gone). */
    val engaged: Boolean get() = phase != Phase.Idle

    /** How far Home is revealed: 0 = the player in place … 1 = shrunk to its smallest, Home in full. */
    val progress: Float
        get() = if (playerSize.height <= 0f) 0f
        else (position.y / (playerSize.height * REVEAL_DISTANCE)).coerceIn(0f, 1f)

    // The player's move from its place, in px: the finger's while dragging, then the animation's.
    private var dragged by mutableStateOf(Offset.Zero)
    private val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    private val position: Offset get() = if (phase == Phase.Dragging) dragged else offset.value

    // Where the drag started, in the player's px: the point that stays under the finger.
    private var origin by mutableStateOf(Offset.Zero)

    private val armed: Boolean get() = position.y >= playerSize.height * LEAVE_DISTANCE

    /** Draws the player where the drag has it. */
    fun applyTo(layer: GraphicsLayerScope) = with(layer) {
        val p = progress
        val s = scale(p)
        val m = morph(p)
        transformOrigin = TransformOrigin(origin.x / size.width, origin.y / size.height)
        scaleX = s
        scaleY = s
        translationX = position.x
        translationY = position.y
        // Radius in the layer's own px, so it's CORNER on screen at any scale.
        shape = CardShape(lerp(Rect(Offset.Zero, size), frame(size), m), CORNER.toPx() * m / s)
        clip = true
    }

    /** Follows a drag that sets off downward, then settles it — run from the player's pointerInput. */
    suspend fun PointerInputScope.track(
        enabled: () -> Boolean,
        events: SwipeHomeEvents,
    ): Unit = coroutineScope {
        var motion: Job? = null
        val tracker = VelocityTracker()
        val leaveSpeed = LEAVE_VELOCITY.toPx()
        val maxSpeed = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            // Not requiring an unconsumed down: the tap layer and buttons under the finger take the
            // down themselves, and only consume what they act on — a seek-bar drag cancels this.
            val down = awaitFirstDown(requireUnconsumed = false)
            if (phase == Phase.Leaving || !enabled()) return@awaitEachGesture
            tracker.resetTracking()
            tracker.addPointerInputChange(down)
            // Only a drag that sets off more down than sideways; anything else is left to the taps.
            val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                if (over.y > abs(over.x)) change.consume()
            } ?: return@awaitEachGesture
            tracker.addPointerInputChange(slop)

            // Caught again on its way back, it carries on from where it is.
            val from = if (phase == Phase.Returning) offset.value else Offset.Zero
            motion?.cancel()
            if (phase == Phase.Idle) {
                origin = down.position
                events.onStart()
            }
            dragged = from
            phase = Phase.Dragging
            var wasArmed = armed
            fun follow(at: Offset) {
                val d = from + at - down.position
                // Pulled up past where it started, it gives only a little.
                dragged = Offset(d.x, if (d.y < 0f) d.y * UPWARD_GIVE else d.y)
                if (armed != wasArmed) {
                    wasArmed = armed
                    if (wasArmed) events.onArmed()
                }
            }
            follow(slop.position)
            val completed = drag(slop.id) { change ->
                tracker.addPointerInputChange(change)
                follow(change.position)
                change.consume()
            }
            val velocity = if (!completed) Velocity.Zero else tracker.calculateVelocity().let {
                Velocity(it.x.coerceIn(-maxSpeed, maxSpeed), it.y.coerceIn(-maxSpeed, maxSpeed))
            }
            val leave = completed && (velocity.y > leaveSpeed || (armed && velocity.y > -leaveSpeed))
            motion = launch { if (leave) leave(velocity, events) else settle(velocity, events) }
        }
    }

    private suspend fun settle(velocity: Velocity, events: SwipeHomeEvents) {
        phase = Phase.Returning
        offset.snapTo(dragged)
        offset.animateTo(
            Offset.Zero,
            spring(dampingRatio = RETURN_DAMPING, stiffness = Spring.StiffnessMediumLow),
            initialVelocity = Offset(velocity.x, velocity.y),
        )
        if (phase == Phase.Returning) {
            phase = Phase.Idle
            events.onReturned()
        }
    }

    /**
     * Drops the player off the bottom edge as a thrown thing falls: on from the release speed and
     * gathering pace, taking [Motion.PLAYER_SWIPE_OUT_MS] from a standstill (a fling is quicker), and
     * done the moment its top edge clears the screen.
     */
    private suspend fun leave(velocity: Velocity, events: SwipeHomeEvents) {
        phase = Phase.Leaving
        val start = dragged
        offset.snapTo(start)
        val height = playerSize.height
        // Its top edge at its smallest, fully narrowed: origin + (frame.top - origin) * scale + y.
        val endY = max(height * REVEAL_DISTANCE, height - origin.y - (frame(playerSize).top - origin.y) * MIN_SCALE)
        val distance = endY - start.y
        if (distance > 0f) {
            val v0 = velocity.y.coerceAtLeast(0f) / 1000f                       // px/ms
            val t0 = Motion.PLAYER_SWIPE_OUT_MS.toFloat()
            val a = 2f * distance / (t0 * t0)                                    // px/ms²
            val duration = ((-v0 + sqrt(v0 * v0 + 2f * a * distance)) / a).coerceAtLeast(MIN_SWIPE_OUT_MS)
            // y(t) = v0·t + ½·a·t², as a fraction of the way over the duration.
            val linear = (v0 * duration / distance).coerceIn(0f, 1f)
            val fall = Easing { t -> linear * t + (1f - linear) * t * t }
            val drift = velocity.x / 1000f * duration / 2f
            offset.animateTo(Offset(start.x + drift, endY), tween(duration.roundToInt(), easing = fall))
        }
        events.onGone()
    }

    private companion object {
        /** Dragged this share of the player's height down, Home is fully revealed. */
        const val REVEAL_DISTANCE = 0.5f

        /** Let go past this share of the height (not flicked back up) and it leaves. */
        const val LEAVE_DISTANCE = 0.2f

        /** A downward fling this fast leaves from anywhere. */
        val LEAVE_VELOCITY = 1000.dp

        /** How small the player gets, fully dragged. */
        const val MIN_SCALE = 0.6f

        /** How far into the reveal it has narrowed to the video frame and rounded its corners. */
        const val MORPH_PROGRESS = 0.3f

        /** The card's corner radius on screen. */
        val CORNER = 20.dp

        /** Dragging up past the start moves it this much of the way. */
        const val UPWARD_GIVE = 0.25f

        /** Springing back overshoots a touch. */
        const val RETURN_DAMPING = 0.8f

        /** Even a hard fling stays on screen long enough to be seen leaving. */
        const val MIN_SWIPE_OUT_MS = 110f

        fun scale(p: Float) = 1f - (1f - MIN_SCALE) * p
        fun morph(p: Float) = (p / MORPH_PROGRESS).coerceIn(0f, 1f)

        /** Where a 16:9 video sits in a player of [size] (as the fullscreen glide has it). */
        fun frame(size: Size) = Rect(Offset.Zero, size).fitAspect(16f / 9f)
    }
}

/** Swipe down to go Home ([SwipeHomeState]); [enabled] is asked at each touch, so read state in it. */
internal fun Modifier.swipeHome(state: SwipeHomeState, enabled: () -> Boolean, events: SwipeHomeEvents) =
    pointerInput(state) { with(state) { track(enabled, events) } }

private class CardShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}
