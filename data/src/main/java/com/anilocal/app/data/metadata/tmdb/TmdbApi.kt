package com.anilocal.app.data.metadata.tmdb

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OPTIONAL artwork enrichment (episode stills, higher-res backdrops). Requires a free v3
 * key in BuildConfig.TMDB_API_KEY. Not on the critical path — AniList already supplies
 * posters/banners/synopsis — so the app runs fully without a TMDB key.
 *
 * To use later: map an AniList/MAL id → TMDB id (e.g. via search), then fetch episode stills.
 */
interface TmdbApi {
    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
    ): TmdbSearchResponse
}

data class TmdbSearchResponse(val results: List<TmdbShow>?)

data class TmdbShow(
    val id: Int,
    val name: String?,
    val poster_path: String?,
    val backdrop_path: String?,
)
