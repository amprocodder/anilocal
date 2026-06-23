package com.anilocal.app.data.source

import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs [SourceRegistry] with the set of in-app built-in sources contributed via Hilt multibinding
 * (`@IntoSet` in [com.anilocal.app.di.AppModule]). Phase 1 is static; once dynamic extensions land,
 * this merges an extension-manager flow into [_sources] so newly installed sources appear in the
 * picker automatically. Built-ins are ordered before extensions, then by name, for a stable list.
 */
@Singleton
class SourceRegistryImpl @Inject constructor(
    builtIns: Set<@JvmSuppressWildcards AnimeSource>,
) : SourceRegistry {

    private val _sources = MutableStateFlow(
        builtIns.sortedWith(compareBy({ it.info.isExternal }, { it.info.name })),
    )
    override val sources: StateFlow<List<AnimeSource>> = _sources.asStateFlow()
}
