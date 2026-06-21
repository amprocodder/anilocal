package com.anilocal.app.data.metadata.mal

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Reads a user's **PUBLIC** MyAnimeList list with **no API key and no OAuth**, via the same
 * `load.json` endpoint MAL's own list page calls. Only a username is required.
 *
 * `status=7` returns every category; the result is a flat JSON array paginated by `offset`
 * (MAL serves ~300 entries per page). Per-entry `status` is numeric — see [MalLoadEntry].
 * Requires the user's list privacy to be set to Public on MAL.
 */
interface MalApi {
    @GET("animelist/{username}/load.json")
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
    suspend fun animeList(
        @Path("username") username: String,
        @Query("offset") offset: Int = 0,
        @Query("status") status: Int = 7,     // 7 = all categories
    ): List<MalLoadEntry>
}

/**
 * One row from `load.json`. `status` is MAL's numeric list code:
 * 1=watching, 2=completed, 3=on_hold, 4=dropped, 6=plan_to_watch.
 */
data class MalLoadEntry(
    @Json(name = "anime_id") val animeId: Int,
    @Json(name = "anime_title") val title: String?,
    @Json(name = "anime_image_path") val imagePath: String?,
    val status: Int,
    val score: Int?,
    @Json(name = "num_watched_episodes") val episodesWatched: Int?,
    @Json(name = "anime_num_episodes") val numEpisodes: Int?,
)
