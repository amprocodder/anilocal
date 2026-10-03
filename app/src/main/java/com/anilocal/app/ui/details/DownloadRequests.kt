package com.anilocal.app.ui.details

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.ui.common.loadOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bounded parallel preparation, one owned picker at a time, and no duplicate episode work. */
internal class DownloadRequests(
    private val scope: CoroutineScope,
    private val detail: () -> AnimeDetail?,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val downloads: DownloadRepository,
    preparationLimit: Int = 3,
) {
    private val preparationSlots = Semaphore(preparationLimit)
    private val prepared = ArrayDeque<PendingDownload>()
    private val _pending = MutableStateFlow<PendingDownload?>(null)
    val pending: StateFlow<PendingDownload?> = _pending
    private val _busyEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val busyEpisodes: StateFlow<Set<Int>> = _busyEpisodes

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            prepared.clear()
            _pending.value = null
            _busyEpisodes.value = emptySet()
        }
    }

    fun download(episode: Episode) {
        if (!scope.isActive) return
        val anime = detail() ?: return
        if (episode.number in _busyEpisodes.value) return
        _busyEpisodes.update { it + episode.number }
        scope.launch {
            var waitingForChoice = false
            try {
                val request = preparationSlots.withPermit { prepare(anime, episode) } ?: return@launch
                currentCoroutineContext().ensureActive()
                if (request.options.size == 1) {
                    // DownloadManager/enqueue work is independent of catalog preparation slots.
                    loadOrNull { downloads.enqueue(anime, episode, request.options.first(), request.markers) }
                } else {
                    prepared.addLast(request)
                    waitingForChoice = true
                    showNextPicker()
                }
            } finally {
                if (!waitingForChoice) finishEpisode(episode.number)
            }
        }
    }

    private suspend fun prepare(anime: AnimeDetail, episode: Episode): PendingDownload? = coroutineScope {
        val markerRequest = async { loadOrNull { skip.markers(anime.idMal, episode.number, 0) }.orEmpty() }
        val options = loadOrNull { streams.resolveStreams(anime.title, episode.number) }.orEmpty()
        if (options.isEmpty()) {
            markerRequest.cancel()
            return@coroutineScope null
        }
        val markers = markerRequest.await()
        currentCoroutineContext().ensureActive()
        PendingDownload(episode, markers, options)
    }

    fun chooseQuality(request: PendingDownload, stream: VideoStream) {
        if (!scope.isActive) return
        val anime = detail() ?: return
        // A callback from the previous dialog must not choose for the next queued episode.
        if (_pending.value !== request || stream !in request.options) return
        _pending.value = null
        showNextPicker()
        scope.launch {
            try {
                loadOrNull { downloads.enqueue(anime, request.episode, stream, request.markers) }
            } finally {
                finishEpisode(request.episode.number)
            }
        }
    }

    fun dismissPicker(request: PendingDownload) {
        if (_pending.value !== request) return
        _pending.value = null
        finishEpisode(request.episode.number)
        showNextPicker()
    }

    private fun showNextPicker() {
        if (_pending.value == null && prepared.isNotEmpty()) _pending.value = prepared.removeFirst()
    }

    private fun finishEpisode(number: Int) { _busyEpisodes.update { it - number } }
}
