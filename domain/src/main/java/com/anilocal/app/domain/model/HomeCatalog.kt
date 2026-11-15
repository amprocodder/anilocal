package com.anilocal.app.domain.model

/** The five Home shelves, fetched together when the catalog supports batching. */
data class HomeCatalog(
    val trending: List<AnimeSummary> = emptyList(),
    val popularThisSeason: List<AnimeSummary> = emptyList(),
    val topAiring: List<AnimeSummary> = emptyList(),
    val allTimePopular: List<AnimeSummary> = emptyList(),
    val upcoming: List<AnimeSummary> = emptyList(),
)
