package com.anilocal.app.ui.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRecoveryTest {
    @Test fun duplicateFatalNotificationsBeforeAndDuringBackoffProduceOneRestart() = runTest {
        val requests = mutableListOf<PlaybackRestart>()
        val recovery = PlaybackRecovery(backgroundScope) { requests += it }

        repeat(3) { recovery.onError(request) }
        runCurrent()
        assertEquals(PlaybackRecoveryState.Restarting(1), recovery.state.value)
        assertTrue(requests.isEmpty())

        elapse(500)
        repeat(3) { recovery.onError(request) }
        elapse(499)
        assertTrue(requests.isEmpty())
        elapse(1)

        assertEquals(listOf(request), requests)
        recovery.onPlaybackStarted()
        assertEquals(PlaybackRecoveryState.Idle, recovery.state.value)
        recovery.close()
    }

    @Test fun failuresDuringSuspendedResolutionQueueOneRestartWithoutConcurrentResolution() = runTest {
        val releaseFirstResolution = CompletableDeferred<Unit>()
        val requests = mutableListOf<PlaybackRestart>()
        var activeResolutions = 0
        var maximumActiveResolutions = 0
        val recovery = PlaybackRecovery(backgroundScope) {
            requests += it
            activeResolutions++
            maximumActiveResolutions = maxOf(maximumActiveResolutions, activeResolutions)
            try {
                if (requests.size == 1) releaseFirstResolution.await()
            } finally {
                activeResolutions--
            }
        }

        recovery.onError(request)
        elapse(1_000)
        assertEquals(1, activeResolutions)
        val nextFailure = request.copy(positionMs = 91_234, failedStreamUrl = "replacement")
        repeat(3) { recovery.onError(nextFailure) }
        elapse(10_000)
        assertEquals(listOf(request), requests)

        releaseFirstResolution.complete(Unit)
        runCurrent()
        assertEquals(PlaybackRecoveryState.Restarting(2), recovery.state.value)
        elapse(2_000)

        assertEquals(listOf(request, nextFailure), requests)
        assertEquals(1, maximumActiveResolutions)
        assertEquals(0, activeResolutions)
        recovery.close()
    }

    @Test fun restartPreservesPositionPlaybackIntentAndFailedUrl() = runTest {
        for (playWhenReady in listOf(true, false)) {
            val expected = request.copy(playWhenReady = playWhenReady)
            val requests = mutableListOf<PlaybackRestart>()
            val recovery = PlaybackRecovery(backgroundScope) { requests += it }

            recovery.onError(expected)
            elapse(1_000)

            assertEquals(listOf(expected), requests)
            recovery.close()
        }
    }

    @Test fun errorRaisedSynchronouslyByRestartIsNotLost() = runTest {
        val requests = mutableListOf<PlaybackRestart>()
        val replacementFailure = request.copy(positionMs = 45_678, failedStreamUrl = "replacement")
        lateinit var recovery: PlaybackRecovery
        recovery = PlaybackRecovery(backgroundScope) {
            requests += it
            if (requests.size == 1) recovery.onError(replacementFailure)
        }

        recovery.onError(request)
        elapse(1_000)
        assertEquals(PlaybackRecoveryState.Restarting(2), recovery.state.value)
        elapse(1_999)
        assertEquals(listOf(request), requests)
        elapse(1)

        assertEquals(listOf(request, replacementFailure), requests)
        recovery.close()
    }

    @Test fun readyAndBriefPlaybackDoNotGiveRepeatedFatalFailuresAnUnlimitedBudget() = runTest {
        var restarts = 0
        val recovery = PlaybackRecovery(backgroundScope) { restarts++ }

        for (attempt in 1..3) {
            recovery.onError(request)
            runCurrent()
            assertEquals(PlaybackRecoveryState.Restarting(attempt), recovery.state.value)
            elapse(1_000L shl (attempt - 1))
            recovery.onPlayerReady()
            assertEquals(PlaybackRecoveryState.Idle, recovery.state.value)
            recovery.onPlaybackStarted()
            elapse(5_000)
            recovery.onPlaybackStopped()
        }
        recovery.onError(request)
        runCurrent()
        elapse(60_000)

        assertEquals(3, restarts)
        assertEquals(PlaybackRecoveryState.Failed, recovery.state.value)
        recovery.close()
    }

    @Test fun thirtySecondsOfContinuousActualPlaybackResetsTheRetryBudget() = runTest {
        var restarts = 0
        val recovery = PlaybackRecovery(backgroundScope) { restarts++ }
        exhaustSuccessfulRestarts(recovery)

        recovery.onPlaybackStarted()
        elapse(15_000)
        // Repeated isPlaying notifications must not restart the stability clock.
        recovery.onPlaybackStarted()
        elapse(15_000)
        recovery.onError(request)
        runCurrent()

        assertEquals(PlaybackRecoveryState.Restarting(1), recovery.state.value)
        elapse(1_000)
        assertEquals(4, restarts)
        recovery.close()
    }

    @Test fun pausedReadyPlayerDoesNotResetTheRetryBudget() = runTest {
        var restarts = 0
        val recovery = PlaybackRecovery(backgroundScope) { restarts++ }
        exhaustSuccessfulRestarts(recovery)

        recovery.onPlayerReady()
        elapse(60_000)
        recovery.onError(request)
        runCurrent()

        assertEquals(3, restarts)
        assertEquals(PlaybackRecoveryState.Failed, recovery.state.value)
        recovery.close()
    }

    @Test fun bufferingStopsStabilityClockAndPlaybackMustBeContinuousToResetBudget() = runTest {
        var restarts = 0
        val recovery = PlaybackRecovery(backgroundScope) { restarts++ }
        exhaustSuccessfulRestarts(recovery)

        recovery.onPlaybackStarted()
        elapse(29_999)
        recovery.onPlaybackStopped()
        elapse(60_000)
        recovery.onPlaybackStarted()
        elapse(29_999)
        recovery.onError(request)
        runCurrent()

        assertEquals(3, restarts)
        assertEquals(PlaybackRecoveryState.Failed, recovery.state.value)
        recovery.close()
    }

    @Test fun resolutionExceptionsRetryWithBackoffThenExposeFailure() = runTest {
        val requests = mutableListOf<PlaybackRestart>()
        val recovery = PlaybackRecovery(backgroundScope) {
            requests += it
            throw IllegalStateException("Server unavailable")
        }

        recovery.onError(request)
        elapse(999)
        assertTrue(requests.isEmpty())
        elapse(1)
        assertEquals(1, requests.size)
        assertEquals(PlaybackRecoveryState.Restarting(2), recovery.state.value)
        elapse(1_999)
        assertEquals(1, requests.size)
        elapse(1)
        assertEquals(2, requests.size)
        assertEquals(PlaybackRecoveryState.Restarting(3), recovery.state.value)
        elapse(4_000)
        elapse(60_000)

        assertEquals(listOf(request, request, request), requests)
        assertEquals(PlaybackRecoveryState.Failed, recovery.state.value)
        recovery.close()
    }

    @Test fun transientResolutionFailureCanRecoverOnNextAttempt() = runTest {
        var restarts = 0
        val recovery = PlaybackRecovery(backgroundScope) {
            restarts++
            if (restarts == 1) throw IllegalStateException("Temporary connection failure")
        }

        recovery.onError(request)
        elapse(1_000)
        elapse(2_000)
        recovery.onPlaybackStarted()
        elapse(5_000)

        assertEquals(2, restarts)
        assertEquals(PlaybackRecoveryState.Idle, recovery.state.value)
        recovery.close()
    }

    @Test fun manualRetryAfterFailureResetsBudgetAndUsesLastPlaybackRequest() = runTest {
        val requests = mutableListOf<PlaybackRestart>()
        var failResolution = true
        val recovery = PlaybackRecovery(backgroundScope) {
            requests += it
            if (failResolution) throw IllegalStateException("Server unavailable")
        }
        recovery.onError(request)
        elapse(1_000)
        elapse(2_000)
        elapse(4_000)
        assertEquals(PlaybackRecoveryState.Failed, recovery.state.value)

        failResolution = false
        recovery.retry()
        runCurrent()
        assertEquals(PlaybackRecoveryState.Restarting(1), recovery.state.value)
        elapse(999)
        assertEquals(3, requests.size)
        elapse(1)
        recovery.onPlaybackStarted()

        assertEquals(listOf(request, request, request, request), requests)
        assertEquals(PlaybackRecoveryState.Idle, recovery.state.value)
        recovery.close()
    }

    @Test fun closeDuringBackoffPreventsReplacementAndIgnoresLaterErrorsAndRetry() = runTest {
        var replacements = 0
        val recovery = PlaybackRecovery(backgroundScope) { replacements++ }
        recovery.onError(request)
        elapse(500)

        recovery.close()
        recovery.onError(request)
        recovery.retry()
        recovery.onPlaybackStarted()
        elapse(60_000)

        assertEquals(0, replacements)
    }

    @Test fun closeDuringResolutionCancelsResolutionBeforeReplacement() = runTest {
        val releaseResolution = CompletableDeferred<Unit>()
        var resolutionStarted = false
        var resolutionCancelled = false
        var replacements = 0
        val recovery = PlaybackRecovery(backgroundScope) {
            resolutionStarted = true
            try {
                releaseResolution.await()
                replacements++
            } finally {
                resolutionCancelled = !releaseResolution.isCompleted
            }
        }
        recovery.onError(request)
        elapse(1_000)
        assertTrue(resolutionStarted)

        recovery.close()
        runCurrent()
        assertTrue(resolutionCancelled)
        releaseResolution.complete(Unit)
        elapse(60_000)

        assertEquals(0, replacements)
    }

    @Test fun scopeCancellationDuringBackoffPreventsReplacement() = runTest {
        val recoveryScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        var replacements = 0
        val recovery = PlaybackRecovery(recoveryScope) { replacements++ }
        recovery.onError(request)
        elapse(500)

        recoveryScope.cancel()
        elapse(60_000)

        assertEquals(0, replacements)
        recovery.close()
    }

    @Test fun scopeCancellationDuringResolutionPreventsReplacement() = runTest {
        val recoveryScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val releaseResolution = CompletableDeferred<Unit>()
        var resolutionStarted = false
        var resolutionFinished = false
        var replacements = 0
        val recovery = PlaybackRecovery(recoveryScope) {
            resolutionStarted = true
            try {
                releaseResolution.await()
                replacements++
            } finally {
                resolutionFinished = true
            }
        }
        recovery.onError(request)
        elapse(1_000)
        assertTrue(resolutionStarted)
        assertFalse(resolutionFinished)

        recoveryScope.cancel()
        runCurrent()
        assertTrue(resolutionFinished)
        releaseResolution.complete(Unit)
        elapse(60_000)

        assertEquals(0, replacements)
        recovery.close()
    }

    private fun TestScope.exhaustSuccessfulRestarts(recovery: PlaybackRecovery) {
        for (attempt in 1..3) {
            recovery.onError(request)
            elapse(1_000L shl (attempt - 1))
        }
    }

    private fun TestScope.elapse(milliseconds: Long) {
        runCurrent()
        advanceTimeBy(milliseconds)
        runCurrent()
    }

    private companion object {
        val request = PlaybackRestart(
            positionMs = 123_456L,
            playWhenReady = true,
            failedStreamUrl = "https://failed.example/video.m3u8?token=current",
        )
    }
}
