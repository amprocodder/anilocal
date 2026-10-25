package com.anilocal.app.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

const val DOWNLOAD_CHANNEL_ID = "downloads"

/**
 * Used ONLY when a source supplies no User-Agent of its own. The HTTP factories deliberately set no
 * user-agent (see [DownloadModule.downloadHttpFactory]), so without this the connection would send
 * HttpURLConnection's "Dalvik/..." default, which scraper CDNs reject. A source's own UA always wins.
 */
const val FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * Provides the Media3 offline-download stack. The same [Cache] is shared by the
 * [DownloadManager] (writes downloaded bytes) and the playback [CacheDataSource.Factory]
 * (reads them back offline). Bytes live in app-specific storage.
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun databaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides @Singleton @Named("downloadDir")
    fun downloadDir(@ApplicationContext context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun downloadCache(@Named("downloadDir") dir: File, db: DatabaseProvider): Cache =
        SimpleCache(File(dir, "media"), NoOpCacheEvictor(), db)

    /**
     * PLAYBACK http factory (the unqualified one PlayerViewModel and the CacheDataSource read
     * through). Deliberately a DIFFERENT instance from [downloadHttpFactory]: both the player and
     * the download stack set per-stream default request headers on their factory, and while they
     * shared one instance, starting playback mid-download clobbered the download's Referer (each
     * segment fetch then 403'd) and vice versa.
     */
    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun httpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)   // see downloadHttpFactory: no user-agent on purpose

    /** DOWNLOAD http factory — only reached through [downloadDataSourceFactory]'s header resolver. */
    @OptIn(UnstableApi::class)
    @Provides @Singleton @Named("downloadHttp")
    fun downloadHttpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            // NO setUserAgent. Media3 1.4.1's makeConnection() applies the default request properties
            // and the DataSpec's headers first, THEN
            //     if (userAgent != null) connection.setRequestProperty(USER_AGENT, userAgent);
            // and setRequestProperty REPLACES — so a constructor user-agent silently clobbered the UA
            // the source supplied, which scraper CDNs gate on and a cf_clearance cookie is bound to.
            // Left null, Media3 skips that line; the resolver below fills in a browser default.
            .setAllowCrossProtocolRedirects(true)

    /**
     * The DownloadManager's upstream: every manifest/segment request is resolved through
     * [DownloadHeaderStore] so the source's headers (Referer etc.) ride along — including in a
     * headless service-restart process, where the store lazily restores them from Room on the
     * download thread itself. Explicit per-request headers win over the store's.
     */
    @OptIn(UnstableApi::class)
    @Provides @Singleton @Named("downloadDataSource")
    fun downloadDataSourceFactory(
        @Named("downloadHttp") http: DefaultHttpDataSource.Factory,
        headerStore: DownloadHeaderStore,
    ): DataSource.Factory =
        ResolvingDataSource.Factory(http) { dataSpec ->
            val merged = headerStore.current(dataSpec.uri.host) + dataSpec.httpRequestHeaders
            // The factory forces no user-agent (it would clobber the source's), so supply a browser
            // default only when the source didn't set one.
            val withUa =
                if (merged.keys.any { it.equals("User-Agent", ignoreCase = true) }) merged
                else merged + ("User-Agent" to FALLBACK_USER_AGENT)
            dataSpec.buildUpon().setHttpRequestHeaders(withUa).build()
        }

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        db: DatabaseProvider,
        cache: Cache,
        @Named("downloadDataSource") upstream: DataSource.Factory,
    ): DownloadManager =
        DownloadManager(context, db, cache, upstream, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 2
        }

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun notificationHelper(@ApplicationContext context: Context): DownloadNotificationHelper =
        DownloadNotificationHelper(context, DOWNLOAD_CHANNEL_ID)

    /** Playback factory: serve cached bytes offline, fall through to http/file for the rest. */
    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun cacheDataSourceFactory(
        @ApplicationContext context: Context,
        cache: Cache,
        http: DefaultHttpDataSource.Factory,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(null)   // read-only: this cache IS the user's downloads
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
}
