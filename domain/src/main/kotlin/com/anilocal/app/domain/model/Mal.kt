package com.anilocal.app.domain.model

/** MyAnimeList watch-list categories. */
enum class MalStatus(val api: String, val label: String) {
    WATCHING("watching", "Watching"),
    PLAN_TO_WATCH("plan_to_watch", "Plan to Watch"),
    COMPLETED("completed", "Completed"),
    ON_HOLD("on_hold", "On Hold"),
    DROPPED("dropped", "Dropped");

    companion object {
        fun fromApi(s: String?): MalStatus? = entries.firstOrNull { it.api == s }
    }
}

/** One entry from a user's (read-only, mirrored) MyAnimeList list. */
data class MalListEntry(
    val malId: Int,
    val title: String,
    val posterUrl: String?,
    val status: MalStatus,
    val score: Int,
    val episodesWatched: Int,
    val totalEpisodes: Int?,
)
