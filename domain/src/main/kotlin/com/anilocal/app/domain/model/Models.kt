package com.anilocal.app.domain.model

/** Lightweight catalog row item. */
data class AnimeSummary(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val idMal: Int? = null,
    val format: String? = null,            // prettified, e.g. "TV", "Movie"
    val episodes: Int? = null,
    val averageScore: Int? = null,         // 0–100, as AniList reports it
)

/** A "Continue Watching" entry: an anime plus where the user left off. */
data class ContinueWatching(
    val anime: AnimeSummary,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** Fraction watched in [0,1], or null when the duration isn't known yet. */
    val fraction: Float?
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}

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
    val format: String? = null,            // prettified, e.g. "TV", "Movie"
    val averageScore: Int? = null,         // 0–100, as AniList reports it
    val status: String? = null,            // prettified, e.g. "Releasing"
    val seasonYear: Int? = null,
    val duration: Int? = null,             // minutes per episode
    val studio: String? = null,            // first main studio name
    val related: List<RelatedAnime> = emptyList(),   // franchise neighbors, chronological
)

/**
 * A franchise neighbor of a title (previous/next season, movie, side story…), tagged with how it
 * relates. Lists come pre-sorted chronologically: prequels first, then sequels, then the rest,
 * each group ordered by release date.
 */
data class RelatedAnime(
    val relation: String,                  // prettified, e.g. "Prequel", "Sequel", "Side story"
    val anime: AnimeSummary,
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
