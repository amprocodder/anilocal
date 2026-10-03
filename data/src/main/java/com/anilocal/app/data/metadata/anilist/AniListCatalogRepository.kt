package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.data.cache.SuspendingLruCache
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.repo.CatalogRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListCatalogRepository internal constructor(
    private val api: AniListApi,
    nowMillis: () -> Long,
    dispatcher: CoroutineDispatcher,
) : CatalogRepository {
    @Inject constructor(api: AniListApi) : this(api, { System.nanoTime() / 1_000_000 }, Dispatchers.IO)

    private data class PageKey(val arguments: String, val page: Int, val genre: String? = null)
    private val pages = SuspendingLruCache<PageKey, List<AnimeSummary>>(100, PAGE_TTL_MS, nowMillis, dispatcher)
    private val details = SuspendingLruCache<String, AnimeDetail>(40, DETAIL_TTL_MS, nowMillis, dispatcher)
    private val malIds = SuspendingLruCache<Int, String?>(200, DETAIL_TTL_MS, nowMillis, dispatcher)
    private val homes = SuspendingLruCache<String, HomeCatalog>(2, PAGE_TTL_MS, nowMillis, dispatcher)
    private val jsonMedia = "application/json".toMediaType()
    private val requestJson = Moshi.Builder().build().adapter(Map::class.java)

    private fun body(query: String, variables: Map<String, Any?> = emptyMap()): okhttp3.RequestBody =
        requestJson.toJson(mapOf("query" to query, "variables" to variables)).toRequestBody(jsonMedia)

    override suspend fun home(): HomeCatalog {
        val seasonalArgs = seasonArguments()
        var complete = false
        return homes.getOrLoad(seasonalArgs, shouldCache = { complete }) {
            val query = """
                query {
                  trending: Page(page:1, perPage:30) { media($TRENDING, type:ANIME, isAdult:false) { $SUMMARY_FIELDS } }
                  popularThisSeason: Page(page:1, perPage:30) { media($seasonalArgs, type:ANIME, isAdult:false) { $SUMMARY_FIELDS } }
                  topAiring: Page(page:1, perPage:30) { media($AIRING, type:ANIME, isAdult:false) { $SUMMARY_FIELDS } }
                  allTimePopular: Page(page:1, perPage:30) { media($POPULAR, type:ANIME, isAdult:false) { $SUMMARY_FIELDS } }
                  upcoming: Page(page:1, perPage:30) { media($UPCOMING, type:ANIME, isAdult:false) { $SUMMARY_FIELDS } }
                }
            """.trimIndent()
            val response = api.query(body(query))
            val data = response.requireData()
            val shelves = listOf(data.trending, data.popularThisSeason, data.topAiring, data.allTimePopular, data.upcoming)
            check(shelves.any { it?.media != null }) {
                response.errors?.firstOrNull()?.message ?: "AniList returned no Home shelves"
            }
            val noErrors = response.errors.isNullOrEmpty()
            complete = noErrors && shelves.all { it?.media != null }
            val result = HomeCatalog(
                trending = data.trending.summaries(),
                popularThisSeason = data.popularThisSeason.summaries(),
                topAiring = data.topAiring.summaries(),
                allTimePopular = data.allTimePopular.summaries(),
                upcoming = data.upcoming.summaries(),
            )
            // The same pages serve Explore's defaults and individual shelf requests.
            // Nested GraphQL field errors can leave an apparently complete but damaged shelf.
            if (noErrors) {
                if (data.trending?.media != null) pages.put(PageKey(TRENDING, 1), result.trending)
                if (data.popularThisSeason?.media != null) pages.put(PageKey(seasonalArgs, 1), result.popularThisSeason)
                if (data.topAiring?.media != null) pages.put(PageKey(AIRING, 1), result.topAiring)
                if (data.allTimePopular?.media != null) pages.put(PageKey(POPULAR, 1), result.allTimePopular)
                if (data.upcoming?.media != null) pages.put(PageKey(UPCOMING, 1), result.upcoming)
            }
            result
        }
    }

    override suspend fun popular(page: Int) = trending(page)
    override suspend fun trending(page: Int) = mediaPage(TRENDING, page)
    override suspend fun allTimePopular(page: Int) = mediaPage(POPULAR, page)
    override suspend fun topAiring(page: Int) = mediaPage(AIRING, page)
    override suspend fun upcoming(page: Int) = mediaPage(UPCOMING, page)
    override suspend fun popularThisSeason(page: Int) = mediaPage(seasonArguments(), page)

    override suspend fun search(query: String): List<AnimeSummary> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return popular(1)
        return pages.getOrLoad(PageKey("search:$normalized", 1)) {
            val q = """
                query(${'$'}search:String) { Page(page:1, perPage:30) {
                  media(search:${'$'}search, type:ANIME, isAdult:false) { $SUMMARY_FIELDS }
                } }
            """.trimIndent()
            api.query(body(q, mapOf("search" to normalized))).requirePage().summaries()
        }
    }

    override suspend fun detail(animeId: String): AnimeDetail {
        val id = animeId.toInt().toString()
        return details.getOrLoad(id) {
            val q = """
                query(${'$'}id:Int) { Media(id:${'$'}id, type:ANIME) {
                  $SUMMARY_FIELDS bannerImage description(asHtml:false) genres episodes
                } }
            """.trimIndent()
            val response = api.query(body(q, mapOf("id" to id.toInt())))
            check(response.errors.isNullOrEmpty()) {
                response.errors?.firstOrNull()?.message ?: "AniList detail loading failed"
            }
            val m = response.requireData().media
                ?: error("AniList: media $id not found")
            AnimeDetail(
                id = m.id.toString(),
                title = m.displayTitle(),
                posterUrl = m.coverImage?.large,
                bannerUrl = m.bannerImage,
                synopsis = m.description?.replace(HTML_TAG, "")?.trim().orEmpty(),
                genres = m.genres.orEmpty(),
                idMal = m.idMal,
                episodes = (1..(m.episodes ?: 0)).map {
                    Episode(id = "${m.id}-$it", number = it, title = "Episode $it")
                },
            )
        }
    }

    override suspend fun browse(genre: String?, sort: BrowseSort, page: Int): List<AnimeSummary> =
        mediaPage("sort: ${sort.anilist}", page, genre?.trim()?.takeIf { it.isNotEmpty() })

    override suspend fun anilistIdForMal(malId: Int): String? = try {
        malIds.getOrLoad(malId) {
            val q = "query(${'$'}idMal:Int) { Media(idMal:${'$'}idMal, type:ANIME) { id } }"
            val response = api.query(body(q, mapOf("idMal" to malId)))
            val media = response.requireData().media
            check(media != null || response.errors.isNullOrEmpty()) {
                response.errors?.firstOrNull()?.message ?: "AniList mapping failed"
            }
            media?.id?.toString()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun mediaPage(arguments: String, page: Int, genre: String? = null): List<AnimeSummary> {
        require(page > 0)
        return pages.getOrLoad(PageKey(arguments, page, genre)) {
            val genreArg = if (genre == null) "" else "genre:${'$'}genre, "
            val genreVariable = if (genre == null) "" else ", ${'$'}genre:String"
            val q = """
                query(${'$'}page:Int$genreVariable) { Page(page:${'$'}page, perPage:30) {
                  media($genreArg$arguments, type:ANIME, isAdult:false) { $SUMMARY_FIELDS }
                } }
            """.trimIndent()
            val variables = if (genre == null) mapOf("page" to page) else mapOf("page" to page, "genre" to genre)
            api.query(body(q, variables)).requirePage().summaries()
        }
    }

    private fun seasonArguments(): String {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val season = when (month) {
            in 2..4 -> "SPRING"
            in 5..7 -> "SUMMER"
            in 8..10 -> "FALL"
            else -> "WINTER"
        }
        return "season: $season, seasonYear: ${if (month == 11) year + 1 else year}, sort: POPULARITY_DESC"
    }

    private fun GraphResponse.requireData(): AniListData = data
        ?: error(errors?.firstOrNull()?.message ?: "AniList returned no catalog data")

    private fun GraphResponse.requirePage(): PageDto = requireData().page?.takeIf { it.media != null }
        ?: error(errors?.firstOrNull()?.message ?: "AniList returned no catalog page")

    private fun PageDto?.summaries() = this?.media.orEmpty().map {
        AnimeSummary(it.id.toString(), it.displayTitle(), it.coverImage?.large, it.idMal)
    }

    private fun MediaDto.displayTitle() = title?.english ?: title?.romaji ?: title?.native ?: "Untitled"

    private companion object {
        const val PAGE_TTL_MS = 3 * 60 * 1000L
        const val DETAIL_TTL_MS = 30 * 60 * 1000L
        const val SUMMARY_FIELDS = "id idMal title{romaji english} coverImage{large}"
        const val TRENDING = "sort: TRENDING_DESC"
        const val POPULAR = "sort: POPULARITY_DESC"
        const val AIRING = "status: RELEASING, sort: POPULARITY_DESC"
        const val UPCOMING = "status: NOT_YET_RELEASED, sort: POPULARITY_DESC"
        val HTML_TAG = Regex("<[^>]*>")
    }
}
