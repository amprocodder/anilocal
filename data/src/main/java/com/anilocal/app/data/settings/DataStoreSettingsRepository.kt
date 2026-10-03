package com.anilocal.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.Sources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository internal constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(context.dataStore)

    private object Keys {
        val AUTO_SKIP = booleanPreferencesKey("auto_skip")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SUBTITLE_SCALE = floatPreferencesKey("subtitle_scale")
        val SUBTITLE_BG = booleanPreferencesKey("subtitle_background")
        val SELECTED_SOURCE = stringPreferencesKey("selected_source")
        val MAL_USERNAME = stringPreferencesKey("mal_username")
        val MAL_SYNC = booleanPreferencesKey("mal_sync_enabled")
        val MAL_LAST_SYNCED = longPreferencesKey("mal_last_synced")
    }

    override val autoSkip: Flow<Boolean> =
        preference(Keys.AUTO_SKIP, true)

    override suspend fun setAutoSkip(enabled: Boolean) {
        setPreference(Keys.AUTO_SKIP, enabled, true)
    }

    override val wifiOnlyDownloads: Flow<Boolean> =
        preference(Keys.WIFI_ONLY, true)

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        setPreference(Keys.WIFI_ONLY, enabled, true)
    }

    override val downloadQuality: Flow<DownloadQuality> =
        preference(Keys.DOWNLOAD_QUALITY, DownloadQuality.AUTO.name).map { name ->
            runCatching { DownloadQuality.valueOf(name) }
                .getOrDefault(DownloadQuality.AUTO)
        }.distinctUntilChanged()

    override suspend fun setDownloadQuality(quality: DownloadQuality) {
        setPreference(Keys.DOWNLOAD_QUALITY, quality.name, DownloadQuality.AUTO.name)
    }

    override val subtitleScale: Flow<Float> =
        preference(Keys.SUBTITLE_SCALE, 1.0f)

    override suspend fun setSubtitleScale(scale: Float) {
        setPreference(Keys.SUBTITLE_SCALE, scale, 1.0f)
    }

    override val subtitleBackground: Flow<Boolean> =
        preference(Keys.SUBTITLE_BG, true)

    override suspend fun setSubtitleBackground(enabled: Boolean) {
        setPreference(Keys.SUBTITLE_BG, enabled, true)
    }

    override val selectedSourceId: Flow<String> =
        preference(Keys.SELECTED_SOURCE, Sources.SAMPLE_ID)

    override suspend fun setSelectedSourceId(id: String) {
        setPreference(Keys.SELECTED_SOURCE, id, Sources.SAMPLE_ID)
    }

    override val malUsername: Flow<String> =
        preference(Keys.MAL_USERNAME, "")

    override suspend fun setMalUsername(username: String) {
        val normalized = username.trim()
        store.edit { prefs ->
            val previous = prefs[Keys.MAL_USERNAME] ?: ""
            if (previous != normalized) {
                prefs[Keys.MAL_USERNAME] = normalized
                if (previous.trim() != normalized && (prefs[Keys.MAL_LAST_SYNCED] ?: 0L) != 0L) {
                    prefs[Keys.MAL_LAST_SYNCED] = 0L
                }
            }
        }
    }

    override val malSyncEnabled: Flow<Boolean> =
        preference(Keys.MAL_SYNC, false)

    override suspend fun setMalSyncEnabled(enabled: Boolean) {
        setPreference(Keys.MAL_SYNC, enabled, false)
    }

    override val malLastSynced: Flow<Long> =
        preference(Keys.MAL_LAST_SYNCED, 0L)

    override suspend fun setMalLastSynced(epochMs: Long) {
        setPreference(Keys.MAL_LAST_SYNCED, epochMs, 0L)
    }

    // An unrelated preference write must not restart consumers such as the download manager.
    private fun <T> preference(key: Preferences.Key<T>, default: T): Flow<T> =
        store.data.map { it[key] ?: default }.distinctUntilChanged()

    private suspend fun <T> setPreference(key: Preferences.Key<T>, value: T, default: T) {
        store.edit { prefs ->
            if ((prefs[key] ?: default) != value) prefs[key] = value
        }
    }
}
