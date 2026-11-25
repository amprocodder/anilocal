package com.anilocal.app.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

const val DOWNLOAD_CHANNEL_ID = "downloads"

/**
 * Used ONLY when a source supplies no User-Agent of its own. The HTTP factories deliberately set no
 * user-agent (see [DownloadModule.downloadDataSourceFactory]), so without this the connection would
 * send HttpURLConnection's "Dalvik/..." default, which scraper CDNs reject. A source's own UA wins.
 */
const val FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * Builds the Media3 offline-download stack (see DataGraph). The same [Cache] is shared by the
 * [DownloadManager] (writes downloaded bytes) and the player's per-stream read-only
 * `CacheDataSource.Factory` (reads them back offline). Bytes live in app-specific storage.
 */
@OptIn(UnstableApi::class)
internal object DownloadModule {

    fun databaseProvider(context: Context): DatabaseProvider = StandaloneDatabaseProvider(context)

    fun downloadDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")

    fun downloadCache(dir: File, db: DatabaseProvider): Cache =
        SimpleCache(File(dir, "media"), NoOpCacheEvictor(), db)

    /**
     * The DownloadManager's upstream: every manifest/segment request is resolved through
     * [DownloadHeaderStore] so the source's headers (Referer etc.) ride along — including in a
     * headless service-restart process, where the store lazily restores them from Room on the
     * download thread itself. Explicit per-request headers win over the store's.
     *
     * Its http factory is the download stack's OWN instance: the player builds a separate factory
     * per stream with that stream's headers, and while the two shared one, starting playback
     * mid-download clobbered the download's Referer (every segment then 403'd) and vice versa.
     */
    fun downloadDataSourceFactory(headerStore: DownloadHeaderStore): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            // NO setUserAgent. Media3 (1.4.1 through 1.11.1) applies the default request properties
            // and the DataSpec's headers first, THEN
            //     if (userAgent != null) connection.setRequestProperty(USER_AGENT, userAgent);
            // and setRequestProperty REPLACES — so a constructor user-agent silently clobbered the UA
            // the source supplied, which scraper CDNs gate on and a cf_clearance cookie is bound to.
            // Left null, Media3 skips that line; the resolver below fills in a browser default.
            .setAllowCrossProtocolRedirects(true)
        return ResolvingDataSource.Factory(http) { dataSpec ->
            val merged = headerStore.current(dataSpec.uri.host) + dataSpec.httpRequestHeaders
            // The factory forces no user-agent (it would clobber the source's), so supply a browser
            // default only when the source didn't set one.
            val withUa =
                if (merged.keys.any { it.equals("User-Agent", ignoreCase = true) }) merged
                else merged + ("User-Agent" to FALLBACK_USER_AGENT)
            dataSpec.buildUpon().setHttpRequestHeaders(withUa).build()
        }
    }

    fun downloadManager(
        context: Context,
        db: DatabaseProvider,
        cache: Cache,
        upstream: DataSource.Factory,
    ): DownloadManager =
        DownloadManager(context, db, cache, upstream, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 2
        }

    fun notificationHelper(context: Context): DownloadNotificationHelper =
        DownloadNotificationHelper(context, DOWNLOAD_CHANNEL_ID)
}
