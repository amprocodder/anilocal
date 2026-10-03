package com.anilocal.app.domain.model

/** Home shelves loaded together, in their display order. */
data class HomeCatalog(
    val trending: List<AnimeSummary>,
    val popularThisSeason: List<AnimeSummary>,
    val topAiring: List<AnimeSummary>,
    val allTimePopular: List<AnimeSummary>,
    val upcoming: List<AnimeSummary>,
)
