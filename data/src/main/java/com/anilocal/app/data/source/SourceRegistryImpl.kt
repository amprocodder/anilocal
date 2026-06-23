package com.anilocal.app.data.source

import android.content.Context
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.extensions.loader.AnimeExtensionLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Backs [SourceRegistry] with the in-app built-in sources (Hilt `@IntoSet`) merged with the user's
 * installed Aniyomi extensions, discovered via [AnimeExtensionLoader]. Extensions load asynchronously
 * on [refresh] (called on construction and after an install/uninstall in Phase 4): the picker shows
 * built-ins immediately and gains extensions when the scan completes. Built-ins sort before
 * extensions, then by name; ids are de-duplicated (built-in wins) for a stable list.
 */
@Singleton
class SourceRegistryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("appScope") private val scope: CoroutineScope,
    builtIns: Set<@JvmSuppressWildcards AnimeSource>,
) : SourceRegistry {

    private val builtInList = builtIns.toList()
    private val _sources = MutableStateFlow(sortSources(builtInList))
    override val sources: StateFlow<List<AnimeSource>> = _sources.asStateFlow()

    init {
        refresh()
    }

    /** Re-scan installed extensions and merge them with the built-ins. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { AnimeExtensionLoader.loadSources(context) }.getOrDefault(emptyList())
            _sources.value = sortSources(builtInList + loaded)
        }
    }

    private fun sortSources(list: List<AnimeSource>): List<AnimeSource> =
        list.distinctBy { it.info.id }
            .sortedWith(compareBy({ it.info.isExternal }, { it.info.name }))
}
