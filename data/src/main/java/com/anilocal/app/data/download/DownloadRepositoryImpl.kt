package com.anilocal.app.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.anilocal.app.data.local.DownloadDao
import com.anilocal.app.data.local.DownloadEntity
import com.anilocal.app.data.local.DownloadStateUpdate
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val dao: DownloadDao,
    okHttp: OkHttpClient,
    moshi: Moshi,
    settings: SettingsRepository,
    @Named("downloadDir") private val downloadDir: File,
    @Named("appScope") private val appScope: CoroutineScope,
) : DownloadRepository {

    private val subAdapter =
        moshi.adapter<List<Subtitle>>(Types.newParameterizedType(List::class.java, Subtitle::class.java))
    private val markerAdapter =
        moshi.adapter<List<SkipMarker>>(Types.newParameterizedType(List::class.java, SkipMarker::class.java))
    private val subtitleDownloader = SubtitleDownloader(okHttp, File(downloadDir, "subs"))
    private val operations = KeyedOperationMutex()
    private val writes = Mutex()
    private val writeQueue = DownloadWriteQueue()
    private val writeSignals = Channel<Unit>(Channel.CONFLATED)
    private val listenerReady = CompletableDeferred<Unit>()

    // These fields are used only on the Media3 application thread (the main thread).
    private var polling: Job? = null
    private var reconciliationStarted = false
    private val startup = StartupDownloadReconciliation()
    private val lastPercent = HashMap<String, Int>()
    private val removals = HashMap<String, CompletableDeferred<Unit>>()

    init {
        appScope.launch {
            for (signal in writeSignals) {
                delay(250)
                var failed = false
                writes.withLock {
                    val batch = writeQueue.drain()
                    if (!batch.isEmpty) {
                        try {
                            dao.applyChanges(batch.updates, batch.removedIds)
                            writeQueue.committed(batch)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            writeQueue.retry(batch)
                            failed = true
                            Log.w("AniLocalDownloads", "Could not save download progress", error)
                        }
                    }
                }
                if (failed) {
                    delay(1_000)
                    writeSignals.trySend(Unit)
                }
            }
        }

        appScope.launch(Dispatchers.Main.immediate) {
            try {
                downloadManager.addListener(object : DownloadManager.Listener {
                    override fun onInitialized(dm: DownloadManager) {
                        reconcileNativeDownloads()
                        updatePolling()
                    }

                    override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                        record(download)
                        updatePolling()
                    }

                    override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                        val id = download.request.id
                        startup.touch(id)
                        lastPercent.remove(id)
                        val waiting = removals[id]
                        if (waiting != null) waiting.complete(Unit)
                        else if (writeQueue.remove(id)) writeSignals.trySend(Unit)
                        updatePolling()
                    }
                })
                if (downloadManager.isInitialized) {
                    reconcileNativeDownloads()
                    updatePolling()
                }
                listenerReady.complete(Unit)
                // Changes to another preference must not reconfigure or wake the native manager.
                settings.wifiOnlyDownloads.distinctUntilChanged().collect { wifiOnly ->
                    val network = if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
                    downloadManager.setRequirements(Requirements(network))
                }
            } catch (error: Throwable) {
                listenerReady.completeExceptionally(error)
                throw error
            }
        }
    }

    override val downloads: Flow<List<DownloadItem>> =
        dao.observeAll().distinctUntilChanged().map { list -> list.map { it.toItem() } }
            .distinctUntilChanged().flowOn(Dispatchers.Default)
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val completedAnimeIds: Flow<Set<String>> =
        dao.observeDownloadedAnimeIds().map { it.toSet() }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun downloadedAnimeIds(): Flow<Set<String>> = completedAnimeIds

    override fun downloadedEpisodes(animeId: String): Flow<Set<Int>> =
        dao.observeDownloadedEpisodes(animeId).map { it.toSet() }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Media3 emits state changes, but does not emit callbacks as its byte counter advances. */
    private fun updatePolling() {
        val active = downloadManager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }
        if (active && polling?.isActive != true) {
            polling = appScope.launch(Dispatchers.Main.immediate) {
                while (isActive) {
                    val current = downloadManager.currentDownloads
                    current.forEach(::record)
                    if (current.none { it.state == Download.STATE_DOWNLOADING }) break
                    delay(1_000)
                }
            }
        } else if (!active) {
            polling?.cancel()
            polling = null
        }
    }

    private fun record(download: Download) {
        val id = download.request.id
        startup.touch(id)
        val percent = download.percentDownloaded
        val progress = when {
            download.state == Download.STATE_COMPLETED -> 100
            percent.isFinite() && percent >= 0f -> percent.toInt().coerceIn(0, 100)
            else -> lastPercent[id] ?: 0
        }
        lastPercent[id] = progress
        if (writeQueue.update(DownloadStateUpdate(id, mapState(download.state), progress))) {
            writeSignals.trySend(Unit)
        }
    }

    /** Recover terminal changes that happened while the process/UI repository was absent. */
    private fun reconcileNativeDownloads() {
        if (reconciliationStarted) return
        reconciliationStarted = true
        val index = downloadManager.downloadIndex
        appScope.launch {
            try {
                val (stored, completedRows) = withContext(Dispatchers.IO) {
                    val native = index.getDownloads().use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.download) }
                    }
                    native to dao.getCompletedRows()
                }
                withContext(Dispatchers.Main.immediate) {
                    stored.filter { !startup.wasTouched(it.request.id) }.forEach(::record)
                    // Live state takes precedence over an older index snapshot.
                    downloadManager.currentDownloads.forEach(::record)
                }
                val nativeIds = stored.mapTo(HashSet()) { it.request.id }
                val missing = withContext(Dispatchers.Main.immediate) {
                    startup.orphaned(completedRows, nativeIds, downloadManager.currentDownloads.mapTo(HashSet()) { it.request.id })
                }
                missing.forEach { row ->
                    // Enqueue and removal reserve the same ID. Recheck the startup ledger after
                    // acquiring it, since their intent may have arrived after the snapshot.
                    operations.withLock(row.id) {
                        writes.withLock {
                            val stillMissing = withContext(Dispatchers.Main.immediate) {
                                startup.orphaned(listOf(row), nativeIds,
                                    downloadManager.currentDownloads.mapTo(HashSet()) { it.request.id })
                                    .isNotEmpty()
                            }
                            if (stillMissing) {
                                writeQueue.forget(row.id)
                                // A newly created row or one no longer completed must stay intact.
                                dao.invalidateCompleted(row.id, row.createdAt)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    startup.finish()
                    updatePolling()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    startup.finish()
                }
                Log.w("AniLocalDownloads", "Could not reconcile saved downloads", error)
            }
        }
    }

    override suspend fun enqueue(
        detail: AnimeDetail,
        episode: Episode,
        stream: VideoStream,
        markers: List<SkipMarker>,
    ) {
        val id = "${detail.id}-ep${episode.number}"
        withContext(Dispatchers.Main.immediate) { startup.touch(id) }
        operations.withLock(id) {
            withContext(Dispatchers.IO) {
                listenerReady.await()
                val existing = dao.getById(id)
                if (existing != null && existing.state != STATE_FAILED) return@withContext
                if (existing != null) {
                    val index = withContext(Dispatchers.Main.immediate) { downloadManager.downloadIndex }
                    val native = index.getDownload(id)
                    if (native != null && native.state != Download.STATE_FAILED) return@withContext
                }

                var rowCreated = false
                var nativeQueued = false
                var sidecars = emptyList<DownloadedSubtitle>()
                try {
                    sidecars = subtitleDownloader.downloadAll(stream.subtitles, id, stream.headers)
                    val localSubs = sidecars.map { Subtitle(Uri.fromFile(it.file).toString(), it.subtitle.language, it.subtitle.label) }
                    val entity = DownloadEntity(
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
                        state = STATE_QUEUED,
                        progress = 0,
                        createdAt = System.currentTimeMillis(),
                    )
                    val request = DownloadRequest.Builder(id, Uri.parse(stream.url))
                        .setData(DownloadRequestMetadata.encode(stream.headers))
                        .apply { stream.mimeType?.let { setMimeType(it) } }.build()
                    coroutineContext.ensureActive()
                    // Once the row is committed, navigation cannot leave a queued row without a
                    // matching native download. All cancellable network work has already finished.
                    withContext(NonCancellable) {
                        writes.withLock {
                            writeQueue.forget(id)
                            dao.upsert(entity)
                            rowCreated = true
                        }
                        withContext(Dispatchers.Main.immediate) {
                            DownloadService.sendResumeDownloads(context, AniLocalDownloadService::class.java, true)
                            DownloadService.sendAddDownload(context, AniLocalDownloadService::class.java, request, true)
                        }
                        nativeQueued = true
                    }
                } catch (error: Throwable) {
                    if (!nativeQueued) withContext(NonCancellable + Dispatchers.IO) {
                        sidecars.forEach { it.file.delete() }
                        if (rowCreated) writes.withLock { dao.updateState(id, STATE_FAILED, 0) }
                    }
                    throw error
                }
            }
        }
    }

    override suspend fun getOffline(animeId: String, episodeNumber: Int): OfflineEpisode? =
        withContext(Dispatchers.IO) {
            val e = dao.getById("$animeId-ep$episodeNumber") ?: return@withContext null
            if (e.state != STATE_COMPLETED) return@withContext null
            OfflineEpisode(
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
        appScope.launch(Dispatchers.Main.immediate) {
            listenerReady.await()
            DownloadService.sendSetStopReason(context, AniLocalDownloadService::class.java, id, STOP_REASON_PAUSED, false)
        }
    }

    override fun resume(id: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            listenerReady.await()
            DownloadService.sendSetStopReason(context, AniLocalDownloadService::class.java, id, Download.STOP_REASON_NONE, true)
            // Media3 reuses its helper across service instances, including after an API 35 timeout.
            DownloadService.sendResumeDownloads(context, AniLocalDownloadService::class.java, true)
        }
    }

    override suspend fun remove(id: String) {
        withContext(Dispatchers.Main.immediate) { startup.touch(id) }
        // Deletion must finish even if its screen disappears. Keep the ID reserved until Media3
        // confirms removal so its late callback cannot delete a newly enqueued episode row.
        appScope.async {
            operations.withLock(id) {
                listenerReady.await()
                val index = withContext(Dispatchers.Main.immediate) { downloadManager.downloadIndex }
                val nativeExists = withContext(Dispatchers.IO) { index.getDownload(id) != null }
                val removed = CompletableDeferred<Unit>()
                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (nativeExists) removals[id] = removed
                        DownloadService.sendRemoveDownload(context, AniLocalDownloadService::class.java, id, false)
                    }
                    writes.withLock {
                        writeQueue.forget(id)
                        dao.deleteById(id)
                    }
                    if (nativeExists) removed.await()
                    withContext(Dispatchers.IO) {
                        File(downloadDir, "subs").listFiles { file -> file.name.startsWith("$id-") }
                            ?.forEach { it.delete() }
                    }
                    writes.withLock { writeQueue.forget(id) }
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        if (removals[id] === removed) removals.remove(id)
                    }
                }
            }
        }.await()
    }

    private fun mapState(state: Int): Int = when (state) {
        Download.STATE_COMPLETED -> STATE_COMPLETED
        Download.STATE_FAILED -> STATE_FAILED
        Download.STATE_STOPPED -> STATE_PAUSED
        Download.STATE_QUEUED, Download.STATE_RESTARTING -> STATE_QUEUED
        else -> STATE_DOWNLOADING
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
        const val STOP_REASON_PAUSED = 1
    }
}
