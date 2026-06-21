package com.anilocal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-scoped VM for app-wide side effects (throttled MAL sync) and the resume bar. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val mal: MalRepository,
    progress: ProgressRepository,
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
        viewModelScope.launch { runCatching { mal.syncIfDue() } }
    }

    /** Hide the resume bar for the current item only — does NOT remove it from Continue Watching. */
    fun dismissBar() {
        dismissedKey.value = mostRecent.value?.let(::keyOf)
    }

    private fun keyOf(c: ContinueWatching) = "${c.anime.id}:${c.updatedAt}"
}
