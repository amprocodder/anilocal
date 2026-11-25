package com.anilocal.app.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.data.download.DownloadHeaderStore
import com.anilocal.app.data.download.DownloadModule
import com.anilocal.app.data.download.DownloadRepositoryImpl
import com.anilocal.app.data.local.DatabaseModule
import com.anilocal.app.data.local.RoomLibraryRepository
import com.anilocal.app.data.local.RoomProgressRepository
import com.anilocal.app.data.metadata.anilist.AniListApi
import com.anilocal.app.data.metadata.anilist.AniListCatalogRepository
import com.anilocal.app.data.metadata.anilist.CachedCatalogRepository
import com.anilocal.app.data.metadata.extension.ExtensionRepoApi
import com.anilocal.app.data.metadata.extension.ExtensionRepositoryImpl
import com.anilocal.app.data.metadata.mal.MalApi
import com.anilocal.app.data.metadata.mal.MalRepositoryImpl
import com.anilocal.app.data.remote.JsonHttp
import com.anilocal.app.data.remote.NetworkModule
import com.anilocal.app.data.settings.SettingsRepositoryImpl
import com.anilocal.app.data.skip.AniSkipApi
import com.anilocal.app.data.skip.AniSkipRepository
import com.anilocal.app.data.source.AutoSourceSelector
import com.anilocal.app.data.source.SourceRegistryImpl
import com.anilocal.app.data.source.SourceStreamRepository
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.ExtensionRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Implemented by the Application, so components the framework instantiates inside :data (the
 * download service) can reach the graph without a DI framework.
 */
interface DataGraphHolder {
    val dataGraph: DataGraph
}

/**
 * The data layer's object graph: every repository, client and database, each created once, on first
 * use (`lazy` is synchronized, so concurrent first uses still get one instance), in dependency order.
 * It replaces Hilt: the wiring is written out here instead of generated, there's no annotation
 * processing, and what depends on what is readable in one file. The public surface is domain
 * interfaces plus the few framework types the app layer needs; the implementations stay internal
 * to this module's wiring.
 *
 * Nothing is built eagerly — e.g. a headless download-service restart only builds the download stack.
 */
@OptIn(UnstableApi::class)
class DataGraph(context: Context) {
    private val app: Context = context.applicationContext

    /**
     * App-lifetime scope for fire-and-forget work that must outlive a screen — e.g. the final watch-
     * progress save when the player's ViewModel is being cleared (its own scope is already cancelled
     * by then). Never cancelled; lives for the process.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- network ----
    val okHttp: OkHttpClient by lazy { NetworkModule.okHttp(app) }
    private val jsonHttp by lazy { JsonHttp(okHttp) }

    // ---- storage ----
    private val database by lazy { DatabaseModule.database(app) }
    private val cacheDatabase by lazy { DatabaseModule.cacheDatabase(app) }
    private val jsonCache by lazy { JsonCache(cacheDatabase.cacheDao(), appScope) }
    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(app, appScope) }
    val library: LibraryRepository by lazy { RoomLibraryRepository(database.libraryDao()) }
    val progress: ProgressRepository by lazy { RoomProgressRepository(database.progressDao()) }

    // ---- catalog & metadata ----
    // Cache-first: the decorator serves the cache DB and delegates to the raw AniList client, so
    // browsing works offline once seen.
    val catalog: CatalogRepository by lazy {
        CachedCatalogRepository(AniListCatalogRepository(AniListApi(jsonHttp)), jsonCache, appScope)
    }
    val skip: SkipRepository by lazy { AniSkipRepository(AniSkipApi(jsonHttp), jsonCache) }
    val mal: MalRepository by lazy { MalRepositoryImpl(MalApi(jsonHttp), database.malDao(), settings) }

    // ---- stream sources ----
    // The app ships NO built-in sources (the set is empty); the registry merges in the user's
    // installed extensions. The active one is chosen at runtime from the selected-source setting.
    val sources: SourceRegistry by lazy { SourceRegistryImpl(app, appScope, builtIns = emptySet()) }
    private val autoSelector by lazy { AutoSourceSelector(sources, jsonCache, settings, appScope) }
    val streams: StreamRepository by lazy {
        SourceStreamRepository(sources, settings, jsonCache, autoSelector, okHttp, appScope)
    }
    val extensions: ExtensionRepository by lazy {
        ExtensionRepositoryImpl(app, ExtensionRepoApi(jsonHttp), okHttp, settings, jsonCache, sources, autoSelector)
    }

    // ---- downloads (Media3) ----
    private val downloadDir by lazy { DownloadModule.downloadDir(app) }
    private val mediaDatabase by lazy { DownloadModule.databaseProvider(app) }
    /** The downloads' byte store; the player reads it back (read-only) for offline playback. */
    val mediaCache: Cache by lazy { DownloadModule.downloadCache(downloadDir, mediaDatabase) }
    private val downloadHeaders by lazy { DownloadHeaderStore(database.downloadDao()) }
    internal val downloadManager: DownloadManager by lazy {
        DownloadModule.downloadManager(app, mediaDatabase, mediaCache, DownloadModule.downloadDataSourceFactory(downloadHeaders))
    }
    internal val downloadNotifications: DownloadNotificationHelper by lazy { DownloadModule.notificationHelper(app) }
    val downloads: DownloadRepository by lazy {
        DownloadRepositoryImpl(
            app, downloadManager, downloadHeaders, database.downloadDao(), okHttp, streams, catalog, settings, downloadDir,
        )
    }
}
