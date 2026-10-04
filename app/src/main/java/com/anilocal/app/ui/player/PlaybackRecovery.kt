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
    /**
     * A source replacement is being prepared. [automatic] is true for a retry made by the
     * background watchdog after the initial bounded retry budget was exhausted.
     *
     * The default keeps the state source-compatible with callers that only care about the
     * attempt number (and with the first three foreground retries).
     */
    data class Restarting(val attempt: Int, val automatic: Boolean = false) : PlaybackRecoveryState
    data object Failed : PlaybackRecoveryState
}

/** Serializes fatal failures, including failures raised while a replacement is being prepared. */
internal class PlaybackRecovery(
    private val scope: CoroutineScope,
    private val restart: suspend (PlaybackRestart) -> Unit,
) {
    private var backgroundRetryDelayMs: Long = BACKGROUND_RETRY_DELAY_MS
    /** Constructor used by local tests to shorten the watchdog interval without changing the
     * production call shape (which intentionally ends in a trailing restart lambda). */
    internal constructor(
        scope: CoroutineScope,
        restart: suspend (PlaybackRestart) -> Unit,
        backgroundRetryDelayMs: Long,
    ) : this(scope, restart) {
        this.backgroundRetryDelayMs = backgroundRetryDelayMs.coerceAtLeast(0L)
    }
    private val _state = MutableStateFlow<PlaybackRecoveryState>(PlaybackRecoveryState.Idle)
    val state = _state.asStateFlow()

    private var recoveryJob: Job? = null
    private var backgroundRetryJob: Job? = null
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
        // A real new fatal error should be retried immediately. It may arrive while the
        // watchdog is waiting between background attempts, so cancel that wait before deciding
        // whether an existing foreground recovery job can consume the request.
        backgroundRetryJob?.cancel()
        backgroundRetryJob = null
        if (recoveryJob?.isActive == true) return

        launchRecovery(generation)
    }

    private fun launchRecovery(session: Int, automaticFirstAttempt: Boolean = false) {
        if (closed || session != generation || recoveryJob?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (pending != null && session == generation) {
                    val next = checkNotNull(pending)
                    pending = null
                    if (attempts >= MAX_ATTEMPTS) {
                        _state.value = PlaybackRecoveryState.Failed
                        scheduleBackgroundRetry(session)
                        return@launch
                    }
                    val attempt = ++attempts
                    _state.value = PlaybackRecoveryState.Restarting(
                        attempt = attempt,
                        automatic = automaticFirstAttempt,
                    )
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
        backgroundRetryJob?.cancel()
        backgroundRetryJob = null
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
        if (!closed && pending == null) {
            backgroundRetryJob?.cancel()
            backgroundRetryJob = null
            _state.value = PlaybackRecoveryState.Idle
        }
    }

    fun retry() {
        if (closed || _state.value != PlaybackRecoveryState.Failed) return
        backgroundRetryJob?.cancel()
        backgroundRetryJob = null
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
        backgroundRetryJob?.cancel()
        backgroundRetryJob = null
        onPlaybackStopped()
        attempts = 0
        _state.value = PlaybackRecoveryState.Idle
    }

    /**
     * Keep trying while the player remains on the same episode. A source can be temporarily
     * unavailable for much longer than the initial 1/2/4 second retry sequence (for example when
     * an extension host is rate-limited), and leaving the player permanently failed would force a
     * needless manual restart. The watchdog sleeps between complete recovery attempts, so source
     * discovery and speed probes are run afresh on every pass.
     */
    private fun scheduleBackgroundRetry(session: Int) {
        if (closed || session != generation || backgroundRetryJob?.isActive == true) return
        backgroundRetryJob = scope.launch {
            try {
                while (!closed && session == generation) {
                    delay(backgroundRetryDelayMs)
                    if (closed || session != generation) return@launch
                    val request = lastRequest ?: return@launch
                    // A callback may have arrived while the watchdog was asleep. Let the active
                    // foreground loop consume it rather than starting a second recovery job.
                    if (recoveryJob?.isActive == true) continue
                    attempts = 0
                    pending = request
                    launchRecovery(session, automaticFirstAttempt = true)
                    // Wait for this bounded sequence to finish. If playback becomes ready,
                    // onPlayerReady cancels this watchdog and this loop exits on its next check.
                    while (session == generation && recoveryJob?.isActive == true) delay(100L)
                    if (session != generation || closed || _state.value != PlaybackRecoveryState.Failed) {
                        return@launch
                    }
                }
            } finally {
                if (session == generation) backgroundRetryJob = null
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val STABLE_PLAYBACK_MS = 30_000L
        const val BACKGROUND_RETRY_DELAY_MS = 30_000L
    }
}
