package com.anilocal.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.anilocal.app.data.DataGraph
import com.anilocal.app.data.DataGraphHolder

/**
 * The composition root. It builds the data layer's [DataGraph] and the [AppGraph] the UI reads from;
 * this file and [AppGraph] are the only places in :app that touch :data, so screens and ViewModels
 * see domain interfaces only. Both graphs are lazy — nothing is constructed until first used.
 */
class AniLocalApp : Application(), ImageLoaderFactory, DataGraphHolder {

    override val dataGraph: DataGraph by lazy { DataGraph(this) }

    val graph: AppGraph by lazy { AppGraph(this, dataGraph) }

    /**
     * App-wide Coil loader tuned for offline: a large disk cache and `respectCacheHeaders(false)`
     * so CDN artwork (AniList/MAL posters, banners, episode thumbs) is served from disk regardless
     * of cache-control — once an image has been seen, it renders with no connection.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
