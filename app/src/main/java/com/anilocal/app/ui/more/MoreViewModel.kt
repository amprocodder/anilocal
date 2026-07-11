package com.anilocal.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.ExtensionRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.domain.source.Sources
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
    private val settings: SettingsRepository,
    private val mal: MalRepository,
    private val sourceRegistry: SourceRegistry,
    private val extensions: ExtensionRepository,
) : ViewModel() {

    val autoSkip: StateFlow<Boolean> =
        settings.autoSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoPlayNext: StateFlow<Boolean> =
        settings.autoPlayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedSourceId: StateFlow<String> =
        settings.selectedSourceId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sources.NONE)

    /** Name of the source the auto-selector last picked, for the "Auto (best source)" row subtitle. */
    val lastAutoWinner: StateFlow<String> =
        settings.lastAutoWinner.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val malUsername: StateFlow<String> =
        settings.malUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val malSyncEnabled: StateFlow<Boolean> =
        settings.malSyncEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    /** True while community-recommended sources are being auto-installed (for a spinner/label). */
    private val _provisioning = MutableStateFlow(false)
    val provisioning: StateFlow<Boolean> = _provisioning
    private val _provisionStatus = MutableStateFlow<String?>(null)
    val provisionStatus: StateFlow<String?> = _provisionStatus

    fun setAutoSkip(enabled: Boolean) = viewModelScope.launch { settings.setAutoSkip(enabled) }

    fun setAutoPlayNext(enabled: Boolean) = viewModelScope.launch { settings.setAutoPlayNext(enabled) }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }

    fun setDownloadQuality(quality: DownloadQuality) = viewModelScope.launch { settings.setDownloadQuality(quality) }

    fun setSubtitleScale(scale: Float) = viewModelScope.launch { settings.setSubtitleScale(scale) }

    fun setSubtitleBackground(enabled: Boolean) = viewModelScope.launch { settings.setSubtitleBackground(enabled) }

    fun setSelectedSource(id: String) = viewModelScope.launch {
        settings.setSelectedSourceId(id)
        // Choosing Auto with nothing (much) to race is useless — seed the community sources so the
        // race has proven candidates on day one. Idempotent and best-effort.
        if (id == Sources.AUTO) provision()
    }

    /** Manually (re)install the community-recommended sources. */
    fun installRecommended() = viewModelScope.launch { provision() }

    private suspend fun provision() {
        if (_provisioning.value) return
        _provisioning.value = true
        _provisionStatus.value = "Installing recommended sources…"
        val added = runCatching { extensions.installRecommended() }.getOrDefault(0)
        _provisionStatus.value = when {
            added > 0 -> "Added $added recommended source${if (added == 1) "" else "s"}"
            else -> null   // nothing to add (already have them) or offline — stay quiet
        }
        _provisioning.value = false
    }

    fun setMalUsername(username: String) = viewModelScope.launch { settings.setMalUsername(username) }
    fun setMalSyncEnabled(enabled: Boolean) = viewModelScope.launch { settings.setMalSyncEnabled(enabled) }
    fun syncMalNow() = viewModelScope.launch {
        _syncStatus.value = "Syncing…"
        _syncStatus.value = mal.sync().fold({ "Synced $it titles" }, { "Sync failed: ${it.message}" })
    }
}
