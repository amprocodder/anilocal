package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.model.TrackLanguages
import kotlinx.coroutines.flow.Flow

/** User settings, persisted in the app's settings file. */
interface SettingsRepository {
    val autoSkip: Flow<Boolean>
    suspend fun setAutoSkip(enabled: Boolean)

    /** When true, the next episode auto-plays when the current one ends. */
    val autoPlayNext: Flow<Boolean>
    suspend fun setAutoPlayNext(enabled: Boolean)

    /**
     * When true (and [autoPlayNext] is on), the player buffers the start of the next episode near
     * the end of the current one so the advance starts without a load.
     */
    val precacheNextEpisode: Flow<Boolean>
    suspend fun setPrecacheNextEpisode(enabled: Boolean)

    /** When true, downloads only run on unmetered (WiFi) networks. */
    val wifiOnlyDownloads: Flow<Boolean>
    suspend fun setWifiOnlyDownloads(enabled: Boolean)

    /** Default quality the download picker pre-selects. */
    val downloadQuality: Flow<DownloadQuality>
    suspend fun setDownloadQuality(quality: DownloadQuality)

    /** Subtitle text-size multiplier (1.0 = default). */
    val subtitleScale: Flow<Float>
    suspend fun setSubtitleScale(scale: Float)

    /** Whether subtitles render with a background box. */
    val subtitleBackground: Flow<Boolean>
    suspend fun setSubtitleBackground(enabled: Boolean)

    /**
     * Caption language an episode starts with: an ISO 639-1 code, [TrackLanguages.AUTO] (the device's
     * language — the default) or [TrackLanguages.OFF]. One the episode lacks falls back to the
     * device's language, then English (see [TrackLanguages.subtitleOrder]).
     */
    val subtitleLanguage: Flow<String>
    suspend fun setSubtitleLanguage(code: String)

    /**
     * Preferred audio language: an ISO 639-1 code ([TrackLanguages.JAPANESE] by default), or
     * [TrackLanguages.AUTO] for whatever the source serves first. It picks between a stream's audio
     * tracks and between a source's sub and dub versions of an episode.
     */
    val audioLanguage: Flow<String>
    suspend fun setAudioLanguage(code: String)

    /** Id of the [com.anilocal.app.domain.source.AnimeSource] chosen to resolve streams. */
    val selectedSourceId: Flow<String>
    suspend fun setSelectedSourceId(id: String)

    /**
     * Display name of the source the automatic selector last picked, purely for the picker's
     * "Auto (best source) · last: <name>" subtitle. Empty until Auto mode has resolved once.
     */
    val lastAutoWinner: Flow<String>
    suspend fun setLastAutoWinner(name: String)

    /** Base URLs of extension repos (each serving an `index.min.json`). Empty by default — repos
     *  are only ever added by the user (manually, or via "Install recommended sources"). */
    val extensionRepoBaseUrls: Flow<List<String>>
    suspend fun setExtensionRepoBaseUrls(urls: List<String>)

    /** Packages the app auto-installed to seed "Auto" — the only ones auto-eviction is allowed to
     *  remove (a user's manual installs are never touched). */
    val autoInstalledSources: Flow<Set<String>>
    suspend fun setAutoInstalledSources(pkgs: Set<String>)

    /** Packages auto-eviction removed as dead, so provisioning won't keep re-installing them. */
    val evictedSources: Flow<Set<String>>
    suspend fun setEvictedSources(pkgs: Set<String>)

    // MyAnimeList sync — keyless, by username only (mirrors the user's PUBLIC list)
    val malUsername: Flow<String>
    suspend fun setMalUsername(username: String)
    val malSyncEnabled: Flow<Boolean>
    suspend fun setMalSyncEnabled(enabled: Boolean)
    val malLastSynced: Flow<Long>
    suspend fun setMalLastSynced(epochMs: Long)
}
