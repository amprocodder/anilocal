package com.anilocal.app.ui.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.OfflineEpisode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Named

/** A selectable subtitle (text) track surfaced to the UI — no Media3 types cross into Compose. */
data class PlayerTextTrack(val label: String, val selected: Boolean)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
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

    // The episode currently loaded — the single source of truth, observable by the UI (the overlay's
    // "Episode N" label and the episode picker's highlighted row). Mutable so auto-play-next can
    // advance through a season in place (same player/surface); mirrored back into the
    // SavedStateHandle on each advance so a process-death restore resumes the right episode.
    private val _currentEpisode = MutableStateFlow<Int>(checkNotNull(savedState["episodeNumber"]))
    val currentEpisode: StateFlow<Int> = _currentEpisode
    private var episodeNumber: Int
        get() = _currentEpisode.value
        set(value) { _currentEpisode.value = value }

    // Resume point (ms) for the CURRENT episode. Seeded from the Continue-Watching arg for the first
    // episode; reset to 0 when we auto-advance so each subsequent episode starts from the beginning.
    private var resumeFromMs: Long = savedState["startMs"] ?: 0L

    private val session = PlaybackSession(resumeFromMs)
    private var loadJob: Job? = null
    private var loading = false
    private var cleared = false
    private val _player = MutableStateFlow(createPlayer(playbackDataSource(emptyMap(), offline = false)))
    val player: StateFlow<ExoPlayer> = _player.asStateFlow()
    private val currentPlayer: ExoPlayer get() = _player.value

    private val _markers = MutableStateFlow<List<SkipMarker>>(emptyList())
    val markers: StateFlow<List<SkipMarker>> = _markers

    // A short, human-readable reason shown over the player when load/resolve/playback fails — so a
    // failure surfaces as a hint instead of an indistinguishable black screen (matches the app's
    // "short hint, no dialogs/spinners" convention). null = no error.
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _position = MutableStateFlow(session.positionMs)
    val position: StateFlow<Long> = _position

    // Secondary "buffered ahead" position for the seek bar's buffered fill (no Flow on the player).
    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition

    // Coerced episode duration (0 until known / for live), shared by the seek bar range and seek clamps.
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // Playback intent also controls Pause while the replacement is connecting/buffering.
    private val _playWhenReady = MutableStateFlow(true)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady

    // True while the player is rebuffering — drives the centered spinner in the overlay.
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    // Series title (top bar line 1); the episode number below renders line 2 ("Episode N").
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    // The season's episode list for the in-player picker: AniList detail online, or stubs built
    // from the downloaded episodes offline.
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes

    // Current playback speed (sticky across episode switches — the player keeps its parameters).
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    // Prev/next-episode button enablement.
    private val _hasPrev = MutableStateFlow(episodeNumber > 1)
    val hasPrev: StateFlow<Boolean> = _hasPrev
    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext

    // Subtitle (CC) tracks for the overlay's caption menu, plus whether captions are currently Off.
    private val _textTracks = MutableStateFlow<List<PlayerTextTrack>>(emptyList())
    val textTracks: StateFlow<List<PlayerTextTrack>> = _textTracks
    private val _textDisabled = MutableStateFlow(false)
    val textDisabled: StateFlow<Boolean> = _textDisabled
    // Index-aligned with [_textTracks]: the real (TrackGroup, trackIndex) each UI row selects.
    private var textGroups: List<Pair<TrackGroup, Int>> = emptyList()

    val subtitleScale: StateFlow<Float> =
        settings.subtitleScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    val subtitleBackground: StateFlow<Boolean> =
        settings.subtitleBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Title metadata, memoized: animeId is fixed for the VM's whole life, so AniList detail need run
    // at most once — auto-advance and hasEpisode() reuse it instead of re-fetching per episode.
    private var detail: AnimeDetail? = null
    private var summary: AnimeSummary? = null
    private var idMal: Int? = null
    // Remember the failed URI so a fresh source test can prefer a healthy alternative.
    private var currentUri: String? = null
    private var autoSkipEnabled = true
    private val autoSkipped = mutableSetOf<Long>()
    // Auto-play-next state: the season's episode count (from AniList detail, null until known), the user
    // toggle, and a re-entrancy guard so a single STATE_ENDED advances exactly once.
    private var totalEpisodes: Int? = null
    private var autoPlayNextEnabled = true
    private var advancing = false

    // Background-resolved next episode (keyed by number; a Deferred so a racing advance joins it
    // instead of restarting the multi-second source resolve). Consumed/cancelled in load().
    private var prefetched: Pair<Int, Deferred<VideoStream?>>? = null

    // AniSkip markers are fetched once per episode, but only after a RELIABLE duration is known — at
    // the first STATE_READY the duration is often still TIME_UNSET, which would disable AniSkip's
    // length filter and return a wrong-cut variant (the "skips 30s in" bug). Drive the fetch from the
    // tick loop instead of a single STATE_READY edge so a first empty result can still retry.
    private var markersFetched = false
    private var firstReadyAtMs = 0L
    private var markersJob: Job? = null

    private val episodeLoaded: Boolean get() = session.attached
    private data class TextChoice(val label: String?, val language: String?, val ordinal: Int)
    private var textChoice: TextChoice? = null
    private var restoreTextChoice = false

    private val recovery = PlaybackRecovery(viewModelScope) {
        val generation = session.generation
        load(recovering = true, failedUrl = it.failedStreamUrl, generation = generation)
    }
    val recoveryState: StateFlow<PlaybackRecoveryState> = recovery.state

    init {
        viewModelScope.launch { settings.autoSkip.collect { autoSkipEnabled = it } }
        viewModelScope.launch { settings.autoPlayNext.collect { autoPlayNextEnabled = it } }

        viewModelScope.launch {
            recovery.state.collect { state ->
                when (state) {
                    is PlaybackRecoveryState.Restarting -> _error.value = "Reconnecting… checking sources (${state.attempt}/3)"
                    PlaybackRecoveryState.Failed -> _error.value = "Playback couldn't reconnect. Try again."
                    PlaybackRecoveryState.Idle -> Unit
                }
            }
        }

        loadJob = viewModelScope.launch { loadSafely(session.generation) }

        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (!loading && recoveryState.value == PlaybackRecoveryState.Idle) {
                    captureProgress()
                    maybeAutoSkip(session.positionMs)
                    maybeFetchMarkers()
                }
                _position.value = session.positionMs
                _duration.value = session.durationMs
                // AniSkip responses are cached independently of the media cut. Once Media3 reports
                // a real duration, discard any stale window before the Skip pill/auto-skip can seek
                // beyond this episode (offline records need the same protection).
                if (session.durationMs > 0L && _markers.value.isNotEmpty()) {
                    val playable = playableMarkers(_markers.value, session.durationMs)
                    if (playable.size != _markers.value.size) _markers.value = playable
                }
                _bufferedPosition.value = if (session.prepared) currentPlayer.bufferedPosition else session.positionMs
                _isBuffering.value = loading || recoveryState.value is PlaybackRecoveryState.Restarting ||
                    currentPlayer.playbackState == Player.STATE_BUFFERING
                if (++tick % 20 == 0) saveProgress()   // ~ every 6s
                delay(300)
            }
        }
    }

    private fun playbackDataSource(headers: Map<String, String>, offline: Boolean): CacheDataSource.Factory {
        val agent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value ?: "AniLocal"
        val http = DefaultHttpDataSource.Factory().setUserAgent(agent)
            .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)
        return CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context,
                if (offline) PlaceholderDataSource.FACTORY else http))
    }

    private fun createPlayer(dataSource: CacheDataSource.Factory): ExoPlayer {
        val generation = session.generation
        // Phase2's legacy renderer and isolated sidecars must survive every recreation.
        val replacement = ExoPlayer.Builder(context, object : DefaultRenderersFactory(context) {
            @Suppress("DEPRECATION")
            override fun buildTextRenderers(context: Context, output: TextOutput, outputLooper: Looper,
                extensionRendererMode: Int, out: ArrayList<Renderer>) {
                out.add(TextRenderer(output, outputLooper).apply { experimentalSetLegacyDecodingEnabled(true) })
            }
        }).setSeekBackIncrementMs(10_000L).setSeekForwardIncrementMs(10_000L)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource).experimentalParseSubtitlesDuringExtraction(false))
            .build()
        replacement.addListener(object : Player.Listener {
            private fun active() = !cleared && session.generation == generation && _player.value === replacement

            override fun onPlaybackStateChanged(state: Int) {
                if (!active()) return
                if (state == Player.STATE_READY) {
                    session.ready(replacement.currentPosition, replacement.duration)
                    _error.value = null
                    recovery.onPlayerReady()
                    if (!_offline.value) prefetchNext()
                } else if (state == Player.STATE_ENDED && episodeLoaded && !advancing && !loading) {
                    advancing = true
                    loadJob?.cancel()
                    loadJob = viewModelScope.launch {
                        try { advanceToNextOrFinish() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { _error.value = error.message ?: "Couldn't advance episode" }
                        finally { advancing = false }
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (active()) {
                    session.playWhenReady = playWhenReady
                    _playWhenReady.value = playWhenReady
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!active()) return
                _isPlaying.value = isPlaying
                if (isPlaying) recovery.onPlaybackStarted() else recovery.onPlaybackStopped()
                if (!isPlaying) viewModelScope.launch { saveProgress() }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (active()) rebuildTextTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!active()) return
                session.failed(replacement.currentPosition, replacement.duration)
                session.playWhenReady = replacement.playWhenReady
                replacement.stop()
                startRecovery()
            }
        })
        return replacement
    }

    private fun startRecovery() {
        if (cleared) return
        prefetched?.second?.cancel()
        prefetched = null
        markersJob?.cancel()
        if (!_offline.value) {
            _markers.value = emptyList()
            markersFetched = false
            firstReadyAtMs = 0L
        }
        _error.value = "Reconnecting… checking sources"
        recovery.onError(PlaybackRestart(session.positionMs, session.playWhenReady, currentUri))
    }

    private suspend fun checkSession(generation: Int) {
        coroutineContext.ensureActive()
        if (cleared || session.generation != generation) throw CancellationException("Episode changed")
    }

    private suspend fun loadSafely(generation: Int) {
        try {
            load(generation = generation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            checkSession(generation)
            session.failed(currentPlayer.currentPosition, currentPlayer.duration)
            _error.value = "Couldn't start playback: ${error.message ?: error.javaClass.simpleName}"
            // Use the same bounded recovery for synchronous prepare/resolve failures.
            startRecovery()
        }
    }

    private suspend fun load(recovering: Boolean = false, failedUrl: String? = null,
        generation: Int = session.generation) {
        val episode = episodeNumber
        loading = true
        try {
            val cached = optional { downloads.getOffline(animeId, episode) }
            checkSession(generation)
            check(cached != null || !recovering || !_offline.value) { "Downloaded episode is no longer available" }
            if (cached != null) {
                _offline.value = true
                playOffline(cached, generation)
                return
            }
            _offline.value = false
            val d = ensureTitle() ?: error("Couldn't load title details (no connection?)")
            checkSession(generation)
            val stream = if (recovering) {
                // Fresh source/server discovery and byte measurements: no stale prefetch or winner pin.
                streams.recoverStream(d.title, episode, failedUrl)
            } else {
                val pre = prefetched?.takeIf { it.first == episode }?.second
                prefetched?.let { if (it.first != episode) it.second.cancel() }
                prefetched = null
                val prefetchedStream = pre?.let {
                    try { it.await() }
                    catch (cancelled: CancellationException) { coroutineContext.ensureActive(); null }
                    catch (_: Exception) { null }
                }
                prefetchedStream ?: streams.resolveStream(d.title, episode)
            }
            checkSession(generation)
            play(stream.url, stream.mimeType, stream.subtitles, stream.headers, generation)
        } finally {
            if (session.generation == generation) loading = false
        }
    }

    private suspend fun <T> optional(action: suspend () -> T): T? =
        try { action() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

    // Resolve the title's AniList detail at most once; populate idMal/totalEpisodes/summary/title.
    private suspend fun ensureTitle(): AnimeDetail? {
        detail?.let { return it }
        val d = optional { catalog.detail(animeId) } ?: return null
        coroutineContext.ensureActive()
        if (cleared) throw CancellationException("Player closed")
        detail = d
        idMal = d.idMal
        d.episodes.size.takeIf { it > 0 }?.let { totalEpisodes = it }
        summary = AnimeSummary(d.id, d.title, d.posterUrl, d.idMal)
        _title.value = d.title
        _episodes.value = d.episodes
        updateNavState()
        return d
    }

    private suspend fun playOffline(ep: OfflineEpisode, generation: Int) {
        idMal = ep.idMal
        summary = AnimeSummary(animeId, ep.title, ep.posterUrl, ep.idMal)
        _title.value = ep.title
        updateNavState()
        // Offline the AniList detail may be unreachable, but the downloaded set is knowable from
        // Room — feed the episode picker and the prev/next enablement from it so a binge of
        // downloaded episodes doesn't require backing out to the Downloads tab between episodes.
        val downloadedNumbers =
            optional { downloads.downloadedEpisodes(animeId).first() }.orEmpty()
        checkSession(generation)
        if (downloadedNumbers.isNotEmpty()) {
            if (_episodes.value.isEmpty()) {
                _episodes.value = downloadedNumbers.sorted().map { n ->
                    Episode(id = "offline-$n", number = n, title = "Episode $n (downloaded)")
                }
            }
            if (!_hasNext.value) _hasNext.value = downloadedNumbers.any { it > episodeNumber }
            if (!_hasPrev.value) _hasPrev.value = downloadedNumbers.any { it < episodeNumber }
        }
        // The duration is normally unknown until the offline media prepares; the tick loop applies
        // the same playable-window filter once Media3 reports it.
        _markers.value = ep.markers
        markersFetched = true   // offline markers come from the cached record; don't re-fetch online.
        play(ep.streamUri, ep.mimeType, ep.subtitles, generation = generation)
    }

    private suspend fun play(uri: String, mimeType: String?, subtitles: List<Subtitle>,
        headers: Map<String, String> = emptyMap(), generation: Int = session.generation) {
        val dataSource = playbackDataSource(headers, offline = _offline.value)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSource)
            .experimentalParseSubtitlesDuringExtraction(false)

        // Build the video item WITHOUT subtitle configs. If subs rode on the MediaItem, Media3's
        // DefaultMediaSourceFactory would auto-merge and prepare them *eagerly* (independent of track
        // selection) — and a sideloaded sub that 404s (ERROR_CODE_IO_BAD_HTTP_STATUS) or whose bytes
        // don't match its declared mime (ERROR_CODE_PARSING_CONTAINER_MALFORMED) would then abort the
        // whole playback. So we attach each sub ourselves below as a fault-isolated source instead.
        val videoItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                when {
                    mimeType != null -> setMimeType(mimeType)
                    uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        val videoSource: MediaSource = mediaSourceFactory.createMediaSource(videoItem)

        // Each sidecar subtitle as its own SingleSampleMediaSource with load errors treated as
        // end-of-stream: a 404/403/timeout fetching a caption degrades that one track to empty
        // instead of failing the video. (Parse errors are made non-fatal separately, by the
        // render-time decoding enabled on the player's renderers factory.) Auto-enable a track on
        // load: DefaultTrackSelector honours SELECTION_FLAG_DEFAULT for text but NEVER reads
        // AUTOSELECT for text, so the first track is DEFAULT (it auto-shows) and the rest AUTOSELECT
        // (the CC button can switch to them). Only ONE track may be DEFAULT. setLabel carries the
        // source's human name ("English", "Spanish - sub") into the CC menu.
        // Resolve each subtitle's real format BEFORE attaching it. Many sources (AnimeOnsen) serve a
        // sidecar at an extension-less URL; declaring it VTT by default makes the WebVTT decoder drop
        // every cue. resolveSubtitleMime sniffs the content when the path has no known suffix. Sniffed
        // in parallel (tiny files) and bounded by a timeout, so it adds at most one short round-trip.
        val subMimes = coroutineScope { subtitles.map { sub -> async { resolveSubtitleMime(sub.url, dataSource) } }.awaitAll() }
        val subFactory = SingleSampleMediaSource.Factory(dataSource)
            .setTreatLoadErrorsAsEndOfStream(true)
        val subSources = subtitles.mapIndexed { index, sub ->
            val flags = if (index == 0) {
                C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT
            } else {
                C.SELECTION_FLAG_AUTOSELECT
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subMimes[index])
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(flags)
                .build()
            // C.TIME_UNSET = a full-file sidecar; cues carry their own timestamps.
            subFactory.createMediaSource(subConfig, C.TIME_UNSET)
        }

        val media: MediaSource =
            if (subSources.isEmpty()) videoSource
            else MergingMediaSource(*(listOf(videoSource) + subSources).toTypedArray())

        checkSession(generation)
        val previous = currentPlayer
        val replacement = createPlayer(dataSource)
        replacement.setPlaybackSpeed(_speed.value)
        replacement.volume = previous.volume
        replacement.trackSelectionParameters = previous.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, _textDisabled.value).build()
        restoreTextChoice = textChoice != null
        textGroups = emptyList()
        _textTracks.value = emptyList()
        currentUri = uri
        _player.value = replacement
        previous.release()
        session.attach()
        replacement.setMediaSource(media, session.positionMs)
        replacement.playWhenReady = session.playWhenReady
        replacement.prepare()
    }

    // Resolve the next episode's stream in the background so the advance/Next transition isn't stalled
    // on a fresh source resolve. One-shot per next-episode (STATE_READY fires repeatedly after seeks).
    private fun prefetchNext() {
        val next = episodeNumber + 1
        if (prefetched?.first == next) return
        prefetched?.second?.cancel()
        val titleText = detail?.title ?: run { prefetched = null; return }
        prefetched = next to viewModelScope.async(Dispatchers.IO) {
            // A failed speculative resolve is optional; cancellation must still stop its requests.
            optional {
                coroutineContext.ensureActive()
                if (!hasEpisode(next)) return@optional null
                if (downloads.getOffline(animeId, next) != null) return@optional null
                // A normal advance may join this exact next-episode resolve after changing generation.
                coroutineContext.ensureActive()
                streams.resolveStream(titleText, next).also { coroutineContext.ensureActive() }
            }
        }
    }

    // Advance to the next episode within this screen when the current one ends, or end
    // the session by dropping it from Continue Watching when there's nothing after it.
    private suspend fun advanceToNextOrFinish() {
        val next = episodeNumber + 1
        if (autoPlayNextEnabled && hasEpisode(next)) {
            playEpisode(next)
        } else {
            summary?.let { progress.remove(it.id) }
        }
    }

    // Reset per-episode state and resolve the given episode's stream into a fresh player.
    // load() itself re-picks offline vs online (and consumes/cancels any prefetch), so a downloaded
    // next episode plays from cache. The memoized title (detail/summary) is NOT reset — same title.
    private suspend fun playEpisode(number: Int) {
        captureProgress()
        val outgoing = summary
        val outgoingEpisode = episodeNumber
        val outgoingPosition = session.positionMs
        val outgoingDuration = session.durationMs
        val saveOutgoing = episodeLoaded
        recovery.reset()
        markersJob?.cancel()
        currentPlayer.pause()
        session.switchEpisode()
        currentPlayer.stop()
        episodeNumber = number
        savedState["episodeNumber"] = number
        resumeFromMs = 0L
        loading = false
        _playWhenReady.value = true
        _isPlaying.value = false
        _markers.value = emptyList()
        markersFetched = false
        firstReadyAtMs = 0L
        autoSkipped.clear()
        _position.value = 0L
        _bufferedPosition.value = 0L
        _duration.value = 0L
        _error.value = null
        _offline.value = false
        currentUri = null
        updateNavState()
        // Keep the captured episode number even if teardown occurs while Room saves this snapshot.
        if (outgoing != null && saveOutgoing) appScope.async {
            persist(outgoing, outgoingEpisode, outgoingPosition, outgoingDuration)
        }.await()
        checkSession(session.generation)
        loadSafely(session.generation)
    }

    // Is there an episode [number] to advance into? A downloaded copy always counts; otherwise it must
    // be within the title's AniList episode count (via the memoized detail, not a fresh fetch).
    private suspend fun hasEpisode(number: Int): Boolean {
        if (number < 1) return false
        if (optional { downloads.getOffline(animeId, number) } != null) return true
        val total = totalEpisodes ?: run { ensureTitle(); totalEpisodes }
        return total != null && number <= total
    }

    private fun updateNavState() {
        _hasPrev.value = episodeNumber > 1
        _hasNext.value = totalEpisodes?.let { episodeNumber < it } ?: false
    }

    private fun maybeAutoSkip(pos: Long) {
        if (!autoSkipEnabled) return
        val active = _markers.value.firstOrNull { pos in it.startMs until it.endMs } ?: return
        if (autoSkipped.add(active.startMs)) seekTo(active.endMs)
    }

    // Fetch AniSkip markers exactly once per episode, but only after a RELIABLE (>0) duration is known
    // (so the length-matched query pins us to the same cut). After a short grace, accept a still-unknown
    // duration (length=0, best-effort) so the Skip control never wedges to "never appears".
    private fun maybeFetchMarkers() {
        if (markersFetched || _offline.value) return
        if (currentPlayer.playbackState != Player.STATE_READY) return
        if (firstReadyAtMs == 0L) firstReadyAtMs = SystemClock.elapsedRealtime()
        val durMs = currentPlayer.duration
        val durKnown = durMs > 0
        if (!durKnown && SystemClock.elapsedRealtime() - firstReadyAtMs < MARKER_GRACE_MS) return
        markersFetched = true   // set before the suspend fetch so the next tick doesn't double-fire.
        val lengthSec = durMs.coerceAtLeast(0) / 1000
        val ep = episodeNumber
        val generation = session.generation
        markersJob = viewModelScope.launch {
            val m = optional { skip.markers(idMal, ep, lengthSec) }.orEmpty()
            checkSession(generation)
            if (!_offline.value) {
                _markers.value = if (durMs > 0L) playableMarkers(m, durMs) else m
            }
        }
    }

    private fun rebuildTextTracks(tracks: Tracks) {
        val ui = mutableListOf<PlayerTextTrack>()
        val groups = mutableListOf<Pair<TrackGroup, Int>>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                ui.add(PlayerTextTrack(f.label ?: f.language ?: "Track ${i + 1}", g.isTrackSelected(i)))
                groups.add(g.mediaTrackGroup to i)
            }
        }
        textGroups = groups
        _textTracks.value = ui
        _textDisabled.value = currentPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        if (restoreTextChoice && groups.isNotEmpty()) {
            restoreTextChoice = false
            val choice = textChoice ?: return
            if (_textDisabled.value) return
            val formats = groups.map { (group, index) -> group.getFormat(index) }
            val byLabel = formats.indexOfFirst { choice.label != null && it.label == choice.label && it.language == choice.language }
            val byLanguage = formats.indexOfFirst { choice.language != null && it.language == choice.language }
            selectTextTrack(when {
                byLabel >= 0 -> byLabel
                byLanguage >= 0 -> byLanguage
                else -> choice.ordinal.coerceIn(0, groups.lastIndex)
            })
        }
    }

    private fun captureProgress() {
        if (session.prepared) session.capture(currentPlayer.currentPosition, currentPlayer.duration)
    }

    private suspend fun saveProgress() {
        if (!episodeLoaded) return   // mid-switch: player position doesn't match episodeNumber yet.
        val s = summary ?: return
        captureProgress()
        persist(s, episodeNumber, session.positionMs, session.durationMs)
    }

    private suspend fun persist(s: AnimeSummary, episode: Int, pos: Long, dur: Long) {
        if (pos <= 0) return
        // Don't record a position in the final stretch — otherwise "Continue" would resume at the
        // credits. Scale the window down for short clips so they still get a resumable point.
        val threshold = if (dur > 0) minOf(END_THRESHOLD_MS, dur / 10) else 0L
        if (dur > 0 && pos >= dur - threshold) return
        progress.save(s, episode, pos, dur)
    }

    // ---- Overlay control surface --------------------------------------------------------------

    fun togglePlay() {
        session.playWhenReady = !session.playWhenReady
        _playWhenReady.value = session.playWhenReady
        currentPlayer.playWhenReady = session.playWhenReady
    }

    fun seekBack() = seekTo(session.positionMs - 10_000L)

    fun seekForward() = seekTo(session.positionMs + 10_000L)

    fun seekTo(positionMs: Long) {
        val target = if (session.durationMs > 0) positionMs.coerceIn(0L, session.durationMs)
            else positionMs.coerceAtLeast(0L)
        // Treat a window the user deliberately seeks into as already consumed, so auto-skip doesn't
        // immediately yank them straight back out of it.
        _markers.value.firstOrNull { target in it.startMs until it.endMs }?.let { autoSkipped.add(it.startMs) }
        session.seek(target)
        if (session.attached) currentPlayer.seekTo(target)
        _position.value = target
    }

    // 'advancing' serializes every episode switch (manual prev/next AND the STATE_ENDED auto-advance),
    // so a double-tap or a tap-then-STATE_ENDED can't launch two concurrent playEpisode/load passes.
    fun goNext() {
        if (advancing || !_hasNext.value) return
        advancing = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch { try { playEpisode(episodeNumber + 1) } finally { advancing = false } }
    }

    fun goPrev() {
        if (advancing || episodeNumber <= 1) return
        advancing = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch { try { playEpisode(episodeNumber - 1) } finally { advancing = false } }
    }

    /**
     * Jump straight to an episode from the in-player picker (same in-place switch as prev/next).
     * No lower bound: the picker only offers episodes that exist, and some series expose an
     * episode 0 (specials) that a `< 1` guard would silently swallow.
     */
    fun jumpTo(number: Int) {
        if (advancing || number == episodeNumber) return
        advancing = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch { try { playEpisode(number) } finally { advancing = false } }
    }

    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 3f)
        currentPlayer.setPlaybackSpeed(s)
        _speed.value = s
    }

    fun selectTextTrack(index: Int) {
        val (group, trackIndex) = textGroups.getOrNull(index) ?: return
        val format = group.getFormat(trackIndex)
        textChoice = TextChoice(format.label, format.language, index)
        restoreTextChoice = false
        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group, trackIndex))
            .build()
        _textDisabled.value = false
    }

    fun disableTextTrack() {
        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _textDisabled.value = true
    }

    /** Manual Skip-Intro/Outro button — jump past the active window. */
    fun seekPast(marker: SkipMarker) {
        autoSkipped.add(marker.startMs)
        seekTo(marker.endMs)
    }

    fun retryPlayback() = recovery.retry()

    override fun onCleared() {
        // viewModelScope is already cancelled here, so persist the final position on the app scope
        // (capturing values before release(), after which the player can't be read).
        val s = summary
        captureProgress()
        val episode = episodeNumber
        val pos = session.positionMs
        val dur = session.durationMs
        cleared = true
        loadJob?.cancel()
        prefetched?.second?.cancel()
        markersJob?.cancel()
        recovery.close()
        // Only persist if the current media matches episodeNumber (not mid-switch) — the outgoing
        // episode was already saved at the top of playEpisode().
        if (s != null && episodeLoaded) appScope.launch { persist(s, episode, pos, dur) }
        currentPlayer.release()
    }

    private companion object {
        const val END_THRESHOLD_MS = 5_000L
        const val MARKER_GRACE_MS = 2_500L
        const val SNIFF_TIMEOUT_MS = 1_500L
    }

    // Resolve a subtitle's MIME: trust a known file extension on the URL path, else sniff the actual
    // bytes (some sources — e.g. AnimeOnsen — serve ASS at an extension-less API URL, and declaring it
    // VTT makes the WebVTT decoder silently drop every cue). The sniff is bounded by a timeout so a
    // slow/hung sidecar can't stall playback start; VTT is the last-resort fallback (a
    // SubtitleConfiguration requires a non-null mime).
    private suspend fun resolveSubtitleMime(url: String, dataSource: CacheDataSource.Factory): String =
        subtitleMimeByPath(url)
            ?: withTimeoutOrNull(SNIFF_TIMEOUT_MS) { sniffSubtitleMime(url, dataSource) }
            ?: MimeTypes.TEXT_VTT

    // Classify by the URL *path* only (ignoring any "?token=…" query), or null if the suffix is unknown.
    private fun subtitleMimeByPath(url: String): String? {
        val path = (Uri.parse(url).path ?: url).lowercase()
        return when {
            path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> null
        }
    }

    // Read the subtitle's first bytes and classify by signature. Goes through the cache-backed factory
    // so it works for both http(s) and offline file:// subs and reuses the stream's request headers.
    // Returns null on any failure (→ caller falls back to VTT).
    private suspend fun sniffSubtitleMime(url: String, dataSource: CacheDataSource.Factory): String? = withContext(Dispatchers.IO) {
        runCatching {
            val ds = dataSource.createDataSource()
            try {
                ds.open(DataSpec(Uri.parse(url)))
                val buf = ByteArray(1024)
                var total = 0
                while (total < buf.size) {
                    val n = ds.read(buf, total, buf.size - total)
                    if (n <= 0) break // RESULT_END_OF_INPUT (-1) or no more bytes
                    total += n
                }
                sniffSubtitleFormat(String(buf, 0, total, Charsets.UTF_8))
            } finally {
                runCatching { ds.close() }
            }
        }.getOrNull()
    }

    private fun sniffSubtitleFormat(raw: String): String? {
        val head = raw.trimStart('\uFEFF').trimStart()
        return when {
            head.startsWith("WEBVTT", ignoreCase = true) -> MimeTypes.TEXT_VTT
            head.startsWith("[Script Info]", ignoreCase = true) ||
                head.contains("[V4+ Styles]", ignoreCase = true) ||
                head.contains("[V4 Styles]", ignoreCase = true) -> MimeTypes.TEXT_SSA
            head.startsWith("<?xml", ignoreCase = true) || head.contains("<tt", ignoreCase = true) ->
                MimeTypes.APPLICATION_TTML
            // Cue-based: SRT uses comma millis ("…,000 -->"), WebVTT uses a dot — distinguish on that.
            head.contains("-->") ->
                if (Regex("\\d{2}:\\d{2}:\\d{2},\\d{3}\\s*-->").containsMatchIn(head)) {
                    MimeTypes.APPLICATION_SUBRIP
                } else {
                    MimeTypes.TEXT_VTT
                }
            else -> null
        }
    }
}
