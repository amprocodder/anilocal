package com.anilocal.app.data.settings

import android.content.Context
import com.anilocal.app.data.metadata.extension.RecommendedSources
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.Sources
import com.anilocal.app.extensions.loader.AnimeExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * [SettingsRepository] over the app's own [SettingsFile]. Keys and defaults are the ones the
 * DataStore implementation used, so migrated settings mean the same thing. A setter writes only when
 * the value differs from what's stored (or the default, when nothing is).
 */
class SettingsRepositoryImpl internal constructor(
    private val file: SettingsFile,
    /** True when this install predates the blank-by-default repo list and already has private
     *  extensions — those installs relied on the old implicit yuzono default and keep it. */
    private val hasLegacyExtensions: () -> Boolean = { false },
) : SettingsRepository {
    constructor(context: Context, scope: CoroutineScope) : this(
        SettingsFile(
            File(context.filesDir, "settings.json"),
            File(context.filesDir, "datastore/settings.preferences_pb"),
            scope,
        ),
        {
            AnimeExtensionLoader.privateDir(context)
                .listFiles { f -> f.name.endsWith(AnimeExtensionLoader.PRIVATE_APK_SUFFIX) }
                ?.isNotEmpty() == true
        },
    )

    private object Keys {
        const val AUTO_SKIP = "auto_skip"
        const val AUTO_PLAY_NEXT = "auto_play_next"
        const val PRECACHE_NEXT = "precache_next_episode"
        const val WIFI_ONLY = "wifi_only_downloads"
        const val DOWNLOAD_QUALITY = "download_quality"
        const val SUBTITLE_SCALE = "subtitle_scale"
        const val SUBTITLE_BG = "subtitle_background"
        const val SELECTED_SOURCE = "selected_source"
        const val AUTO_LAST_WINNER = "auto_last_winner"
        const val EXTENSION_REPOS = "extension_repos"
        const val AUTO_INSTALLED = "auto_installed_sources"
        const val EVICTED_SOURCES = "evicted_sources"
        const val MAL_USERNAME = "mal_username"
        const val MAL_SYNC = "mal_sync_enabled"
        const val MAL_LAST_SYNCED = "mal_last_synced"
    }

    override val autoSkip = bool(Keys.AUTO_SKIP, true)
    override suspend fun setAutoSkip(enabled: Boolean) = set(Keys.AUTO_SKIP, JsonPrimitive(enabled), true)

    override val autoPlayNext = bool(Keys.AUTO_PLAY_NEXT, true)
    override suspend fun setAutoPlayNext(enabled: Boolean) = set(Keys.AUTO_PLAY_NEXT, JsonPrimitive(enabled), true)

    override val precacheNextEpisode = bool(Keys.PRECACHE_NEXT, true)
    override suspend fun setPrecacheNextEpisode(enabled: Boolean) = set(Keys.PRECACHE_NEXT, JsonPrimitive(enabled), true)

    override val wifiOnlyDownloads = bool(Keys.WIFI_ONLY, true)
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = set(Keys.WIFI_ONLY, JsonPrimitive(enabled), true)

    override val downloadQuality: Flow<DownloadQuality> = string(Keys.DOWNLOAD_QUALITY, DownloadQuality.AUTO.name)
        .map { name -> DownloadQuality.entries.firstOrNull { it.name == name } ?: DownloadQuality.AUTO }
        .distinctUntilChanged()
    override suspend fun setDownloadQuality(quality: DownloadQuality) =
        set(Keys.DOWNLOAD_QUALITY, JsonPrimitive(quality.name), DownloadQuality.AUTO.name)

    override val subtitleScale: Flow<Float> = file.values.map { it[Keys.SUBTITLE_SCALE]?.floatOrNull ?: 1.0f }
        .distinctUntilChanged()
    override suspend fun setSubtitleScale(scale: Float) = set(Keys.SUBTITLE_SCALE, JsonPrimitive(scale), 1.0f)

    override val subtitleBackground = bool(Keys.SUBTITLE_BG, true)
    override suspend fun setSubtitleBackground(enabled: Boolean) = set(Keys.SUBTITLE_BG, JsonPrimitive(enabled), true)

    override val selectedSourceId = string(Keys.SELECTED_SOURCE, Sources.NONE)
    override suspend fun setSelectedSourceId(id: String) = set(Keys.SELECTED_SOURCE, JsonPrimitive(id), Sources.NONE)

    override val lastAutoWinner = string(Keys.AUTO_LAST_WINNER, "")
    override suspend fun setLastAutoWinner(name: String) = set(Keys.AUTO_LAST_WINNER, JsonPrimitive(name), "")

    override val extensionRepoBaseUrls: Flow<List<String>> = file.values.map { values ->
        // The app ships with NO repos: the user adds one, or explicitly taps "Install recommended
        // sources". An unset key only falls back to the old implicit default for installs that
        // already have extensions from it (so upgrading doesn't orphan them).
        values[Keys.EXTENSION_REPOS]?.stringOrNull()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: if (hasLegacyExtensions()) listOf(RecommendedSources.REPO_URL) else emptyList()
    }.distinctUntilChanged()
    override suspend fun setExtensionRepoBaseUrls(urls: List<String>) =
        set(Keys.EXTENSION_REPOS, JsonPrimitive(urls.joinToString("\n")), null)

    override val autoInstalledSources = pkgSet(Keys.AUTO_INSTALLED)
    override suspend fun setAutoInstalledSources(pkgs: Set<String>) =
        set(Keys.AUTO_INSTALLED, JsonPrimitive(pkgs.joinToString("\n")), null)

    override val evictedSources = pkgSet(Keys.EVICTED_SOURCES)
    override suspend fun setEvictedSources(pkgs: Set<String>) =
        set(Keys.EVICTED_SOURCES, JsonPrimitive(pkgs.joinToString("\n")), null)

    override val malUsername = string(Keys.MAL_USERNAME, "")
    override suspend fun setMalUsername(username: String) {
        val normalized = username.trim()
        file.edit { values ->
            val previous = values[Keys.MAL_USERNAME]?.stringOrNull() ?: ""
            if (previous != normalized) {
                values[Keys.MAL_USERNAME] = JsonPrimitive(normalized)
                // A different account: its list hasn't been synced yet.
                if (previous.trim() != normalized) values[Keys.MAL_LAST_SYNCED] = JsonPrimitive(0L)
            }
        }
    }

    override val malSyncEnabled = bool(Keys.MAL_SYNC, false)
    override suspend fun setMalSyncEnabled(enabled: Boolean) = set(Keys.MAL_SYNC, JsonPrimitive(enabled), false)

    override val malLastSynced: Flow<Long> = file.values.map { it[Keys.MAL_LAST_SYNCED]?.longOrNull ?: 0L }
        .distinctUntilChanged()
    override suspend fun setMalLastSynced(epochMs: Long) = set(Keys.MAL_LAST_SYNCED, JsonPrimitive(epochMs), 0L)

    private fun bool(key: String, default: Boolean): Flow<Boolean> =
        file.values.map { it[key]?.booleanOrNull ?: default }.distinctUntilChanged()

    private fun string(key: String, default: String): Flow<String> =
        file.values.map { it[key]?.stringOrNull() ?: default }.distinctUntilChanged()

    private fun pkgSet(key: String): Flow<Set<String>> = file.values.map { values ->
        values[key]?.stringOrNull()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }.distinctUntilChanged()

    /** Write [value] unless it equals what's stored (or [default], when nothing is). */
    private suspend fun set(key: String, value: JsonPrimitive, default: Any?) {
        file.edit { values ->
            val current = values[key] ?: default?.let { primitiveOf(it) }
            if (current != value) values[key] = value
        }
    }

    private fun primitiveOf(v: Any): JsonPrimitive = when (v) {
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private fun JsonPrimitive.stringOrNull(): String? = if (isString) content else null
}
