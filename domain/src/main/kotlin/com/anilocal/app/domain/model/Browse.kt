package com.anilocal.app.domain.model

/** Sort options for the Explore/browse grid. */
enum class BrowseSort(val anilist: String, val label: String) {
    POPULAR("POPULARITY_DESC", "Popular"),
    SCORE("SCORE_DESC", "Top Rated"),
    TRENDING("TRENDING_DESC", "Trending"),
    NEWEST("START_DATE_DESC", "Newest"),
}

/** AniList's standard anime genres (stable set) for the Explore genre chips. */
val AniListGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo",
    "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
    "Sports", "Supernatural", "Thriller",
)
