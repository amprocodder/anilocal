package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.HomeCatalog
import com.anilocal.app.domain.model.BrowseSort
import com.anilocal.app.domain.repo.CatalogRepository
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Named
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first decorator over [AniListCatalogRepository] — the piece that decouples browsing from
 * AniList being reachable. Every cached read is served instantly, including stale entries which refresh in
 * the background; cold reads are fetched and written through, so Home, Explore,
 * Details, and Search keep working offline once they've been seen. A true cold-cache failure
 * propagates so the screen can offer retry.
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
    @Named("appScope") private val appScope: CoroutineScope,
) : CatalogRepository {

    private val homeRequests = Mutex()
    private val refreshingHome = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Reads the existing installed app's row keys, so offline Home survives the batched query. */
    override suspend fun home(): HomeCatalog {
        val cached = readHomeRows()
        if (cached.all { it != null }) {
            if (cached.any { it!!.ageMs > TTL_ROWS }) refreshHomeInBackground()
            return cached.map { it!!.value }.toHome()
        }
        return homeRequests.withLock {
            // Another screen may have filled the missing shelves while we waited.
            val latest = readHomeRows()
            if (latest.all { it != null }) return@withLock latest.map { it!!.value }.toHome()
            val fresh = try {
                upstream.homeResult().let { result ->
                    result.catalog.rows().also { if (result.cacheable) writeHomeRows(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (latest.none { it != null }) throw failure
                List(HOME_KEYS.size) { emptyList<AnimeSummary>() }
            }
            fresh.mapIndexed { index, row -> row.ifEmpty { latest[index]?.value.orEmpty() } }.toHome()
        }
    }

    private suspend fun readHomeRows() = HOME_KEYS.map { key ->
        cache.getAged<List<AnimeSummary>>(key, SUMMARIES).also { currentCoroutineContext().ensureActive() }
    }

    private suspend fun writeHomeRows(rows: List<List<AnimeSummary>>) {
        rows.forEachIndexed { index, row ->
            currentCoroutineContext().ensureActive()
            // AniList partial/empty responses must never erase a useful shelf or pin a cold miss.
            if (row.isNotEmpty()) cache.put(HOME_KEYS[index], SUMMARIES, row)
        }
        // These queries have the same arguments as the corresponding Home shelves.
        // Warm Explore on the first visit without another AniList round trip.
        if (rows[0].isNotEmpty()) cache.put("popular:1", SUMMARIES, rows[0])
        if (rows[3].isNotEmpty()) cache.put("browse::POPULAR:1", SUMMARIES, rows[3])
    }

    private fun refreshHomeInBackground() {
        if (!refreshingHome.compareAndSet(false, true)) return
        appScope.launch {
            try {
                homeRequests.withLock {
                    val result = upstream.homeResult()
                    if (result.cacheable) writeHomeRows(result.catalog.rows())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Stale rows have already been returned; keep them during network outages.
            } finally {
                refreshingHome.set(false)
            }
        }
    }

    private fun HomeCatalog.rows() = listOf(trending, popularThisSeason, topAiring, allTimePopular, upcoming)
    private fun List<List<AnimeSummary>>.toHome() = HomeCatalog(this[0], this[1], this[2], this[3], this[4])

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
        val HOME_KEYS = listOf("home:trending:1", "home:season:1", "home:airing:1", "home:alltime:1", "home:upcoming:1")
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
