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
import com.anilocal.app.domain.repo.StreamRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    // Per-request headers for the download stack: the DownloadManager's data-source chain resolves
    // every manifest/segment request through this store (see DownloadModule), so header-gated
    // sources fetch instead of 403'ing — in this process or a headless service restart.
    private val headerStore: DownloadHeaderStore,
    private val dao: DownloadDao,
    private val okHttp: OkHttpClient,
    // For retry(): a failed download's URL is usually an expired token, so retrying means
    // re-resolving a fresh stream from the active source, not re-fetching the dead URL.
    private val streams: StreamRepository,
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
    private val headerAdapter =
        moshi.adapter<Map<String, String>>(
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        )

    // Retry machinery: one retry at a time (polite to the source — a season's worth of failures
    // arriving together must not stampede it), a bounded per-process auto-retry budget, and the
    // ids whose Media3 removal is part of an in-place retry (their Room row must survive it).
    private val retryMutex = Mutex()
    private val retrying = ConcurrentHashMap.newKeySet<String>()
    private val autoRetryCounts = ConcurrentHashMap<String, Int>()

    init {
        // Bridge Media3 download state → Room so the UI (which reads only Room) stays offline-safe.
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                val progress = download.percentDownloaded.toInt().coerceIn(0, 100)
                scope.launch { dao.updateState(download.request.id, mapState(download.state), progress) }
                // Auto-retry the terminal failure: with 2 parallel slots, a queued episode's
                // tokenized URL has often expired by the time it gets a slot — a fresh resolve
                // succeeds where re-fetching the dead URL never can. Bounded per process; the
                // manual Retry button (which re-arms this budget) remains for the rest.
                if (download.state == Download.STATE_FAILED) {
                    val id = download.request.id
                    // Sideload builds have no logcat access for the user; make the real cause
                    // (UnknownHostException = dead/expired host, 403 = bad headers, timeout, …)
                    // visible so a stuck-download report is diagnosable from `adb logcat`.
                    Log.w(TAG, "Download failed: $id host=${download.request.uri.host} → " +
                        "${finalException?.javaClass?.simpleName}: ${finalException?.message}")
                    val attempt = autoRetryCounts.merge(id, 1, Int::plus) ?: 1
                    if (attempt <= MAX_AUTO_RETRIES) {
                        scope.launch {
                            delay(AUTO_RETRY_BASE_DELAY_MS * attempt)
                            runCatching { doRetry(id) }
                        }
                    } else {
                        Log.w(TAG, "Download $id exhausted $MAX_AUTO_RETRIES auto-retries; left FAILED for manual retry")
                    }
                }
            }

            override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                // An in-place retry removes the old Media3 download (purging its stale bytes) and
                // immediately re-adds under the same id — that removal must not delete the row.
                if (retrying.remove(download.request.id)) return
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

        val localSubs = fetchLocalSubs(stream, id)

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
                headersJson = headerAdapter.toJson(stream.headers),
            )
        )

        // Point the header store at this stream before enqueuing, so the manifest and every segment
        // fetch with the source's headers. Adaptive (HLS/DASH/SS) requests carry the
        // mimeType but no stream keys, so the segment downloader pulls every rendition (the
        // DownloadQuality setting isn't applied to adaptive track selection yet — progressive only).
        headerStore.set(stream.headers)
        val request = DownloadRequest.Builder(id, Uri.parse(stream.url))
            .apply { stream.mimeType?.let { setMimeType(it) } }
            .build()
        try {
            DownloadService.sendAddDownload(context, AniLocalDownloadService::class.java, request, /* foreground= */ true)
        } catch (e: Exception) {
            // Season passes enqueue from appScope, possibly with the app backgrounded — where an
            // API 31+ foreground-service start throws. Hand the request straight to the shared
            // DownloadManager instead (the service binds the same instance and takes over on its
            // next start). If even that fails, drop the Room row we just wrote so no phantom
            // "Downloading 0%" entry survives, and let the caller count the episode as failed.
            runCatching { downloadManager.addDownload(request) }.onFailure {
                dao.deleteById(id)
                throw it
            }
        }
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

    override suspend fun retry(id: String): Boolean {
        autoRetryCounts.remove(id)   // a deliberate user retry re-arms the auto budget
        return doRetry(id)
    }

    // Re-resolve → refetch subs → swap the Media3 download in place (same id, fresh URL). The Room
    // row is REWRITTEN, never deleted, so the episode keeps its place in the Downloads UI; on any
    // resolution failure the row is left untouched (still FAILED, still retryable).
    private suspend fun doRetry(id: String): Boolean = withContext(Dispatchers.IO) {
        retryMutex.withLock {
            // Only a row that is STILL failed is retryable: a delayed auto-retry (or a queued
            // second tap) must not purge and restart a download that has meanwhile been fixed by
            // a manual retry, re-enqueued by a season re-press, or even completed.
            val e = dao.getById(id)?.takeIf { it.state == STATE_FAILED } ?: return@withLock false
            val variants = runCatching { streams.resolveStreams(e.title, e.episodeNumber) }
                .onFailure { Log.w(TAG, "Retry $id: re-resolve failed: ${it.message}") }
                .getOrNull() ?: return@withLock false
            val stream = pickVariant(variants, e.quality) ?: run {
                Log.w(TAG, "Retry $id: re-resolve returned no usable variant"); return@withLock false
            }
            val localSubs = fetchLocalSubs(stream, id)

            // The resolve/subs legs above are seconds of network work — re-check that the row
            // survived them (a user delete mid-retry must win; without this the upsert below
            // would resurrect a removed episode) and that nothing else took the download over.
            // Past this point everything is local and fast, so the remaining window is ~ms.
            if (dao.getById(id)?.state != STATE_FAILED) return@withLock false
            Log.i(TAG, "Retry $id: re-resolved to host=${Uri.parse(stream.url).host}, re-adding")

            // Swap the download in place through the DownloadManager DIRECTLY — NOT via the
            // foreground-service intents. Firing startForegroundService for every retry (a season
            // of simultaneous failures = a burst of them) trips the platform's background-FGS /
            // "Stop FGS timeout" limiter, which tears the service down and stalls the healthy
            // downloads sharing it. The manager restarts/maintains the service on its own when a
            // download is present. The `retrying` flag makes the resulting async onDownloadRemoved
            // skip the row delete (this is a rewrite, not a removal).
            retrying.add(id)
            downloadManager.removeDownload(id)

            dao.upsert(
                e.copy(
                    streamUri = stream.url,
                    mimeType = stream.mimeType,
                    quality = stream.quality ?: e.quality,
                    subtitlesJson = subAdapter.toJson(localSubs),
                    state = STATE_DOWNLOADING,
                    progress = 0,
                    headersJson = headerAdapter.toJson(stream.headers),
                )
            )
            headerStore.set(stream.headers)

            val request = DownloadRequest.Builder(id, Uri.parse(stream.url))
                .apply { stream.mimeType?.let { setMimeType(it) } }
                .build()
            downloadManager.addDownload(request)
            true
        }
    }

    /**
     * Re-pick the originally chosen quality from a fresh variant list, tolerantly: exact label
     * match, else the best variant not above the label's height, else the lowest one (everything
     * now exceeds it), else the best available. [variants] arrive best-first.
     */
    private fun pickVariant(variants: List<VideoStream>, quality: String?): VideoStream? {
        if (variants.isEmpty()) return null
        if (quality == null) return variants.first()
        variants.firstOrNull { it.quality == quality }?.let { return it }
        val wanted = quality.filter { it.isDigit() }.toIntOrNull() ?: return variants.first()
        return variants.filter { (it.height ?: 0) <= wanted }.maxByOrNull { it.height ?: 0 }
            ?: variants.lastOrNull()
    }

    override fun resume(id: String) {
        // Point the header store at THIS download (it may still hold another stream's headers).
        // Async is fine: the store is read per-request on the download thread, and this tiny Room
        // read almost always lands before the service processes the resume intent.
        scope.launch { runCatching { headerStore.setFromJson(dao.getById(id)?.headersJson) } }
        // Send synchronously from the tap handler (app is foreground). If a background start still
        // slips through on API 31+, fall back to the shared manager directly — same pattern and
        // reason as enqueue()'s fallback.
        try {
            DownloadService.sendSetStopReason(
                context, AniLocalDownloadService::class.java, id, Download.STOP_REASON_NONE, /* foreground= */ true,
            )
        } catch (e: Exception) {
            runCatching { downloadManager.setStopReason(id, Download.STOP_REASON_NONE) }
        }
    }

    override suspend fun remove(id: String) {
        DownloadService.sendRemoveDownload(context, AniLocalDownloadService::class.java, id, false)
        withContext(Dispatchers.IO) {
            // Match on the id PLUS its delimiter — sidecars are "$id-$index.$ext", so a bare
            // startsWith(id) would let "anime-ep1" also match "anime-ep10-0.srt" and wipe episode 10's
            // (11's, …) subtitles when episode 1 is removed.
            File(downloadDir, "subs").listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
        }
        dao.deleteById(id)
    }

    /**
     * Pull sidecar subtitles into app storage and rewrite their URLs to local file:// uris. The
     * stream's request headers ride along — subtitle CDNs are often gated on the same Referer as
     * the video, and a header-less fetch 403s (which used to mean the episode downloaded with no
     * captions at all). Any previous attempt's sidecars for this id are dropped first, so a retry
     * can't leave orphans behind when the new stream names/formats its subs differently.
     */
    private suspend fun fetchLocalSubs(stream: VideoStream, id: String): List<Subtitle> {
        withContext(Dispatchers.IO) {
            File(downloadDir, "subs").listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
        }
        return stream.subtitles.mapIndexedNotNull { index, sub ->
            runCatching {
                val file = downloadSubtitle(sub, id, index, stream.headers)
                Subtitle(Uri.fromFile(file).toString(), sub.language, sub.label)
            }.getOrNull()
        }
    }

    private suspend fun downloadSubtitle(sub: Subtitle, id: String, index: Int, headers: Map<String, String>): File =
        withContext(Dispatchers.IO) {
            val dir = File(downloadDir, "subs").apply { mkdirs() }
            // Derive the extension from the URL PATH, not the raw URL: "…/sub.srt?token=abc" must yield
            // "srt", not "srt?token=abc" — the latter both makes an odd/illegal filename and defeats the
            // path-based mime classifier when the cached file:// uri is replayed offline.
            val ext = (Uri.parse(sub.url).path ?: sub.url).substringAfterLast('.', "vtt").take(5)
            val file = File(dir, "$id-$index.$ext")
            val request = Request.Builder().url(sub.url).apply {
                // Per-header runCatching: one source-supplied header OkHttp rejects (odd chars)
                // must not cost the whole subtitle.
                headers.forEach { (k, v) -> runCatching { header(k, v) } }
            }.build()
            okHttp.newCall(request).execute().use { resp ->
                // Without this check an error page would persist as a "valid" subtitle sidecar and
                // render as garbage cues offline; throwing lets the caller drop just this track.
                if (!resp.isSuccessful) error("subtitle fetch failed (HTTP ${resp.code})")
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
        const val MAX_AUTO_RETRIES = 4               // per download id, per process
        const val AUTO_RETRY_BASE_DELAY_MS = 4_000L  // 4s, 8s, 12s, 16s — outlives a transient blip
        const val TAG = "AniLocalDownloads"
    }
}
