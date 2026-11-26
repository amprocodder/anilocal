package com.anilocal.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * The app's motion: the curves and durations shared by the transitions that move the player — the
 * player rising over the screen it opens from and sinking back down (or being swiped away to Home),
 * the bottom bar getting out of its way, and the video gliding between fullscreen and the watch
 * page. Short enough that nothing
 * waits on them: playback starts loading the moment the player opens, whatever the animation does.
 */
object Motion {
    /**
     * Ease in and out — for things arriving or moving on screen. Its gentle start matters: the first
     * frame of a transition is often a slow one (the incoming screen composes), and a curve that
     * covers most of the distance up front would spend its motion inside that frame, unseen.
     */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Gentle start, leaving at full speed — for things going off screen. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** The player rising from the bottom edge to fill the screen. */
    const val PLAYER_OPEN_MS = 300

    /** The player sinking back off the bottom edge. */
    const val PLAYER_CLOSE_MS = 240

    /** The video gliding between fullscreen and its box on the watch page. */
    const val FULLSCREEN_MS = 280

    /** The player dropping off the bottom edge when a swipe down lets go at a standstill (a fling is quicker). */
    const val PLAYER_SWIPE_OUT_MS = 260
}
