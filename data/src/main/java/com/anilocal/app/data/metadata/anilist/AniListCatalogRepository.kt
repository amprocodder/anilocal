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
                id idMal title{romaji english} coverImage{large} format episodes averageScore
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
                id idMal title{romaji english} coverImage{large} format episodes averageScore
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
              format averageScore status seasonYear duration
              studios(isMain: true) { nodes { name } } nextAiringEpisode { episode }
            } }
        """.trimIndent()
        val m = api.query(body(q, JSONObject().put("id", animeId.toInt()))).data?.media
            ?: error("AniList: media $animeId not found")
        // Airing shows report episodes as null OR as the full planned count; while releasing, cap
        // at what has actually aired so the grid can't offer episodes no source can resolve yet.
        val aired = m.nextAiringEpisode?.episode?.minus(1)?.coerceAtLeast(0)
        val count = if (m.status == "RELEASING" && aired != null) {
            minOf(m.episodes ?: Int.MAX_VALUE, aired)
        } else {
            m.episodes ?: 0
        }
        AnimeDetail(
            id = m.id.toString(),
            title = m.displayTitle(),
            posterUrl = m.coverImage?.large,
            bannerUrl = m.bannerImage,
            synopsis = m.description?.stripHtml().orEmpty(),
            genres = m.genres.orEmpty(),
            idMal = m.idMal,
            episodes = (1..count).map { Episode(id = "${m.id}-$it", number = it, title = "Episode $it") },
            format = m.format?.prettyFormat(),
            averageScore = m.averageScore,
            status = m.status?.prettyStatus(),
            seasonYear = m.seasonYear,
            duration = m.duration,
            studio = m.studios?.nodes?.firstOrNull()?.name,
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

    override suspend fun anilistIdForMal(malId: Int): String? = withContext(Dispatchers.IO) {
        val q = "query(${'$'}idMal:Int){ Media(idMal:${'$'}idMal, type:ANIME){ id } }"
        runCatching { api.query(body(q, JSONObject().put("idMal", malId))).data?.media?.id?.toString() }.getOrNull()
    }

    private suspend fun mediaPage(mediaArgs: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int){ Page(page:${'$'}page, perPage:30){
              media($mediaArgs, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large} format episodes averageScore
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

    private fun MediaDto.toSummary() = AnimeSummary(
        id = id.toString(),
        title = displayTitle(),
        posterUrl = coverImage?.large,
        idMal = idMal,
        format = format?.prettyFormat(),
        episodes = episodes,
        averageScore = averageScore,
    )

    private fun MediaDto.displayTitle() =
        title?.english ?: title?.romaji ?: title?.native ?: "Untitled"

    private fun String.stripHtml() = replace(Regex("<[^>]*>"), "").trim()

    private fun String.prettyFormat() = when (this) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> this
    }

    private fun String.prettyStatus() = when (this) {
        "RELEASING" -> "Releasing"
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> this
    }
}
