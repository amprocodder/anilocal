package com.anilocal.app.domain.source

import kotlinx.coroutines.flow.StateFlow

/** Well-known source ids the app references directly. */
object Sources {
    /**
     * Default "no source selected" — the app ships no built-in sources, so until the user picks an
     * installed extension this is persisted, and [SourceRegistry] resolution falls back to the first
     * available source (if any).
     */
    const val NONE = ""

    /**
     * "Auto (best source)" — a sentinel id (never a real [SourceInfo.id]) that routes stream
     * resolution through the automatic selector instead of a single fixed source: it races the
     * installed sources for a title, pins the winner, and reuses it for later episodes. Persisted in
     * the same selected-source setting as any manual id; [StreamRepository] treats this id specially.
     */
    const val AUTO = "auto"
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

    /** Re-scan installed + privately-installed extensions and update [sources]. Fire-and-forget:
     *  the scan runs off the caller; observers of [sources] see the result when it lands. Call after
     *  an install/uninstall so a new source appears without an app restart. */
    fun refresh()
}
