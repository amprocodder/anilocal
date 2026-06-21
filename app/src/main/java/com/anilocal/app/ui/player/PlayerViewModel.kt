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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anilocal.app.domain.model.AnimeSummary
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    cacheDataSourceFactory: CacheDataSource.Factory,
    private val catalog: CatalogRepository,
    private val streams: StreamRepository,
    private val skip: SkipRepository,
    private val progress: ProgressRepository,
    private val downloads: DownloadRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val animeId: String = checkNotNull(savedState["animeId"])
    private val episodeNumber: Int = checkNotNull(savedState["episodeNumber"])

    // Player reads downloaded bytes from the offline cache, falling through to network online.
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .build()

    private val _markers = MutableStateFlow<List<SkipMarker>>(emptyList())
    val markers: StateFlow<List<SkipMarker>> = _markers

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
                }
            }
        })

        viewModelScope.launch { load() }

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
        val cached = downloads.getOffline(animeId, episodeNumber)
        if (cached != null) {
            _offline.value = true
            playOffline(cached)
            return
        }
        // Online: AniList detail → resolve stream → (AniSkip markers come on STATE_READY).
        val detail = runCatching { catalog.detail(animeId) }.getOrNull() ?: return
        idMal = detail.idMal
        summary = AnimeSummary(detail.id, detail.title, detail.posterUrl, detail.idMal)
        val stream = runCatching { streams.resolveStream(detail.title, episodeNumber) }.getOrNull() ?: return
        play(stream.url, stream.mimeType, stream.subtitles)
    }

    private fun playOffline(ep: OfflineEpisode) {
        idMal = ep.idMal
        summary = AnimeSummary(animeId, ep.title, null, ep.idMal)
        _markers.value = ep.markers
        play(ep.streamUri, ep.mimeType, ep.subtitles)
    }

    private fun play(uri: String, mimeType: String?, subtitles: List<Subtitle>) {
        val subConfigs = subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subtitleMime(sub.url))
                .setLanguage(sub.language)
                .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subConfigs)
            .apply {
                when {
                    mimeType != null -> setMimeType(mimeType)
                    uri.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        player.setMediaItem(item)
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
        if (player.currentPosition > 0) {
            progress.save(s, episodeNumber, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    fun seekPast(marker: SkipMarker) = player.seekTo(marker.endMs)

    override fun onCleared() {
        player.release()
    }

    private fun subtitleMime(url: String): String = when {
        url.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        url.endsWith(".ass", true) || url.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}
