package com.anilocal.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.auth.AuthUser
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.domain.source.Sources
import com.anilocal.app.ui.common.loadOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settings: SettingsRepository,
    private val mal: MalRepository,
    private val sourceRegistry: SourceRegistry,
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

    /** Available stream sources (built-ins now; + installed extensions later) for the picker. */
    val sources: StateFlow<List<SourceInfo>> =
        sourceRegistry.sources.map { list -> list.map(AnimeSource::info) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sourceRegistry.sources.value.map(AnimeSource::info))

    val selectedSourceId: StateFlow<String> =
        settings.selectedSourceId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sources.SAMPLE_ID)

    val malUsername: StateFlow<String> =
        settings.malUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val malSyncEnabled: StateFlow<Boolean> =
        settings.malSyncEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val autoSkipWrites = ConflatedSetting(viewModelScope, settings::setAutoSkip)
    private val wifiWrites = ConflatedSetting(viewModelScope, settings::setWifiOnlyDownloads)
    private val qualityWrites = ConflatedSetting(viewModelScope, settings::setDownloadQuality)
    private val scaleWrites = ConflatedSetting(viewModelScope, settings::setSubtitleScale)
    private val backgroundWrites = ConflatedSetting(viewModelScope, settings::setSubtitleBackground)
    private val sourceWrites = ConflatedSetting(viewModelScope, settings::setSelectedSourceId)
    private val syncEnabledWrites = ConflatedSetting(viewModelScope, settings::setMalSyncEnabled)
    private val syncActions = MalSyncActions(viewModelScope, settings, mal)
    val syncStatus = syncActions.status
    val syncing = syncActions.syncing
    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn

    fun setAutoSkip(enabled: Boolean) = autoSkipWrites.set(enabled)
    fun setWifiOnly(enabled: Boolean) = wifiWrites.set(enabled)
    fun setDownloadQuality(quality: DownloadQuality) = qualityWrites.set(quality)
    fun setSubtitleScale(scale: Float) = scaleWrites.set(scale.coerceIn(0.6f, 2f))
    fun setSubtitleBackground(enabled: Boolean) = backgroundWrites.set(enabled)
    fun setSelectedSource(id: String) = sourceWrites.set(id)
    fun setMalUsername(username: String) = syncActions.setUsername(username)
    fun setMalSyncEnabled(enabled: Boolean) = syncEnabledWrites.set(enabled)
    fun syncMalNow(username: String? = null) = syncActions.sync(username)

    fun signInWithGoogle(idToken: String) {
        if (_signingIn.value) return
        _signingIn.value = true
        viewModelScope.launch {
            try {
                loadOrNull { authRepo.signInWithGoogle(idToken) }
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun signOut() = authRepo.signOut()
}
