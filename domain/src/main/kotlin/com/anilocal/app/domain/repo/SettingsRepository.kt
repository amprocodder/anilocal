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
}
