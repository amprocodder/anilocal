package com.anilocal.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val AUTO_SKIP = booleanPreferencesKey("auto_skip")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SUBTITLE_SCALE = floatPreferencesKey("subtitle_scale")
        val SUBTITLE_BG = booleanPreferencesKey("subtitle_background")
    }

    override val autoSkip: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_SKIP] ?: true }

    override suspend fun setAutoSkip(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SKIP] = enabled }
    }

    override val wifiOnlyDownloads: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WIFI_ONLY] ?: true }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    override val downloadQuality: Flow<DownloadQuality> =
        context.dataStore.data.map { prefs ->
            runCatching { DownloadQuality.valueOf(prefs[Keys.DOWNLOAD_QUALITY] ?: DownloadQuality.AUTO.name) }
                .getOrDefault(DownloadQuality.AUTO)
        }

    override suspend fun setDownloadQuality(quality: DownloadQuality) {
        context.dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    override val subtitleScale: Flow<Float> =
        context.dataStore.data.map { it[Keys.SUBTITLE_SCALE] ?: 1.0f }

    override suspend fun setSubtitleScale(scale: Float) {
        context.dataStore.edit { it[Keys.SUBTITLE_SCALE] = scale }
    }

    override val subtitleBackground: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SUBTITLE_BG] ?: true }

    override suspend fun setSubtitleBackground(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUBTITLE_BG] = enabled }
    }
}
