package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.DownloadQuality
import kotlinx.coroutines.flow.Flow

/** User settings, persisted via DataStore. */
interface SettingsRepository {
    val autoSkip: Flow<Boolean>
    suspend fun setAutoSkip(enabled: Boolean)

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

    // MyAnimeList sync (all entered in-app — no rebuild needed)
    val malClientId: Flow<String>
    suspend fun setMalClientId(clientId: String)
    val malUsername: Flow<String>
    suspend fun setMalUsername(username: String)
    val malSyncEnabled: Flow<Boolean>
    suspend fun setMalSyncEnabled(enabled: Boolean)
    val malLastSynced: Flow<Long>
    suspend fun setMalLastSynced(epochMs: Long)
}
