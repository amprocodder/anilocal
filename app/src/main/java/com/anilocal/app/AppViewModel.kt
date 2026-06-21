package com.anilocal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.repo.MalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-scoped VM for app-open side effects (e.g. throttled MAL sync). */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val mal: MalRepository,
) : ViewModel() {
    fun onAppOpen() {
        viewModelScope.launch { runCatching { mal.syncIfDue() } }
    }
}
