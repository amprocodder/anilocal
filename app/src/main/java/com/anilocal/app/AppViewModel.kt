package com.anilocal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.StreamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped VM for app-wide side effects (throttled MAL sync) and the resume bar. */
class AppViewModel(
    private val mal: MalRepository,
    progress: ProgressRepository,
    private val catalog: CatalogRepository,
    private val streams: StreamRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {

    /** The most-recently watched item, ignoring any bar dismissal. */
    private val mostRecent: StateFlow<ContinueWatching?> =
        progress.continueWatching
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Key of the item the user hid the resume bar for; session-only, not persisted. */
    private val dismissedKey = MutableStateFlow<String?>(null)

    /**
     * Item to show in the bottom resume bar, or null when there's nothing to resume or the bar was
     * dismissed for the current item. Watching anything new yields a fresh key, so the bar returns.
     */
    val resumeBar: StateFlow<ContinueWatching?> =
        combine(mostRecent, dismissedKey) { item, dismissed ->
            item?.takeUnless { keyOf(it) == dismissed }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAppOpen() {
        // The resume bar's episode is the likeliest thing to be played next: resolve it in the
        // background (once the first screen has settled) so tapping Resume starts near-instantly.
        viewModelScope.launch {
            delay(PREFETCH_DELAY_MS)
            val item = mostRecent.first { it != null } ?: return@launch
            try {
                if (downloads.getOffline(item.anime.id, item.episodeNumber) != null) return@launch
                streams.prefetch(catalog.detail(item.anime.id), item.episodeNumber)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best-effort: the play button resolves normally.
            }
        }
        viewModelScope.launch {
            try {
                mal.syncIfDue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // MAL sync is a background convenience; a network outage must not crash the UI.
            }
        }
    }

    /** Hide the resume bar for the current item only — does NOT remove it from Continue Watching. */
    fun dismissBar() {
        dismissedKey.value = mostRecent.value?.let(::keyOf)
    }

    private fun keyOf(c: ContinueWatching) = "${c.anime.id}:${c.updatedAt}"

    private companion object {
        const val PREFETCH_DELAY_MS = 1_500L
    }
}
