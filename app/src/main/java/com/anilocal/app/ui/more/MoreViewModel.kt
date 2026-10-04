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
import com.anilocal.app.ui.common.loadOrNull
import kotlinx.coroutines.CancellationException
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
    private val malActions = MalSyncActions(viewModelScope, settings, mal)
    val syncStatus = malActions.status
    val syncing = malActions.syncing

    /** True while community-recommended sources are being auto-installed (for a spinner/label). */
    private val _provisioning = MutableStateFlow(false)
    val provisioning: StateFlow<Boolean> = _provisioning
    private val _provisionStatus = MutableStateFlow<String?>(null)
    val provisionStatus: StateFlow<String?> = _provisionStatus

    private val autoSkipWrites = ConflatedSetting(viewModelScope, settings::setAutoSkip)
    private val autoPlayWrites = ConflatedSetting(viewModelScope, settings::setAutoPlayNext)
    private val wifiWrites = ConflatedSetting(viewModelScope, settings::setWifiOnlyDownloads)
    private val qualityWrites = ConflatedSetting(viewModelScope, settings::setDownloadQuality)
    private val scaleWrites = ConflatedSetting(viewModelScope, settings::setSubtitleScale)
    private val backgroundWrites = ConflatedSetting(viewModelScope, settings::setSubtitleBackground)
    private val malEnabledWrites = ConflatedSetting(viewModelScope, settings::setMalSyncEnabled)
    private val sourceWrites = ConflatedSetting<String>(viewModelScope) { id ->
        settings.setSelectedSourceId(id)
        // Installing extensions must not hold up a later source-selection preference write.
        if (id == Sources.AUTO) installRecommended()
    }

    fun setAutoSkip(enabled: Boolean) = autoSkipWrites.set(enabled)
    fun setAutoPlayNext(enabled: Boolean) = autoPlayWrites.set(enabled)
    fun setWifiOnly(enabled: Boolean) = wifiWrites.set(enabled)
    fun setDownloadQuality(quality: DownloadQuality) = qualityWrites.set(quality)
    fun setSubtitleScale(scale: Float) = scaleWrites.set(scale)
    fun setSubtitleBackground(enabled: Boolean) = backgroundWrites.set(enabled)
    fun setSelectedSource(id: String) = sourceWrites.set(id)

    /** Manually (re)install the community-recommended sources. */
    fun installRecommended() = viewModelScope.launch { provision() }

    private suspend fun provision() {
        if (_provisioning.value) return
        _provisioning.value = true
        _provisionStatus.value = "Updating sources…"
        try {
            val added = loadOrNull { extensions.installRecommended() }
            val removed = loadOrNull { extensions.pruneLosers() }
            _provisionStatus.value = if (added == null || removed == null) {
                "Couldn't update sources. Tap Install recommended to retry."
            } else buildString {
                if (added > 0) append("Added $added source${if (added == 1) "" else "s"}")
                if (removed > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("removed $removed dead")
                }
            }.ifBlank {
                "No verified recommended sources available yet. Tap Install recommended to retry."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Provisioning is best-effort: a dead repo, unavailable network, or failed signature
            // check must leave Auto usable and give the user a safe retry affordance instead of an
            // unhandled coroutine exception or a silent empty source list.
            _provisionStatus.value = "Couldn't update sources. Tap Install recommended to retry."
        } finally {
            _provisioning.value = false
        }
    }

    fun setMalUsername(username: String) = malActions.setUsername(username)
    fun setMalSyncEnabled(enabled: Boolean) = malEnabledWrites.set(enabled)
    fun syncMalNow(username: String? = null) = malActions.sync(username)
}
