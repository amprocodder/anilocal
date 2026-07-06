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
        DefaultHttpDataSource.Factory().setUserAgent("AniLocal").setAllowCrossProtocolRedirects(true)

    /** DOWNLOAD http factory — only reached through [downloadDataSourceFactory]'s header resolver. */
    @OptIn(UnstableApi::class)
    @Provides @Singleton @Named("downloadHttp")
    fun downloadHttpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setUserAgent("AniLocal").setAllowCrossProtocolRedirects(true)

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
            val headers = headerStore.current()
            if (headers.isEmpty()) dataSpec
            else dataSpec.buildUpon().setHttpRequestHeaders(headers + dataSpec.httpRequestHeaders).build()
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
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
}
