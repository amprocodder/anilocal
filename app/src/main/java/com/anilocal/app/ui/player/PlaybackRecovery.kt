package com.anilocal.app.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PlaybackRestart(
    val positionMs: Long,
    val playWhenReady: Boolean,
    val failedStreamUrl: String?,
)

sealed interface PlaybackRecoveryState {
    data object Idle : PlaybackRecoveryState
    data class Restarting(val attempt: Int) : PlaybackRecoveryState
    data object Failed : PlaybackRecoveryState
}

/** Serializes fatal failures, including failures raised while a replacement is being prepared. */
internal class PlaybackRecovery(
    private val scope: CoroutineScope,
    private val restart: suspend (PlaybackRestart) -> Unit,
) {
    private val _state = MutableStateFlow<PlaybackRecoveryState>(PlaybackRecoveryState.Idle)
    val state = _state.asStateFlow()

    private var recoveryJob: Job? = null
    private var stablePlaybackJob: Job? = null
    private var pending: PlaybackRestart? = null
    private var lastRequest: PlaybackRestart? = null
    private var attempts = 0
    private var closed = false
    private var generation = 0

    fun onError(request: PlaybackRestart) {
        if (closed) return
        onPlaybackStopped()
        lastRequest = request
        pending = request
        if (recoveryJob?.isActive == true) return

        val session = generation
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (pending != null && session == generation) {
                    val next = checkNotNull(pending)
                    pending = null
                    if (attempts >= MAX_ATTEMPTS) {
                        _state.value = PlaybackRecoveryState.Failed
                        return@launch
                    }
                    val attempt = ++attempts
                    _state.value = PlaybackRecoveryState.Restarting(attempt)
                    delay(1_000L shl (attempt - 1))
                    // Duplicate notifications during the delay are the same stopped session.
                    pending = null
                    try {
                        restart(next)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The replacement can synchronously report a newer fatal error through
                        // [onError] before throwing (for example, a prepare failure followed by
                        // an error callback). Keep that newer request's position/play intent and
                        // failed URL instead of replaying the stale request that just threw.
                        if (pending == null) pending = next
                    }
                }
            } finally {
                if (session == generation) recoveryJob = null
            }
        }
        recoveryJob = job
        job.start()
    }

    fun onPlaybackStarted() {
        if (closed || pending != null) return
        _state.value = PlaybackRecoveryState.Idle
        if (stablePlaybackJob?.isActive == true) return
        val session = generation
        stablePlaybackJob = scope.launch {
            delay(STABLE_PLAYBACK_MS)
            if (session == generation) attempts = 0
        }
    }

    fun onPlaybackStopped() {
        stablePlaybackJob?.cancel()
        stablePlaybackJob = null
    }

    /** Ready can mean paused or audio-focus suppression. It earns no retry-budget reset. */
    fun onPlayerReady() {
        if (!closed && pending == null) _state.value = PlaybackRecoveryState.Idle
    }

    fun retry() {
        if (closed || _state.value != PlaybackRecoveryState.Failed) return
        attempts = 0
        lastRequest?.let(::onError)
    }

    fun close() {
        closed = true
        reset()
    }

    /** An episode switch abandons old work and gives the newly selected episode its own budget. */
    fun reset() {
        generation++
        pending = null
        lastRequest = null
        val previous = recoveryJob
        recoveryJob = null
        previous?.cancel()
        onPlaybackStopped()
        attempts = 0
        _state.value = PlaybackRecoveryState.Idle
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val STABLE_PLAYBACK_MS = 30_000L
    }
}
