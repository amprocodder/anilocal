package com.anilocal.app.data.metadata.mal

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Official MAL API v2, read-only Client-ID mode: GET a user's PUBLIC anime list by username
 * with just the X-MAL-CLIENT-ID header (no OAuth). Paginated; status comes per-entry.
 */
interface MalApi {
    @GET("v2/users/{username}/animelist")
    suspend fun animeList(
        @Path("username") username: String,
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Query("fields") fields: String = "list_status,num_episodes,main_picture",
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
        @Query("nsfw") nsfw: Boolean = true,
    ): MalListResponse
}

data class MalListResponse(val data: List<MalNode>?, val paging: MalPaging?)
data class MalPaging(val next: String?)
data class MalNode(val node: MalAnime?, @Json(name = "list_status") val listStatus: MalListStatus?)

data class MalAnime(
    val id: Int,
    val title: String?,
    @Json(name = "main_picture") val mainPicture: MalPicture?,
    @Json(name = "num_episodes") val numEpisodes: Int?,
)

data class MalPicture(val medium: String?, val large: String?)

data class MalListStatus(
    val status: String?,
    val score: Int?,
    @Json(name = "num_episodes_watched") val episodesWatched: Int?,
)
