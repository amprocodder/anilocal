package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.repo.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListCatalogRepository @Inject constructor(
    private val api: AniListApi,
) : CatalogRepository {

    private val jsonMedia = "application/json".toMediaType()

    private fun body(query: String, variables: JSONObject): okhttp3.RequestBody =
        JSONObject().put("query", query).put("variables", variables).toString().toRequestBody(jsonMedia)

    override suspend fun popular(page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int){ Page(page:${'$'}page, perPage:30){
              media(sort:TRENDING_DESC, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large} episodes
              } } }
        """.trimIndent()
        api.query(body(q, JSONObject().put("page", page)))
            .data?.page?.media.orEmpty().map { it.toSummary() }
    }

    override suspend fun search(query: String): List<AnimeSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext popular(1)
        val q = """
            query(${'$'}search:String){ Page(page:1, perPage:30){
              media(search:${'$'}search, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large}
              } } }
        """.trimIndent()
        api.query(body(q, JSONObject().put("search", query)))
            .data?.page?.media.orEmpty().map { it.toSummary() }
    }

    override suspend fun detail(animeId: String): AnimeDetail = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}id:Int){ Media(id:${'$'}id, type:ANIME){
              id idMal title{romaji english} coverImage{large} bannerImage
              description(asHtml:false) genres episodes
            } }
        """.trimIndent()
        val m = api.query(body(q, JSONObject().put("id", animeId.toInt()))).data?.media
            ?: error("AniList: media $animeId not found")
        val count = m.episodes ?: 0
        AnimeDetail(
            id = m.id.toString(),
            title = m.displayTitle(),
            posterUrl = m.coverImage?.large,
            bannerUrl = m.bannerImage,
            synopsis = m.description?.stripHtml().orEmpty(),
            genres = m.genres.orEmpty(),
            idMal = m.idMal,
            episodes = (1..count).map { Episode(id = "${m.id}-$it", number = it, title = "Episode $it") },
        )
    }

    private fun MediaDto.toSummary() =
        AnimeSummary(id = id.toString(), title = displayTitle(), posterUrl = coverImage?.large, idMal = idMal)

    private fun MediaDto.displayTitle() =
        title?.english ?: title?.romaji ?: title?.native ?: "Untitled"

    private fun String.stripHtml() = replace(Regex("<[^>]*>"), "").trim()
}
