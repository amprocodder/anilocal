package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.RelatedAnime
import com.anilocal.app.domain.repo.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AniListCatalogRepository(
    private val api: AniListApi,
) : CatalogRepository {

    override suspend fun home(): HomeCatalog = homeResult().catalog

    internal suspend fun homeResult(): AniListHomeResult = withContext(Dispatchers.IO) {
        val (season, year) = currentSeason()
        val q = """
            query(${'$'}season:MediaSeason, ${'$'}year:Int) {
              trending: Page(page:1, perPage:30) {
                media(sort:TRENDING_DESC, type:ANIME, isAdult:false) { ...Summary }
              }
              popularThisSeason: Page(page:1, perPage:30) {
                media(season:${'$'}season, seasonYear:${'$'}year, sort:POPULARITY_DESC, type:ANIME, isAdult:false) { ...Summary }
              }
              topAiring: Page(page:1, perPage:30) {
                media(status:RELEASING, sort:POPULARITY_DESC, type:ANIME, isAdult:false) { ...Summary }
              }
              allTimePopular: Page(page:1, perPage:30) {
                media(sort:POPULARITY_DESC, type:ANIME, isAdult:false) { ...Summary }
              }
              upcoming: Page(page:1, perPage:30) {
                media(status:NOT_YET_RELEASED, sort:POPULARITY_DESC, type:ANIME, isAdult:false) { ...Summary }
              }
            }
            fragment Summary on Media {
              id idMal title { romaji english } coverImage { large extraLarge } bannerImage
              format episodes averageScore status nextAiringEpisode { episode }
            }
        """.trimIndent()
        val response = api.query(q, buildJsonObject { put("season", season); put("year", year) })
        val data = response.data ?: error("AniList: home unavailable")
        fun PageDto?.summaries() = this?.media.orEmpty().map { it.toSummary() }
        AniListHomeResult(HomeCatalog(
            trending = data.trending.summaries(),
            popularThisSeason = data.popularThisSeason.summaries(),
            topAiring = data.topAiring.summaries(),
            allTimePopular = data.allTimePopular.summaries(),
            upcoming = data.upcoming.summaries(),
        ), cacheable = response.errors.isNullOrEmpty())
    }

    override suspend fun popular(page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int){ Page(page:${'$'}page, perPage:30){
              media(sort:TRENDING_DESC, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large} format episodes averageScore status nextAiringEpisode{episode}
              } } }
        """.trimIndent()
        api.query(q, buildJsonObject { put("page", page) })
            .requireData().page?.media?.map { it.toSummary() } ?: error("AniList: page unavailable")
    }

    override suspend fun search(query: String): List<AnimeSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext popular(1)
        val q = """
            query(${'$'}search:String){ Page(page:1, perPage:30){
              media(search:${'$'}search, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large} format episodes averageScore status nextAiringEpisode{episode}
              } } }
        """.trimIndent()
        api.query(q, buildJsonObject { put("search", query) })
            .requireData().page?.media?.map { it.toSummary() } ?: error("AniList: page unavailable")
    }

    override suspend fun detail(animeId: String): AnimeDetail = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}id:Int){ Media(id:${'$'}id, type:ANIME){
              id idMal title{romaji english native} synonyms coverImage{large extraLarge} bannerImage
              streamingEpisodes { title thumbnail }
              description(asHtml:false) genres episodes
              format averageScore status seasonYear duration
              studios(isMain: true) { nodes { name } } nextAiringEpisode { episode }
              relations { edges { relationType(version: 2) node {
                id idMal type format isAdult title{romaji english native} synonyms coverImage{large}
                episodes averageScore startDate{year month} status nextAiringEpisode{episode}
              } } }
            } }
        """.trimIndent()
        val m = api.query(q, buildJsonObject { put("id", animeId.toInt()) }).requireData().media
            ?: error("AniList: media $animeId not found")
        val count = m.releasedEpisodes() ?: 0
        AnimeDetail(
            id = m.id.toString(),
            title = m.displayTitle(),
            romajiTitle = m.title?.romaji?.trim()?.takeIf { it.isNotEmpty() && it != m.displayTitle() },
            posterUrl = m.coverImage?.large,
            posterHdUrl = m.coverImage?.extraLarge,
            bannerUrl = m.bannerImage,
            synopsis = m.description?.stripHtml().orEmpty(),
            genres = m.genres.orEmpty(),
            idMal = m.idMal,
            episodes = m.episodeArt(count).let { art ->
                (1..count).map {
                    val (name, thumb) = art[it] ?: (null to null)
                    Episode(id = "${m.id}-$it", number = it, title = name ?: "Episode $it", thumbnailUrl = thumb)
                }
            },
            format = m.format?.prettyFormat(),
            averageScore = m.averageScore,
            status = m.status?.prettyStatus(),
            seasonYear = m.seasonYear,
            duration = m.duration,
            studio = m.studios?.nodes?.firstOrNull()?.name,
            related = m.relations?.edges.orEmpty().toRelated(),
            altTitles = m.altTitles(),
        )
    }

    /**
     * Franchise neighbors in watch-order: prequels, then sequels, then surrounding content
     * (parent/side stories/movies…), each group sorted by release date. Manga/novel sources,
     * character-only links, and the OTHER grab-bag (music videos, commercials) are dropped.
     */
    private fun List<RelationEdgeDto>.toRelated(): List<RelatedAnime> = mapNotNull { edge ->
        val node = edge.node ?: return@mapNotNull null
        if (node.type != "ANIME" || node.isAdult == true) return@mapNotNull null
        val rank = RELATION_RANK[edge.relationType] ?: return@mapNotNull null
        Triple(rank, node, edge.relationType!!)
    }.sortedWith(
        compareBy(
            { it.first },
            { it.second.startDate?.year ?: Int.MAX_VALUE },
            { it.second.startDate?.month ?: Int.MAX_VALUE },
        )
    ).map { (_, node, type) -> RelatedAnime(relation = type.prettyRelation(), anime = node.toSummary()) }
        .distinctBy { it.anime.id }   // a node can appear under two edges; the UI keys rows by id

    override suspend fun trending(page: Int) = mediaPage("sort: TRENDING_DESC", page)
    override suspend fun allTimePopular(page: Int) = mediaPage("sort: POPULARITY_DESC", page)
    override suspend fun topAiring(page: Int) = mediaPage("status: RELEASING, sort: POPULARITY_DESC", page)
    override suspend fun upcoming(page: Int) = mediaPage("status: NOT_YET_RELEASED, sort: POPULARITY_DESC", page)

    override suspend fun popularThisSeason(page: Int): List<AnimeSummary> {
        val (season, year) = currentSeason()
        return mediaPage("season: $season, seasonYear: $year, sort: POPULARITY_DESC", page)
    }

    override suspend fun browse(genre: String?, sort: BrowseSort, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int, ${'$'}genre:String) { Page(page:${'$'}page, perPage:30) {
              media(genre:${'$'}genre, sort:${sort.anilist}, type:ANIME, isAdult:false) {
                id idMal title{romaji english} coverImage{large} format episodes averageScore status nextAiringEpisode{episode}
              }
            } }
        """.trimIndent()
        api.query(q, buildJsonObject { put("page", page); put("genre", genre) })   // null genre = all
            .requireData().page?.media?.map { it.toSummary() } ?: error("AniList: page unavailable")
    }

    override suspend fun anilistIdForMal(malId: Int): String? = withContext(Dispatchers.IO) {
        val q = "query(${'$'}idMal:Int){ Media(idMal:${'$'}idMal, type:ANIME){ id } }"
        try {
            api.query(q, buildJsonObject { put("idMal", malId) }).requireData().media?.id?.toString()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun mediaPage(mediaArgs: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val q = """
            query(${'$'}page:Int){ Page(page:${'$'}page, perPage:30){
              media($mediaArgs, type:ANIME, isAdult:false){
                id idMal title{romaji english} coverImage{large} format episodes averageScore status nextAiringEpisode{episode}
              } } }
        """.trimIndent()
        api.query(q, buildJsonObject { put("page", page) }).requireData().page?.media?.map { it.toSummary() }
            ?: error("AniList: page unavailable")
    }

    private fun GraphResponse.requireData(): AniListData {
        if (!errors.isNullOrEmpty()) error(errors.first().message ?: "AniList: query unavailable")
        return data ?: error("AniList: query unavailable")
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

    /**
     * Episodes that have actually been RELEASED — never AniList's planned total. Nothing for a title
     * that hasn't started; for an airing one, everything before the next scheduled episode (AniList
     * reports `episodes` as null or as the full planned count while airing). Null when unknown.
     */
    private fun MediaDto.releasedEpisodes(): Int? = when (status) {
        "NOT_YET_RELEASED" -> 0
        "RELEASING" -> nextAiringEpisode?.episode?.minus(1)?.coerceAtLeast(0)
            ?.let { aired -> minOf(episodes ?: Int.MAX_VALUE, aired) }
            ?: episodes
        else -> episodes
    }

    /**
     * Episode number → (name, thumbnail) from AniList's `streamingEpisodes` ("Episode 3 - Name").
     * Sequels sometimes number across the franchise ("Episode 17" for season 2's fifth): when no
     * parsed number fits 1..[count] but the list is a complete contiguous run of exactly [count],
     * it's shifted to start at 1; anything else that doesn't fit is dropped rather than mismatched.
     */
    private fun MediaDto.episodeArt(count: Int): Map<Int, Pair<String?, String?>> {
        if (count <= 0) return emptyMap()
        val parsed = streamingEpisodes.orEmpty().mapNotNull { e ->
            val match = EPISODE_TITLE.find(e.title.orEmpty()) ?: return@mapNotNull null
            val n = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            Triple(n, match.groupValues[2].trim().ifEmpty { null }, e.thumbnail?.ifBlank { null })
        }.distinctBy { it.first }
        if (parsed.isEmpty()) return emptyMap()
        val offset = if (parsed.none { it.first in 1..count }) {
            val nums = parsed.map { it.first }.sorted()
            if (nums.size == count && nums.last() - nums.first() == count - 1) nums.first() - 1 else return emptyMap()
        } else 0
        return parsed.mapNotNull { (n, name, thumb) ->
            (n - offset).takeIf { it in 1..count }?.let { it to (name to thumb) }
        }.toMap()
    }

    private fun MediaDto.toSummary() = AnimeSummary(
        id = id.toString(),
        title = displayTitle(),
        posterUrl = coverImage?.large,
        idMal = idMal,
        format = format?.prettyFormat(),
        // Cards show the released count; hide the badge entirely when nothing is out yet.
        episodes = releasedEpisodes()?.takeIf { it > 0 },
        averageScore = averageScore,
        altTitles = altTitles(),
        bannerUrl = bannerImage,
        posterHdUrl = coverImage?.extraLarge,
    )

    private fun MediaDto.displayTitle() =
        title?.english ?: title?.romaji ?: title?.native ?: "Untitled"

    /** Every name other than [displayTitle], romaji first (the likeliest other spelling a source
     *  uses) — the inputs stream-source matching scores candidates against. */
    private fun MediaDto.altTitles(): List<String> {
        val display = displayTitle()
        return (listOfNotNull(title?.romaji, title?.english, title?.native) + synonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != display }
            .distinct()
    }

    private fun String.stripHtml() = replace(HTML_TAG, "").trim()

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
        "RELEASING" -> "Airing"   // MAL's wording
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> this
    }

    private fun String.prettyRelation() = when (this) {
        "PREQUEL" -> "Prequel"
        "SEQUEL" -> "Sequel"
        "PARENT" -> "Parent story"
        "SIDE_STORY" -> "Side story"
        "SPIN_OFF" -> "Spin-off"
        "ALTERNATIVE" -> "Alternative"
        "SUMMARY" -> "Recap"
        "COMPILATION" -> "Compilation"
        else -> this
    }

    private companion object {
        val HTML_TAG = Regex("<[^>]*>")
        /** Watch-order rank per AniList relationType (v2); types not listed are dropped. */
        val RELATION_RANK = mapOf(
            "PREQUEL" to 0,
            "SEQUEL" to 1,
            "PARENT" to 2,
            "SIDE_STORY" to 3,
            "SPIN_OFF" to 4,
            "ALTERNATIVE" to 5,
            "SUMMARY" to 6,
            "COMPILATION" to 7,
        )
    }
}

/** Partial Home responses can be displayed, but must not poison the persisted shelves. */
internal data class AniListHomeResult(val catalog: HomeCatalog, val cacheable: Boolean)

/** "Episode 12 - The Name" (also "–"/":" separators); group 1 = number, group 2 = name. */
private val EPISODE_TITLE = Regex("""^\s*Episode\s+(\d+)\s*(?:[-–—:]\s*(.*))?$""", RegexOption.IGNORE_CASE)
