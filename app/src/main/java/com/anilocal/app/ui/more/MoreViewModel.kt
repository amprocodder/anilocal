package com.anilocal.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.auth.AuthUser
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settings: SettingsRepository,
    private val mal: MalRepository,
) : ViewModel() {

    val user: StateFlow<AuthUser?> =
        authRepo.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoSkip: StateFlow<Boolean> =
        settings.autoSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val wifiOnly: StateFlow<Boolean> =
        settings.wifiOnlyDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val downloadQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)

    val subtitleScale: StateFlow<Float> =
        settings.subtitleScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    val subtitleBackground: StateFlow<Boolean> =
        settings.subtitleBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val malConfigured: Boolean get() = mal.isConfigured
    val malUsername: StateFlow<String> =
        settings.malUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val malSyncEnabled: StateFlow<Boolean> =
        settings.malSyncEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    fun setAutoSkip(enabled: Boolean) = viewModelScope.launch { settings.setAutoSkip(enabled) }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }

    fun setDownloadQuality(quality: DownloadQuality) = viewModelScope.launch { settings.setDownloadQuality(quality) }

    fun setSubtitleScale(scale: Float) = viewModelScope.launch { settings.setSubtitleScale(scale) }

    fun setSubtitleBackground(enabled: Boolean) = viewModelScope.launch { settings.setSubtitleBackground(enabled) }

    fun setMalUsername(username: String) = viewModelScope.launch { settings.setMalUsername(username) }
    fun setMalSyncEnabled(enabled: Boolean) = viewModelScope.launch { settings.setMalSyncEnabled(enabled) }
    fun syncMalNow() = viewModelScope.launch {
        _syncStatus.value = "Syncing…"
        _syncStatus.value = mal.sync().fold({ "Synced $it titles" }, { "Sync failed: ${it.message}" })
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch { authRepo.signInWithGoogle(idToken) }

    fun signOut() = authRepo.signOut()
}
