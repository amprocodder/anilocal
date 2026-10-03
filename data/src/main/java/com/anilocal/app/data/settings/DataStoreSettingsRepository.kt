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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
        preference(Keys.AUTO_SKIP, true)

    override suspend fun setAutoSkip(enabled: Boolean) {
        writePreference(Keys.AUTO_SKIP, enabled, true)
    }

    override val autoPlayNext: Flow<Boolean> =
        preference(Keys.AUTO_PLAY_NEXT, true)

    override suspend fun setAutoPlayNext(enabled: Boolean) {
        writePreference(Keys.AUTO_PLAY_NEXT, enabled, true)
    }

    override val wifiOnlyDownloads: Flow<Boolean> =
        preference(Keys.WIFI_ONLY, true)

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        writePreference(Keys.WIFI_ONLY, enabled, true)
    }

    override val downloadQuality: Flow<DownloadQuality> =
        store.data.map { prefs ->
            runCatching { DownloadQuality.valueOf(prefs[Keys.DOWNLOAD_QUALITY] ?: DownloadQuality.AUTO.name) }
                .getOrDefault(DownloadQuality.AUTO)
        }.distinctUntilChanged()

    override suspend fun setDownloadQuality(quality: DownloadQuality) {
        writePreference(Keys.DOWNLOAD_QUALITY, quality.name, DownloadQuality.AUTO.name)
    }

    override val subtitleScale: Flow<Float> =
        preference(Keys.SUBTITLE_SCALE, 1.0f)

    override suspend fun setSubtitleScale(scale: Float) {
        writePreference(Keys.SUBTITLE_SCALE, scale, 1.0f)
    }

    override val subtitleBackground: Flow<Boolean> =
        preference(Keys.SUBTITLE_BG, true)

    override suspend fun setSubtitleBackground(enabled: Boolean) {
        writePreference(Keys.SUBTITLE_BG, enabled, true)
    }

    override val selectedSourceId: Flow<String> =
        preference(Keys.SELECTED_SOURCE, Sources.NONE)

    override suspend fun setSelectedSourceId(id: String) {
        writePreference(Keys.SELECTED_SOURCE, id, Sources.NONE)
    }

    override val lastAutoWinner: Flow<String> =
        preference(Keys.AUTO_LAST_WINNER, "")

    override suspend fun setLastAutoWinner(name: String) {
        writePreference(Keys.AUTO_LAST_WINNER, name, "")
    }

    override val extensionRepoBaseUrls: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.EXTENSION_REPOS]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("https://raw.githubusercontent.com/yuzono/anime-repo/repo")
        }.distinctUntilChanged()

    override suspend fun setExtensionRepoBaseUrls(urls: List<String>) {
        writePreference(Keys.EXTENSION_REPOS, urls.joinToString("\n"))
    }

    override val autoInstalledSources: Flow<Set<String>> =
        store.data.map { it[Keys.AUTO_INSTALLED].toPkgSet() }.distinctUntilChanged()

    override suspend fun setAutoInstalledSources(pkgs: Set<String>) {
        writePreference(Keys.AUTO_INSTALLED, pkgs.joinToString("\n"))
    }

    override val evictedSources: Flow<Set<String>> =
        store.data.map { it[Keys.EVICTED_SOURCES].toPkgSet() }.distinctUntilChanged()

    override suspend fun setEvictedSources(pkgs: Set<String>) {
        writePreference(Keys.EVICTED_SOURCES, pkgs.joinToString("\n"))
    }

    override val malUsername: Flow<String> =
        preference(Keys.MAL_USERNAME, "")

    override suspend fun setMalUsername(username: String) {
        val normalized = username.trim()
        store.edit { prefs ->
            val previous = prefs[Keys.MAL_USERNAME] ?: ""
            if (previous != normalized) {
                prefs[Keys.MAL_USERNAME] = normalized
                if (previous.trim() != normalized) prefs[Keys.MAL_LAST_SYNCED] = 0L
            }
        }
    }

    override val malSyncEnabled: Flow<Boolean> =
        preference(Keys.MAL_SYNC, false)

    override suspend fun setMalSyncEnabled(enabled: Boolean) {
        writePreference(Keys.MAL_SYNC, enabled, false)
    }

    override val malLastSynced: Flow<Long> =
        preference(Keys.MAL_LAST_SYNCED, 0L)

    override suspend fun setMalLastSynced(epochMs: Long) {
        writePreference(Keys.MAL_LAST_SYNCED, epochMs, 0L)
    }

    private fun <T> preference(key: Preferences.Key<T>, default: T): Flow<T> =
        store.data.map { it[key] ?: default }.distinctUntilChanged()

    private suspend fun <T> writePreference(key: Preferences.Key<T>, value: T, default: T? = null) {
        store.edit { prefs -> if ((prefs[key] ?: default) != value) prefs[key] = value }
    }

    /** Newline-joined package sets round-trip through one string pref (same pattern as the repo URLs). */
    private fun String?.toPkgSet(): Set<String> =
        this?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
