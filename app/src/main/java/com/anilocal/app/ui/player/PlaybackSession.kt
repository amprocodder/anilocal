package com.anilocal.app.ui.player

/** Resume data belongs to an episode, even while its replacement player temporarily reports zero. */
internal class PlaybackSession(initialPositionMs: Long) {
    var generation = 0
        private set
    var attached = false
        private set
    var prepared = false
        private set
    var positionMs = initialPositionMs.coerceAtLeast(0)
        private set
    var durationMs = 0L
        private set
    var playWhenReady = true

    fun attach() {
        attached = true
        prepared = false
    }

    fun ready(positionMs: Long, durationMs: Long) {
        prepared = true
        capture(positionMs, durationMs)
    }

    fun capture(positionMs: Long, durationMs: Long) {
        if (!attached || !prepared) return
        this.positionMs = positionMs.coerceAtLeast(0)
        if (durationMs > 0) this.durationMs = durationMs
    }

    fun failed(positionMs: Long, durationMs: Long) {
        if (attached && prepared) {
            // Some fatal teardown paths clear the player's window before notifying the listener.
            // A deliberate seek to zero has already updated this session through seek().
            if (positionMs > 0 || this.positionMs == 0L) this.positionMs = positionMs.coerceAtLeast(0)
            if (durationMs > 0) this.durationMs = durationMs
        }
        prepared = false
    }

    fun seek(positionMs: Long) {
        this.positionMs = positionMs.coerceAtLeast(0)
    }

    fun switchEpisode() {
        generation++
        attached = false
        prepared = false
        positionMs = 0
        durationMs = 0
        playWhenReady = true
    }
}
