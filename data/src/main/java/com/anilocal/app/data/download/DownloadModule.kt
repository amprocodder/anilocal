package com.anilocal.app.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun httpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setUserAgent("AniLocal").setAllowCrossProtocolRedirects(true)

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        db: DatabaseProvider,
        cache: Cache,
        http: DefaultHttpDataSource.Factory,
    ): DownloadManager =
        DownloadManager(context, db, cache, http, Executors.newFixedThreadPool(3)).apply {
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
