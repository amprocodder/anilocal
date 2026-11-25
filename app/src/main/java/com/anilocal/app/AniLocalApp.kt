package com.anilocal.app

import android.app.Application
import com.anilocal.app.data.DataGraph
import com.anilocal.app.data.DataGraphHolder
import com.anilocal.app.ui.image.ImageLoader

/**
 * The composition root. It builds the data layer's [DataGraph] and the [AppGraph] the UI reads from;
 * this file and [AppGraph] are the only places in :app that touch :data, so screens and ViewModels
 * see domain interfaces only. Both graphs are lazy — nothing is constructed until first used.
 */
class AniLocalApp : Application(), DataGraphHolder, ImageLoader.Provider {

    override val dataGraph: DataGraph by lazy { DataGraph(this) }

    val graph: AppGraph by lazy { AppGraph(this, dataGraph) }

    /**
     * The app's image loader, tuned for offline: a 256 MB disk cache served regardless of cache
     * headers, so CDN artwork (AniList/MAL posters, banners, episode thumbs) renders with no
     * connection once it has been seen. It shares the data layer's HTTP client.
     */
    private val images = lazy { ImageLoader(this, dataGraph.okHttp) }
    override val imageLoader: ImageLoader by images

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (images.isInitialized()) imageLoader.trimMemory(level)
    }
}
