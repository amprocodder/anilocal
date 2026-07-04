package com.anilocal.app.data.metadata.anilist

import com.squareup.moshi.Json
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * AniList public GraphQL endpoint. We POST a raw JSON body (built safely with JSONObject)
 * and parse a typed response with Moshi. No API key required. Could be upgraded to Apollo
 * codegen later; raw POST keeps the dependency surface minimal.
 */
interface AniListApi {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("/")
    suspend fun query(@Body body: RequestBody): GraphResponse
}

data class GraphResponse(val data: AniListData?)

data class AniListData(
    @Json(name = "Page") val page: PageDto?,
    @Json(name = "Media") val media: MediaDto?,
)

data class PageDto(val media: List<MediaDto>?)

data class MediaDto(
    val id: Int,
    val idMal: Int?,
    val title: TitleDto?,
    val coverImage: CoverDto?,
    val bannerImage: String?,
    val description: String?,
    val genres: List<String>?,
    val episodes: Int?,
    val format: String?,
    val averageScore: Int?,
    val status: String?,
    val seasonYear: Int?,
    val duration: Int?,
    val studios: StudiosDto?,
    val nextAiringEpisode: NextAiringEpisodeDto?,
)

data class TitleDto(val romaji: String?, val english: String?, val native: String?)

data class CoverDto(val large: String?, val extraLarge: String?)

data class StudiosDto(val nodes: List<StudioNodeDto>?)

data class StudioNodeDto(val name: String?)

data class NextAiringEpisodeDto(val episode: Int?)
