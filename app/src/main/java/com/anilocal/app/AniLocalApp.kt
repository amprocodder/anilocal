package com.anilocal.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AniLocalApp : Application(), ImageLoaderFactory {

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
