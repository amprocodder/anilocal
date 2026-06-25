package com.anilocal.app.ui.player

import android.content.Context
import android.net.Uri
import android.os.Looper
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
import androidx.media3.datasource.DefaultHttpDataSource
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
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.OfflineEpisode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    // Stored (not just used at player construction) so [play] can build the per-subtitle
    // SingleSampleMediaSource from the same cache-backed factory the video reads through.
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    // The shared upstream HTTP factory the player's CacheDataSource reads through; we set
    // per-stream request headers on it so header-gated sources resolve instead of 403'ing.
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
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

    // Subtitle parsing is kept OFF the extraction path: a sideloaded caption is decoded at render
    // time by the TextRenderer instead, so a malformed/mis-typed sidecar fails *there* (logged, the
    // track just yields no cues) rather than aborting the whole media source with
    // ERROR_CODE_PARSING_CONTAINER_MALFORMED. That needs BOTH halves of Media3 1.4.1's transitional
    // toggle — parse-during-extraction OFF here, and legacy decoding ON in the renderer below.
    private val mediaSourceFactory: DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(cacheDataSourceFactory)
            .experimentalParseSubtitlesDuringExtraction(false)

    // Player reads downloaded bytes from the offline cache, falling through to network online.
    // 1.4.1 has no DefaultRenderersFactory shortcut to enable legacy (render-time) text decoding, so
    // override buildTextRenderers to flip it on the TextRenderer it builds.
    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            @Suppress("DEPRECATION") // transitional in Media3 1.4.x; revisit on a Media3 bump
            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>,
            ) {
                out.add(TextRenderer(output, outputLooper).apply {
                    experimentalSetLegacyDecodingEnabled(true)
                })
            }
        },
    )
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private val _markers = MutableStateFlow<List<SkipMarker>>(emptyList())
    val markers: StateFlow<List<SkipMarker>> = _markers

    // A short, human-readable reason shown over the player when load/resolve/playback fails — so a
    // failure surfaces as a hint instead of an indistinguishable black screen (matches the app's
    // "short hint, no dialogs/spinners" convention). null = no error.
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    val subtitleScale: StateFlow<Float> =
        settings.subtitleScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    val subtitleBackground: StateFlow<Boolean> =
        settings.subtitleBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var summary: AnimeSummary? = null
    private var idMal: Int? = null
    // Last URI handed to the player, appended to playback errors so an unparseable/odd stream URL is
    // visible for diagnosis (device logcat isn't reachable for sideload users).
    private var currentUri: String? = null
    private var autoSkipEnabled = true
    private val autoSkipped = mutableSetOf<Long>()

    init {
        viewModelScope.launch { settings.autoSkip.collect { autoSkipEnabled = it } }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Online path resolves skip windows once duration is known.
                if (state == Player.STATE_READY && _markers.value.isEmpty() && !_offline.value) {
                    val lengthSec = player.duration.coerceAtLeast(0) / 1000
                    viewModelScope.launch {
                        _markers.value = runCatching { skip.markers(idMal, episodeNumber, lengthSec) }
                            .getOrDefault(emptyList())
                    }
                } else if (state == Player.STATE_ENDED) {
                    // Finished — drop it from Continue Watching so it doesn't linger near 100%.
                    summary?.let { s -> viewModelScope.launch { progress.remove(s.id) } }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Capture the resume point when the user pauses (scope still alive here; the final
                // save on teardown is handled separately in onCleared via appScope).
                if (!isPlaying) viewModelScope.launch { saveProgress() }
            }

            override fun onPlayerError(e: PlaybackException) {
                // A transport/decode failure (e.g. the stream URL won't load) would otherwise leave
                // the surface black with no signal. Surface why — plus the URL — so it's diagnosable.
                _error.value = "Playback failed: ${e.errorCodeName}" +
                    (currentUri?.let { "\n${it.take(160)}" } ?: "")
            }
        })

        // Crash-proof the whole load+play path: a synchronous throw here (e.g. setMediaItem building
        // a media source for a format whose Media3 module isn't bundled, like DASH) would otherwise
        // take down the app. Surface it as a hint instead — the message also names the real cause.
        viewModelScope.launch {
            runCatching { load() }
                .onFailure { _error.value = "Couldn't start playback: ${it.message ?: it.javaClass.simpleName}" }
        }

        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val pos = player.currentPosition
                _position.value = pos
                maybeAutoSkip(pos)
                if (++tick % 15 == 0) saveProgress()   // ~ every 6s
                delay(400)
            }
        }
    }

    private suspend fun load() {
        val cached = runCatching { downloads.getOffline(animeId, episodeNumber) }.getOrNull()
        if (cached != null) {
            _offline.value = true
            playOffline(cached)
            return
        }
        // Online: AniList detail → resolve stream → (AniSkip markers come on STATE_READY).
        // Don't swallow failures into a silent return — a blank surface with no reason is the bug we
        // keep hitting. Surface a short hint so the failing leg (metadata vs. stream) is obvious.
        val detail = runCatching { catalog.detail(animeId) }
            .getOrElse { _error.value = "Couldn't load title details (no connection?)"; return }
        idMal = detail.idMal
        summary = AnimeSummary(detail.id, detail.title, detail.posterUrl, detail.idMal)
        val stream = runCatching { streams.resolveStream(detail.title, episodeNumber) }
            .getOrElse { _error.value = "No stream from the selected source for \"${detail.title}\""; return }
        play(stream.url, stream.mimeType, stream.subtitles, stream.headers)
    }

    private fun playOffline(ep: OfflineEpisode) {
        idMal = ep.idMal
        summary = AnimeSummary(animeId, ep.title, ep.posterUrl, ep.idMal)
        _markers.value = ep.markers
        play(ep.streamUri, ep.mimeType, ep.subtitles)
    }

    private fun play(uri: String, mimeType: String?, subtitles: List<Subtitle>, headers: Map<String, String> = emptyMap()) {
        // Apply the source-provided request headers (Referer/User-Agent/etc.) to the shared upstream
        // HTTP factory the player reads through. Set every time (empty clears the previous stream's
        // headers) and before prepare(), since the data source reads these at open() time. Many real
        // sources 403 without their Referer — this is what lets a header-gated stream resolve.
        httpDataSourceFactory.setDefaultRequestProperties(headers)
        currentUri = uri

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
                    uri.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
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
        val subFactory = SingleSampleMediaSource.Factory(cacheDataSourceFactory)
            .setTreatLoadErrorsAsEndOfStream(true)
        val subSources = subtitles.mapIndexed { index, sub ->
            val flags = if (index == 0) {
                C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT
            } else {
                C.SELECTION_FLAG_AUTOSELECT
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subtitleMime(sub.url))
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

        if (startMs > 0) player.setMediaSource(media, startMs) else player.setMediaSource(media)
        player.prepare()
        player.playWhenReady = true
    }

    private fun maybeAutoSkip(pos: Long) {
        if (!autoSkipEnabled) return
        val active = _markers.value.firstOrNull { pos in it.startMs until it.endMs } ?: return
        if (autoSkipped.add(active.startMs)) player.seekTo(active.endMs)
    }

    private suspend fun saveProgress() {
        val s = summary ?: return
        persist(s, player.currentPosition, player.duration.coerceAtLeast(0))
    }

    private suspend fun persist(s: AnimeSummary, pos: Long, dur: Long) {
        if (pos <= 0) return
        // Don't record a position in the final stretch — otherwise "Continue" would resume at the
        // credits. Scale the window down for short clips so they still get a resumable point.
        val threshold = if (dur > 0) minOf(END_THRESHOLD_MS, dur / 10) else 0L
        if (dur > 0 && pos >= dur - threshold) return
        progress.save(s, episodeNumber, pos, dur)
    }

    fun seekPast(marker: SkipMarker) = player.seekTo(marker.endMs)

    override fun onCleared() {
        // viewModelScope is already cancelled here, so persist the final position on the app scope
        // (capturing values before release(), after which the player can't be read).
        val s = summary
        val pos = player.currentPosition
        val dur = player.duration.coerceAtLeast(0)
        if (s != null) appScope.launch { persist(s, pos, dur) }
        player.release()
    }

    private companion object {
        const val END_THRESHOLD_MS = 5_000L
    }

    // Classify by the URL *path* only. Many sources serve sidecars behind a query/proxy
    // ("…/sub.srt?token=…"), so matching the raw URL string would leave a real SubRip in the VTT
    // fallback and the WebVTT parser would silently drop every cue. VTT stays the last-resort fallback
    // (a SubtitleConfiguration requires a non-null mime), but only after the path has no known suffix.
    private fun subtitleMime(url: String): String {
        val path = (Uri.parse(url).path ?: url).lowercase()
        return when {
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }
}
