package com.anilocal.app.domain.model

enum class DownloadState { DOWNLOADING, QUEUED, PAUSED, COMPLETED, FAILED }

/** Default download-quality preference. [maxHeight] = null means "highest available". */
enum class DownloadQuality(val label: String, val maxHeight: Int?) {
    AUTO("Highest available", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
}

/** A downloaded (or in-progress) episode, as shown in the Downloads tab. */
data class DownloadItem(
    val id: String,                // "<animeId>-ep<n>"
    val animeId: String,
    val episodeNumber: Int,
    val title: String,
    val posterUrl: String?,
    val idMal: Int?,
    val state: DownloadState,
    val progress: Int,             // 0..100
    val quality: String? = null,
)

/** Everything needed to play an episode fully offline — no network calls. */
data class OfflineEpisode(
    val streamUri: String,         // original URI; served from the offline cache
    val mimeType: String?,
    val title: String,
    val episodeNumber: Int,
    val idMal: Int?,
    val subtitles: List<Subtitle>, // local file:// URIs
    val markers: List<SkipMarker>,
)
