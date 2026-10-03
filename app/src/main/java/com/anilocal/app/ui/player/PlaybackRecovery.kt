package com.anilocal.app.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

    fun onError(request: PlaybackRestart) {
        if (closed) return
        onPlaybackStopped()
        lastRequest = request
        pending = request
        if (recoveryJob?.isActive == true) return

        recoveryJob = scope.launch {
            try {
                while (pending != null) {
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
                        pending = next
                    }
                }
            } finally {
                recoveryJob = null
            }
        }
    }

    fun onPlaybackStarted() {
        if (closed || pending != null) return
        _state.value = PlaybackRecoveryState.Idle
        if (stablePlaybackJob?.isActive == true) return
        stablePlaybackJob = scope.launch {
            delay(STABLE_PLAYBACK_MS)
            attempts = 0
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
        pending = null
        recoveryJob?.cancel()
        onPlaybackStopped()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val STABLE_PLAYBACK_MS = 30_000L
    }
}
