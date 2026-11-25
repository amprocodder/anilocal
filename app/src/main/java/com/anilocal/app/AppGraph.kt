package com.anilocal.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.datasource.cache.Cache
import com.anilocal.app.data.DataGraph
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

/**
 * What the UI layer can depend on: the domain interfaces, plus the few framework objects the player
 * needs. ViewModels are handed these in their constructors (see [appViewModel]) — there's no DI
 * framework, and nothing here exposes a :data implementation type.
 */
class AppGraph(val appContext: Context, private val data: DataGraph) {
    val catalog: CatalogRepository get() = data.catalog
    val streams: StreamRepository get() = data.streams
    val skip: SkipRepository get() = data.skip
    val library: LibraryRepository get() = data.library
    val progress: ProgressRepository get() = data.progress
    val settings: SettingsRepository get() = data.settings
    val downloads: DownloadRepository get() = data.downloads
    val mal: MalRepository get() = data.mal
    val extensions: ExtensionRepository get() = data.extensions
    val sources: SourceRegistry get() = data.sources

    /** The downloads' byte store; the player reads it (read-only) so downloaded episodes play offline. */
    val mediaCache: Cache get() = data.mediaCache

    /** App-lifetime scope for work that must outlive a ViewModel (e.g. the player's final progress save). */
    val appScope: CoroutineScope get() = data.appScope
}

/** The app's [AppGraph], from any composable. */
@Composable
fun appGraph(): AppGraph = (LocalContext.current.applicationContext as AniLocalApp).graph

/**
 * A ViewModel scoped like `viewModel()`, built by [create] from the [AppGraph] (and the creation
 * extras, for `createSavedStateHandle()`) the first time this owner asks for it.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: CreationExtras.(AppGraph) -> VM,
): VM {
    val graph = appGraph()
    return viewModel(key = key) { create(graph) }
}
