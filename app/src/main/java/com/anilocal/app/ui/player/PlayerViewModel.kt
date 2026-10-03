package com.anilocal.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Named

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val cache: Cache,
    private val catalog: CatalogRepository,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val progress: ProgressRepository,
    private val downloads: DownloadRepository,
    // App-lifetime scope so the final save survives viewModelScope being cancelled on teardown.
    @Named("appScope") private val appScope: CoroutineScope,
    settings: SettingsRepository,
) : ViewModel() {

    private val animeId: String = checkNotNull(savedState["animeId"])
    private val episodeNumber: Int = checkNotNull(savedState["episodeNumber"])

    // Optional resume point (ms) passed from Continue Watching; 0 means start from the beginning.
    private val startMs: Long = savedState["startMs"] ?: 0L

    // A replacement is published so the existing screen can attach without leaving the route.
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _markers = MutableStateFlow<List<SkipMarker>>(emptyList())
    val markers: StateFlow<List<SkipMarker>> = _markers

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    // The UI only changes when entering/leaving a skip window, not on every playback tick.
    val activeMarker: StateFlow<SkipMarker?> =
        combine(position, markers) { pos, windows ->
            windows.firstOrNull { pos in it.startMs until it.endMs }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val subtitleScale: StateFlow<Float> =
        settings.subtitleScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    val subtitleBackground: StateFlow<Boolean> =
        settings.subtitleBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var summary: AnimeSummary? = null
    private var idMal: Int? = null
    private var autoSkipEnabled = true
    private val autoSkipped = mutableSetOf<Long>()
    private var currentStreamUrl: String? = null
    private var playbackPrepared = false
    private var lastPositionMs = startMs
    private var lastDurationMs = 0L
    private var intendedPlayWhenReady = true

    private val recovery = PlaybackRecovery(viewModelScope) { request ->
        load(request.positionMs, request.failedStreamUrl)
    }
    val recoveryState: StateFlow<PlaybackRecoveryState> = recovery.state

    init {
        viewModelScope.launch { settings.autoSkip.collect { autoSkipEnabled = it } }

        viewModelScope.launch {
            try {
                load(startMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recovery.onError(PlaybackRestart(lastPositionMs, true, currentStreamUrl))
            }
        }

        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (recoveryState.value == PlaybackRecoveryState.Idle && !_loading.value) {
                    captureProgress()
                    maybeAutoSkip(lastPositionMs)
                }
                _position.value = lastPositionMs
                if (_player.value?.isPlaying == true) {
                    if (++tick % 15 == 0) saveProgress()   // ~ every 6s of actual playback
                    delay(400)
                } else {
                    // Pauses and failed/idle sessions need no repeated writes or high-rate polling.
                    tick = 0
                    delay(1_000)
                }
            }
        }
    }

    private fun createPlayer(headers: Map<String, String>, offline: Boolean = false): ExoPlayer {
        // A per-player HTTP factory applies source headers without changing the download manager.
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("AniLocal")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                // DefaultDataSource still reads local subtitle files; HTTP is disabled offline.
                DefaultDataSource.Factory(context, if (offline) PlaceholderDataSource.FACTORY else http),
            )
        val newPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (_player.value !== newPlayer) return
                if (state == Player.STATE_READY) {
                    playbackPrepared = true
                    captureProgress()
                    if (newPlayer.duration > 0) {
                        _markers.value = playableMarkers(_markers.value, newPlayer.duration)
                    }
                    recovery.onPlayerReady()
                }
                // Online path resolves skip windows once duration is known.
                if (state == Player.STATE_READY && _markers.value.isEmpty() && !_offline.value) {
                    val lengthSec = newPlayer.duration.coerceAtLeast(0) / 1000
                    viewModelScope.launch {
                        try {
                            val markers = skip.markers(idMal, episodeNumber, lengthSec)
                            if (_player.value === newPlayer) {
                                _markers.value = playableMarkers(markers, newPlayer.duration)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Missing skip metadata must not interrupt playback.
                        }
                    }
                } else if (state == Player.STATE_ENDED) {
                    // Finished — drop it from Continue Watching so it doesn't linger near 100%.
                    summary?.let { s -> viewModelScope.launch { progress.remove(s.id) } }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_player.value !== newPlayer) return
                if (isPlaying) recovery.onPlaybackStarted() else recovery.onPlaybackStopped()
                // Capture the resume point when the user pauses (scope still alive here; the final
                // save on teardown is handled separately in onCleared via appScope).
                if (!isPlaying) viewModelScope.launch { saveProgress() }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (_player.value === newPlayer) intendedPlayWhenReady = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                if (_player.value !== newPlayer) return
                captureProgress()
                playbackPrepared = false
                val request = PlaybackRestart(lastPositionMs, intendedPlayWhenReady, currentStreamUrl)
                newPlayer.stop()
                recovery.onError(request)
            }
        })
        return newPlayer
    }

    private suspend fun load(positionMs: Long, failedStreamUrl: String? = null) {
        _loading.value = true
        try {
            val cached = downloads.getOffline(animeId, episodeNumber)
            check(cached != null || !_offline.value) { "Downloaded episode is no longer available" }
            val stream = if (cached != null) {
                // Offline recovery rebuilds the cached player and never runs a network speed test.
                _offline.value = true
                idMal = cached.idMal
                summary = AnimeSummary(animeId, cached.title, cached.posterUrl, cached.idMal)
                _markers.value = cached.markers
                VideoStream(cached.streamUri, cached.mimeType, subtitles = cached.subtitles)
            } else {
                _offline.value = false
                val anime = summary ?: catalog.detail(animeId).let { detail ->
                    idMal = detail.idMal
                    AnimeSummary(detail.id, detail.title, detail.posterUrl, detail.idMal)
                        .also { summary = it }
                }
                // Always resolve new URLs and measure the current sources, including on recovery.
                streams.resolveFastestStream(anime.title, episodeNumber, failedStreamUrl)
            }
            coroutineContext.ensureActive()
            // A pause while reconnecting overrides the intent captured at the original failure.
            play(stream, positionMs, intendedPlayWhenReady)
        } finally {
            _loading.value = false
        }
    }

    private fun play(stream: VideoStream, positionMs: Long, playWhenReady: Boolean) {
        val subConfigs = stream.subtitles.mapIndexed { index, sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subtitleMime(sub.url))
                .setLanguage(sub.language)
                .setSelectionFlags(
                    C.SELECTION_FLAG_AUTOSELECT or if (index == 0) C.SELECTION_FLAG_DEFAULT else 0,
                )
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setSubtitleConfigurations(subConfigs)
            .apply {
                when {
                    stream.mimeType != null -> setMimeType(stream.mimeType)
                    stream.url.substringBefore('?').endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        val previous = _player.value
        val replacement = createPlayer(stream.headers, offline = _offline.value)
        playbackPrepared = false
        currentStreamUrl = stream.url
        _player.value = replacement
        previous?.release()
        replacement.setMediaItem(item, positionMs.coerceAtLeast(0L))
        replacement.playWhenReady = playWhenReady
        replacement.prepare()
    }

    private fun maybeAutoSkip(pos: Long) {
        if (!autoSkipEnabled || !playbackPrepared) return
        val active = _markers.value.firstOrNull { pos in it.startMs until it.endMs } ?: return
        if (autoSkipped.add(active.startMs)) _player.value?.seekTo(active.endMs)
    }

    private fun captureProgress() {
        // An idle replacement reports zero until prepared; keep the failed session's resume point.
        if (!playbackPrepared) return
        val current = _player.value ?: return
        lastPositionMs = current.currentPosition.coerceAtLeast(0L)
        val duration = current.duration
        if (duration > 0) lastDurationMs = duration
    }

    private suspend fun saveProgress() {
        val s = summary ?: return
        captureProgress()
        persist(s, lastPositionMs, lastDurationMs)
    }

    private suspend fun persist(s: AnimeSummary, pos: Long, dur: Long) {
        if (pos <= 0) return
        // Don't record a position in the final stretch — otherwise "Continue" would resume at the
        // credits. Scale the window down for short clips so they still get a resumable point.
        val threshold = if (dur > 0) minOf(END_THRESHOLD_MS, dur / 10) else 0L
        if (dur > 0 && pos >= dur - threshold) return
        progress.save(s, episodeNumber, pos, dur)
    }

    fun seekPast(marker: SkipMarker) { _player.value?.seekTo(marker.endMs) }

    fun retryPlayback() = recovery.retry()

    override fun onCleared() {
        // viewModelScope is already cancelled here, so persist the final position on the app scope
        // (capturing values before release(), after which the player can't be read).
        val s = summary
        captureProgress()
        val pos = lastPositionMs
        val dur = lastDurationMs
        recovery.close()
        if (s != null) appScope.launch { persist(s, pos, dur) }
        _player.value?.release()
    }

    private companion object {
        const val END_THRESHOLD_MS = 5_000L
    }

    private fun subtitleMime(url: String): String = when {
        url.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        url.endsWith(".ass", true) || url.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}
