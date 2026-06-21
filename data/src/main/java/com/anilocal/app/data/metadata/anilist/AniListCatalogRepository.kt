package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
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

    override suspend fun trending(page: Int) = mediaPage("sort: TRENDING_DESC", page)
    override suspend fun allTimePopular(page: Int) = mediaPage("sort: POPULARITY_DESC", page)
    override suspend fun topAiring(page: Int) = mediaPage("status: RELEASING, sort: POPULARITY_DESC", page)
    override suspend fun upcoming(page: Int) = mediaPage("status: NOT_YET_RELEASED, sort: POPULARITY_DESC", page)

    override suspend fun popularThisSeason(page: Int): List<AnimeSummary> {
        val (season, year) = currentSeason()
        return mediaPage("season: $season, seasonYear: $year, sort: POPULARITY_DESC", page)
    }

    override suspend fun browse(genre: String?, sort: BrowseSort, page: Int): List<AnimeSummary> {
        val g = genre?.let { "genre: \"$it\", " } ?: ""
        return mediaPage("${g}sort: ${sort.anilist}", page)
    }

    private suspend fun mediaPage(mediaArgs: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int){ Page(page:${'$'}page, perPage:30){
              media($mediaArgs, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large}
              } } }
        """.trimIndent()
        api.query(body(q, JSONObject().put("page", page))).data?.page?.media.orEmpty().map { it.toSummary() }
    }

    private fun currentSeason(): Pair<String, Int> {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH)        // 0 = Jan
        val year = cal.get(java.util.Calendar.YEAR)
        val season = when (month) {
            in 2..4 -> "SPRING"
            in 5..7 -> "SUMMER"
            in 8..10 -> "FALL"
            else -> "WINTER"                                 // Dec, Jan, Feb
        }
        return season to (if (month == 11) year + 1 else year)
    }

    private fun MediaDto.toSummary() =
        AnimeSummary(id = id.toString(), title = displayTitle(), posterUrl = coverImage?.large, idMal = idMal)

    private fun MediaDto.displayTitle() =
        title?.english ?: title?.romaji ?: title?.native ?: "Untitled"

    private fun String.stripHtml() = replace(Regex("<[^>]*>"), "").trim()
}
