package com.anilocal.app.data.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.anilocal.app.data.local.DownloadDao
import com.anilocal.app.data.local.DownloadEntity
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.DownloadItem
import com.anilocal.app.domain.model.DownloadState
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.OfflineEpisode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    // The same HTTP factory the DownloadManager downloads through; we set the source's request
    // headers on it so header-gated manifests/segments fetch instead of 403'ing (mirrors playback).
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
    private val dao: DownloadDao,
    private val okHttp: OkHttpClient,
    moshi: Moshi,
    settings: SettingsRepository,
    @Named("downloadDir") private val downloadDir: File,
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val subAdapter =
        moshi.adapter<List<Subtitle>>(Types.newParameterizedType(List::class.java, Subtitle::class.java))
    private val markerAdapter =
        moshi.adapter<List<SkipMarker>>(Types.newParameterizedType(List::class.java, SkipMarker::class.java))

    init {
        // Bridge Media3 download state → Room so the UI (which reads only Room) stays offline-safe.
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                val progress = download.percentDownloaded.toInt().coerceIn(0, 100)
                scope.launch { dao.updateState(download.request.id, mapState(download.state), progress) }
            }

            override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                scope.launch { dao.deleteById(download.request.id) }
            }
        })

        // WiFi-only setting → DownloadManager requirements. The manager watches connectivity
        // and automatically pauses (STATE_QUEUED) / resumes downloads as the network changes.
        mainScope.launch {
            settings.wifiOnlyDownloads.collect { wifiOnly ->
                val network = if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
                downloadManager.setRequirements(Requirements(network))
            }
        }
    }

    override val downloads: Flow<List<DownloadItem>> =
        dao.observeAll().map { list -> list.map { it.toItem() } }

    override fun downloadedAnimeIds(): Flow<Set<String>> =
        dao.observeDownloadedAnimeIds().map { it.toSet() }

    override fun downloadedEpisodes(animeId: String): Flow<Set<Int>> =
        dao.observeDownloadedEpisodes(animeId).map { it.toSet() }

    override suspend fun enqueue(
        detail: AnimeDetail,
        episode: Episode,
        stream: VideoStream,
        markers: List<SkipMarker>,
    ) {
        val id = "${detail.id}-ep${episode.number}"

        // Pull sidecar subtitles into app storage and rewrite their URLs to local file:// uris.
        val localSubs = stream.subtitles.mapIndexedNotNull { index, sub ->
            runCatching {
                val file = downloadSubtitle(sub, id, index)
                Subtitle(Uri.fromFile(file).toString(), sub.language, sub.label)
            }.getOrNull()
        }

        dao.upsert(
            DownloadEntity(
                id = id,
                animeId = detail.id,
                episodeNumber = episode.number,
                title = detail.title,
                posterUrl = detail.posterUrl,
                idMal = detail.idMal,
                streamUri = stream.url,
                mimeType = stream.mimeType,
                quality = stream.quality,
                subtitlesJson = subAdapter.toJson(localSubs),
                skipMarkersJson = markerAdapter.toJson(markers),
                state = STATE_DOWNLOADING,
                progress = 0,
                createdAt = System.currentTimeMillis(),
            )
        )

        // Apply the source's request headers to the shared download HTTP factory before enqueuing, so
        // the manifest and every segment fetch with them. Adaptive (HLS/DASH/SS) requests carry the
        // mimeType but no stream keys, so the segment downloader pulls every rendition (the
        // DownloadQuality setting isn't applied to adaptive track selection yet — progressive only).
        httpDataSourceFactory.setDefaultRequestProperties(stream.headers)
        val request = DownloadRequest.Builder(id, Uri.parse(stream.url))
            .apply { stream.mimeType?.let { setMimeType(it) } }
            .build()
        DownloadService.sendAddDownload(context, AniLocalDownloadService::class.java, request, /* foreground= */ true)
    }

    override suspend fun getOffline(animeId: String, episodeNumber: Int): OfflineEpisode? {
        val e = dao.getById("$animeId-ep$episodeNumber") ?: return null
        if (e.state != STATE_COMPLETED) return null
        return OfflineEpisode(
            streamUri = e.streamUri,
            mimeType = e.mimeType,
            title = e.title,
            posterUrl = e.posterUrl,
            episodeNumber = e.episodeNumber,
            idMal = e.idMal,
            subtitles = runCatching { subAdapter.fromJson(e.subtitlesJson) }.getOrNull().orEmpty(),
            markers = runCatching { markerAdapter.fromJson(e.skipMarkersJson) }.getOrNull().orEmpty(),
        )
    }

    override fun pause(id: String) {
        DownloadService.sendSetStopReason(
            context, AniLocalDownloadService::class.java, id, STOP_REASON_PAUSED, /* foreground= */ false,
        )
    }

    override fun resume(id: String) {
        DownloadService.sendSetStopReason(
            context, AniLocalDownloadService::class.java, id, Download.STOP_REASON_NONE, /* foreground= */ true,
        )
    }

    override suspend fun remove(id: String) {
        DownloadService.sendRemoveDownload(context, AniLocalDownloadService::class.java, id, false)
        withContext(Dispatchers.IO) {
            File(downloadDir, "subs").listFiles { f -> f.name.startsWith(id) }?.forEach { it.delete() }
        }
        dao.deleteById(id)
    }

    private suspend fun downloadSubtitle(sub: Subtitle, id: String, index: Int): File =
        withContext(Dispatchers.IO) {
            val dir = File(downloadDir, "subs").apply { mkdirs() }
            val ext = sub.url.substringAfterLast('.', "vtt").take(5)
            val file = File(dir, "$id-$index.$ext")
            okHttp.newCall(Request.Builder().url(sub.url).build()).execute().use { resp ->
                file.outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
            }
            file
        }

    private fun mapState(state: Int): Int = when (state) {
        Download.STATE_COMPLETED -> STATE_COMPLETED
        Download.STATE_FAILED -> STATE_FAILED
        Download.STATE_STOPPED -> STATE_PAUSED      // manual stopReason set
        Download.STATE_QUEUED -> STATE_QUEUED       // waiting (e.g. for WiFi)
        else -> STATE_DOWNLOADING                   // DOWNLOADING / REMOVING / RESTARTING
    }

    private fun DownloadEntity.toItem() = DownloadItem(
        id = id,
        animeId = animeId,
        episodeNumber = episodeNumber,
        title = title,
        posterUrl = posterUrl,
        idMal = idMal,
        state = when (state) {
            STATE_COMPLETED -> DownloadState.COMPLETED
            STATE_FAILED -> DownloadState.FAILED
            STATE_PAUSED -> DownloadState.PAUSED
            STATE_QUEUED -> DownloadState.QUEUED
            else -> DownloadState.DOWNLOADING
        },
        progress = progress,
        quality = quality,
    )

    private companion object {
        const val STATE_DOWNLOADING = 0
        const val STATE_COMPLETED = 1
        const val STATE_FAILED = 2
        const val STATE_PAUSED = 3
        const val STATE_QUEUED = 4
        const val STOP_REASON_PAUSED = 1   // any non-zero stop reason pauses a download
    }
}
