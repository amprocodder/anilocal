package com.anilocal.app.domain.model

/** Lightweight catalog row item. */
data class AnimeSummary(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val idMal: Int? = null,
)

/** Full detail for the detail screen. */
data class AnimeDetail(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String? = null,
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val idMal: Int? = null,
    val episodes: List<Episode> = emptyList(),
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String? = null,
    val thumbnailUrl: String? = null,
)

/** What a Source resolves an episode to: a playable stream. */
data class VideoStream(
    val url: String,
    val mimeType: String? = null,          // e.g. application/x-mpegURL for HLS
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
    val quality: String? = null,           // human label, e.g. "1080p"
    val height: Int? = null,               // vertical resolution, for sorting/matching
)

data class Subtitle(val url: String, val language: String, val label: String = language)

/** A skip window (opening or ending), in milliseconds. */
data class SkipMarker(
    val type: Type,
    val startMs: Long,
    val endMs: Long,
) {
    enum class Type { INTRO, OUTRO }
}

/** A selectable stream host/server for an episode (Source decides what these mean). */
data class VideoServer(
    val id: String,
    val name: String,
    val episodeId: String,
)
