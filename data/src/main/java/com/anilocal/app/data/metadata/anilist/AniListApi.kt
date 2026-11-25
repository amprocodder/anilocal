package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.data.remote.JsonHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * AniList's public GraphQL endpoint: POST `{query, variables}`, typed response. No API key. The
 * queries are raw strings (see [AniListCatalogRepository]); no GraphQL client or codegen needed.
 */
class AniListApi(private val http: JsonHttp) {
    suspend fun query(query: String, variables: JsonObject): GraphResponse = http.post(
        ENDPOINT,
        buildJsonObject {
            put("query", query)
            put("variables", variables)
        },
        GraphResponse.serializer(),
    )

    private companion object {
        val ENDPOINT = "https://graphql.anilist.co/".toHttpUrl()
    }
}

@Serializable
data class GraphResponse(val data: AniListData?, val errors: List<GraphError>? = null)

@Serializable
data class GraphError(val message: String?)

@Serializable
data class AniListData(
    @SerialName("Page") val page: PageDto? = null,
    @SerialName("Media") val media: MediaDto? = null,
    val trending: PageDto? = null,
    val popularThisSeason: PageDto? = null,
    val topAiring: PageDto? = null,
    val allTimePopular: PageDto? = null,
    val upcoming: PageDto? = null,
)

@Serializable
data class PageDto(val media: List<MediaDto>?)

@Serializable
data class MediaDto(
    val id: Int,
    val idMal: Int? = null,
    val title: TitleDto? = null,
    // Only requested by the detail query (the title and its relation nodes); null elsewhere.
    val synonyms: List<String>? = null,
    val coverImage: CoverDto? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val averageScore: Int? = null,
    val status: String? = null,
    val seasonYear: Int? = null,
    val duration: Int? = null,
    val studios: StudiosDto? = null,
    val nextAiringEpisode: NextAiringEpisodeDto? = null,
    // Only requested for relation nodes (detail query); null elsewhere.
    val type: String? = null,
    val isAdult: Boolean? = null,
    val startDate: FuzzyDateDto? = null,
    val relations: RelationsDto? = null,
    // Only requested by the detail query: per-episode titles/thumbnails (Crunchyroll-sourced; often
    // empty for currently-airing shows). Titled "Episode N - Name".
    val streamingEpisodes: List<StreamingEpisodeDto>? = null,
)

@Serializable
data class StreamingEpisodeDto(val title: String? = null, val thumbnail: String? = null)

@Serializable
data class RelationsDto(val edges: List<RelationEdgeDto>? = null)

@Serializable
data class RelationEdgeDto(val relationType: String? = null, val node: MediaDto? = null)

@Serializable
data class FuzzyDateDto(val year: Int? = null, val month: Int? = null)

@Serializable
data class TitleDto(val romaji: String? = null, val english: String? = null, val native: String? = null)

@Serializable
data class CoverDto(val large: String? = null, val extraLarge: String? = null)

@Serializable
data class StudiosDto(val nodes: List<StudioNodeDto>? = null)

@Serializable
data class StudioNodeDto(val name: String? = null)

@Serializable
data class NextAiringEpisodeDto(val episode: Int? = null)
