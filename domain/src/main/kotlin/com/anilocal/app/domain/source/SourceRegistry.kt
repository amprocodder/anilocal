package com.anilocal.app.domain.source

import kotlinx.coroutines.flow.StateFlow

/** Well-known source ids the app references directly. */
object Sources {
    /** The always-present, lawful built-in sample source — the default selection. */
    const val SAMPLE_ID = "sample-local"
}

/**
 * Registry of every [AnimeSource] currently available to resolve streams: the in-app built-ins
 * now, plus dynamically loaded user extensions later. Lets the UI present a source picker and
 * lets [com.anilocal.app.domain.repo.StreamRepository] route resolution to whichever source the
 * user selected — replacing the single compile-time `AnimeSource` binding. Browsing/metadata
 * stay on AniList regardless of the selected source.
 */
interface SourceRegistry {
    /** Reactive list of available sources; emits again when extensions are added/removed. */
    val sources: StateFlow<List<AnimeSource>>

    /** The available source whose [SourceInfo.id] is [id], or null if none is registered. */
    fun get(id: String): AnimeSource? = sources.value.firstOrNull { it.info.id == id }
}
