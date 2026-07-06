package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.repo.CatalogRepository
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first decorator over [AniListCatalogRepository] — the piece that decouples browsing from
 * AniList being reachable. Every read is served from the cache DB when fresh enough, fetched (and
 * written through) otherwise, and served STALE — any age — when the fetch fails, so Home, Explore,
 * Details, and Search keep working offline once they've been seen. ViewModels keep their existing
 * `runCatching { … } → blank` behavior for the true first-run-offline case.
 *
 * TTLs are per surface: fast-moving Home rows refresh every 45 min; browse/search/detail pages
 * refresh a few times a day (a detail's aired-episode count grows weekly at most); the MAL→AniList
 * id map never expires (ids are immutable) — that one also makes MAL-only Library rows openable
 * offline once resolved.
 */
@Singleton
class CachedCatalogRepository @Inject constructor(
    private val upstream: AniListCatalogRepository,
    private val cache: JsonCache,
) : CatalogRepository {

    override suspend fun trending(page: Int) = rows("home:trending:$page") { upstream.trending(page) }
    override suspend fun popularThisSeason(page: Int) = rows("home:season:$page") { upstream.popularThisSeason(page) }
    override suspend fun topAiring(page: Int) = rows("home:airing:$page") { upstream.topAiring(page) }
    override suspend fun allTimePopular(page: Int) = rows("home:alltime:$page") { upstream.allTimePopular(page) }
    override suspend fun upcoming(page: Int) = rows("home:upcoming:$page") { upstream.upcoming(page) }

    override suspend fun popular(page: Int): List<AnimeSummary> =
        cache.cached("popular:$page", SUMMARIES, TTL_BROWSE, preferStaleOverEmpty = true) { upstream.popular(page) }

    override suspend fun browse(genre: String?, sort: BrowseSort, page: Int): List<AnimeSummary> =
        cache.cached("browse:${genre.orEmpty()}:${sort.name}:$page", SUMMARIES, TTL_BROWSE, preferStaleOverEmpty = true) {
            upstream.browse(genre, sort, page)
        }

    override suspend fun search(query: String): List<AnimeSummary> {
        val q = query.trim()
        if (q.isBlank()) return popular(1)   // the upstream blank-query fallback, but cached
        return cache.cached("search:${q.lowercase()}", SUMMARIES, TTL_SEARCH, preferStaleOverEmpty = true) {
            upstream.search(q)
        }
    }

    override suspend fun detail(animeId: String): AnimeDetail =
        cache.cached("detail:$animeId", DETAIL, TTL_DETAIL) { upstream.detail(animeId) }

    override suspend fun anilistIdForMal(malId: Int): String? {
        val key = "malmap:$malId"
        cache.getAged<String>(key, STRING)?.let { return it.value }
        // Upstream already swallows failures to null; only a real id is worth persisting.
        return upstream.anilistIdForMal(malId)?.also { cache.put(key, STRING, it) }
    }

    private suspend fun rows(key: String, fetch: suspend () -> List<AnimeSummary>): List<AnimeSummary> =
        cache.cached(key, SUMMARIES, TTL_ROWS, preferStaleOverEmpty = true, fetch = fetch)

    private companion object {
        val SUMMARIES: java.lang.reflect.Type =
            Types.newParameterizedType(List::class.java, AnimeSummary::class.java)
        val DETAIL: java.lang.reflect.Type = AnimeDetail::class.java
        val STRING: java.lang.reflect.Type = String::class.java

        const val TTL_ROWS = 45L * 60 * 1000            // Home rows
        const val TTL_BROWSE = 3L * 60 * 60 * 1000      // Explore grid / popular
        const val TTL_SEARCH = 24L * 60 * 60 * 1000     // search results
        const val TTL_DETAIL = 4L * 60 * 60 * 1000      // detail + episode list (airing count grows weekly)
    }
}
