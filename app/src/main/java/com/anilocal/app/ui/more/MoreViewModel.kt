package com.anilocal.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.auth.AuthUser
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val user: StateFlow<AuthUser?> =
        authRepo.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoSkip: StateFlow<Boolean> =
        settings.autoSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val wifiOnly: StateFlow<Boolean> =
        settings.wifiOnlyDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val downloadQuality: StateFlow<DownloadQuality> =
        settings.downloadQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.AUTO)

    fun setAutoSkip(enabled: Boolean) = viewModelScope.launch { settings.setAutoSkip(enabled) }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }

    fun setDownloadQuality(quality: DownloadQuality) = viewModelScope.launch { settings.setDownloadQuality(quality) }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch { authRepo.signInWithGoogle(idToken) }

    fun signOut() = authRepo.signOut()
}
