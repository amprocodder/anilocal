package com.anilocal.app.data.settings

import android.content.Context
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
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SUBTITLE_SCALE = floatPreferencesKey("subtitle_scale")
        val SUBTITLE_BG = booleanPreferencesKey("subtitle_background")
        val SELECTED_SOURCE = stringPreferencesKey("selected_source")
        val AUTO_LAST_WINNER = stringPreferencesKey("auto_last_winner")
        val EXTENSION_REPOS = stringPreferencesKey("extension_repos")
        val AUTO_INSTALLED = stringPreferencesKey("auto_installed_sources")
        val EVICTED_SOURCES = stringPreferencesKey("evicted_sources")
        val MAL_USERNAME = stringPreferencesKey("mal_username")
        val MAL_SYNC = booleanPreferencesKey("mal_sync_enabled")
        val MAL_LAST_SYNCED = longPreferencesKey("mal_last_synced")
    }

    override val autoSkip: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_SKIP] ?: true }

    override suspend fun setAutoSkip(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SKIP] = enabled }
    }

    override val autoPlayNext: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_PLAY_NEXT] ?: true }

    override suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
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

    override val selectedSourceId: Flow<String> =
        context.dataStore.data.map { it[Keys.SELECTED_SOURCE] ?: Sources.NONE }

    override suspend fun setSelectedSourceId(id: String) {
        context.dataStore.edit { it[Keys.SELECTED_SOURCE] = id }
    }

    override val lastAutoWinner: Flow<String> =
        context.dataStore.data.map { it[Keys.AUTO_LAST_WINNER] ?: "" }

    override suspend fun setLastAutoWinner(name: String) {
        context.dataStore.edit { it[Keys.AUTO_LAST_WINNER] = name }
    }

    override val extensionRepoBaseUrls: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.EXTENSION_REPOS]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("https://raw.githubusercontent.com/yuzono/anime-repo/repo")
        }

    override suspend fun setExtensionRepoBaseUrls(urls: List<String>) {
        context.dataStore.edit { it[Keys.EXTENSION_REPOS] = urls.joinToString("\n") }
    }

    override val autoInstalledSources: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.AUTO_INSTALLED].toPkgSet() }

    override suspend fun setAutoInstalledSources(pkgs: Set<String>) {
        context.dataStore.edit { it[Keys.AUTO_INSTALLED] = pkgs.joinToString("\n") }
    }

    override val evictedSources: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.EVICTED_SOURCES].toPkgSet() }

    override suspend fun setEvictedSources(pkgs: Set<String>) {
        context.dataStore.edit { it[Keys.EVICTED_SOURCES] = pkgs.joinToString("\n") }
    }

    override val malUsername: Flow<String> =
        context.dataStore.data.map { it[Keys.MAL_USERNAME] ?: "" }

    override suspend fun setMalUsername(username: String) {
        context.dataStore.edit { it[Keys.MAL_USERNAME] = username }
    }

    override val malSyncEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MAL_SYNC] ?: false }

    override suspend fun setMalSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MAL_SYNC] = enabled }
    }

    override val malLastSynced: Flow<Long> =
        context.dataStore.data.map { it[Keys.MAL_LAST_SYNCED] ?: 0L }

    override suspend fun setMalLastSynced(epochMs: Long) {
        context.dataStore.edit { it[Keys.MAL_LAST_SYNCED] = epochMs }
    }

    /** Newline-joined package sets round-trip through one string pref (same pattern as the repo URLs). */
    private fun String?.toPkgSet(): Set<String> =
        this?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
